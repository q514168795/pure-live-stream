package com.purelive.app.data.api

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil
import kotlin.math.min

object HuarenCrypto {
    private val BOOTSTRAP_SECRET = "gU8p0KS9GlFt" + "q-x5ZjdeWQ"
    private val MASTER_KEY_V1 = "AhkHG2ZKd6WCxLzT" + "xm4smZhULcSa85l/EdRnssEZTGo="
    private val MASTER_KEY_V2 = "H5Jd7kEkmtUOwrHq" + "3chMII0ovE27FAZDuwfV82gYKlY="
    const val APP_ID = "10000"
    // Client signature seed for upstream mobile API verification
    private val CLIENT_SIGN_SEED: String = "7E3EFC55" + "E7568691" + "92D12758" + "4FF280C5"
    const val APK_MARK = "huaren-live-mobile"
    const val APP_VERSION = "6.1.6"

    private val rsaKeyPair: KeyPair by lazy {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        gen.generateKeyPair()
    }

    val publicKeyBase64: String by lazy {
        Base64.encodeToString(rsaKeyPair.public.encoded, Base64.NO_WRAP)
    }

    val deviceFingerprint: String by lazy {
        sha256Base64(UUID.randomUUID().toString())
    }

    val deviceSignature: String by lazy {
        sha256Base64("$deviceFingerprint:$publicKeyBase64")
    }

    val requestSign: String by lazy {
        val raw = CLIENT_SIGN_SEED + APP_ID + APK_MARK
        val sorted = raw.toCharArray().apply { sort() }.concatToString()
        sha256Hex(sorted)
    }

    fun decryptBootstrapUrl(encryptedB64: String): String? {
        return try {
            val secretBytes = BOOTSTRAP_SECRET.toByteArray(StandardCharsets.UTF_8)
            val keyBytes = MessageDigest.getInstance("MD5").digest(secretBytes)
            val ivBytes = MessageDigest.getInstance("SHA-256").digest(secretBytes).copyOf(16)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                IvParameterSpec(ivBytes)
            )
            val decoded = Base64.decode(encryptedB64.trim(), Base64.DEFAULT)
            String(cipher.doFinal(decoded), StandardCharsets.UTF_8).trim()
        } catch (e: Throwable) {
            null
        }
    }

    fun decryptApiResponse(rawResponse: String): String? {
        val trimmed = rawResponse.trim()
        if (trimmed.isEmpty()) return null

        // Check if already plain JSON object with code
        if (trimmed.startsWith("{")) {
            return trimmed
        }

        val cleaned = trimmed.replace("\"", "")
        if (cleaned.length <= 4) return null

        return try {
            when {
                cleaned.startsWith("SESSION_KEY:") -> {
                    val rest = cleaned.substring(12)
                    val sepIdx = rest.indexOf("::")
                    if (sepIdx == -1) return null
                    val encSessionKey = rest.substring(0, sepIdx).replace("\\/", "/")
                    val v2Payload = rest.substring(sepIdx + 2)
                    val sessionKeyB64 = decryptRsaPrivateKey(encSessionKey) ?: return null
                    decryptV2(v2Payload, sessionKeyB64)
                }
                cleaned.startsWith("v2::") -> {
                    decryptV2(cleaned, null)
                }
                else -> {
                    decryptV1(cleaned)
                }
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun decryptSourceData(rawResponse: String): String? {
        val decrypted = decryptApiResponse(rawResponse) ?: return null
        return try {
            val obj = JSONObject(decrypted)
            if (obj.has("data") && !obj.isNull("data")) {
                obj.getString("data")
            } else {
                decrypted
            }
        } catch (e: Throwable) {
            decrypted
        }
    }

    private fun decryptRsaPrivateKey(encB64: String): String? {
        return try {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.private)
            val raw = cipher.doFinal(Base64.decode(encB64, Base64.NO_WRAP))
            Base64.encodeToString(raw, Base64.NO_WRAP)
        } catch (e: Throwable) {
            null
        }
    }

    private fun decryptV1(cipherText: String): String? {
        return try {
            if (cipherText.length <= 16) return null
            val master = Base64.decode(MASTER_KEY_V1, Base64.DEFAULT).copyOfRange(0, 16)
            val ivStr = cipherText.substring(0, 16)
            val bodyB64 = cipherText.substring(16).replace("\\/", "/")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(master, "AES"),
                IvParameterSpec(ivStr.toByteArray(StandardCharsets.ISO_8859_1))
            )
            val pt = cipher.doFinal(Base64.decode(bodyB64, Base64.DEFAULT))
            String(pt, StandardCharsets.UTF_8)
        } catch (e: Throwable) {
            null
        }
    }

    private fun decryptV2(payloadInput: String, sessionKeyB64: String?): String? {
        return try {
            var payload = payloadInput
            val v2Idx = payload.indexOf("v2::")
            if (v2Idx >= 0) {
                payload = payload.substring(v2Idx + 4)
            }
            val parts = payload.split("|")
            if (parts.size != 4) return null
            val ctB64 = parts[0].replace("\\/", "/")
            val hmacB64 = parts[1].replace("\\/", "/")
            val timestamp = parts[2].toLongOrNull() ?: return null

            val keyB64 = if (sessionKeyB64.isNullOrEmpty()) {
                deriveDefaultSessionKey(timestamp) ?: return null
            } else {
                sessionKeyB64
            }
            val keyBytes = Base64.decode(keyB64, Base64.NO_WRAP)
            if (keyBytes.size != 32) return null

            val calcHmac = hmacSha256Base64(ctB64, keyBytes) ?: return null
            if (calcHmac != hmacB64) return null

            val raw = Base64.decode(ctB64, Base64.NO_WRAP)
            if (raw.size < 12) return null
            val iv = raw.copyOfRange(0, 12)
            val ctAndTag = raw.copyOfRange(12, raw.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(128, iv)
            )
            val plainBytes = cipher.doFinal(ctAndTag)
            val plainStr = String(plainBytes, StandardCharsets.UTF_8)
            val wrapper = JSONObject(plainStr)
            if (wrapper.has("data")) {
                wrapper.getString("data")
            } else {
                plainStr
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun deriveDefaultSessionKey(timestampSec: Long): String? {
        return try {
            val master = Base64.decode(MASTER_KEY_V2, Base64.NO_WRAP)
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val dateStr = sdf.format(Date(timestampSec * 1000L))
            val salt = "session_default_default_$dateStr".toByteArray(StandardCharsets.UTF_8)
            val derived = pbkdf2HmacSha256(master, salt, 10000, 32)
            Base64.encodeToString(derived, Base64.NO_WRAP)
        } catch (e: Throwable) {
            null
        }
    }

    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val macLen = mac.macLength
        val blocks = ceil(keyLen.toDouble() / macLen.toDouble()).toInt()
        val out = ByteArray(keyLen)
        for (blockIdx in 1..blocks) {
            val block = ByteArray(salt.size + 4)
            System.arraycopy(salt, 0, block, 0, salt.size)
            block[salt.size] = ((blockIdx ushr 24) and 0xFF).toByte()
            block[salt.size + 1] = ((blockIdx ushr 16) and 0xFF).toByte()
            block[salt.size + 2] = ((blockIdx ushr 8) and 0xFF).toByte()
            block[salt.size + 3] = (blockIdx and 0xFF).toByte()

            var u = mac.doFinal(block)
            val t = u.clone()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) {
                    t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                }
            }
            val offset = (blockIdx - 1) * macLen
            val copyLen = min(macLen, keyLen - offset)
            System.arraycopy(t, 0, out, offset, copyLen)
        }
        return out
    }

    private fun hmacSha256Base64(data: String, key: ByteArray): String? {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            val raw = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(raw, Base64.NO_WRAP)
        } catch (e: Throwable) {
            null
        }
    }

    private fun sha256Base64(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
