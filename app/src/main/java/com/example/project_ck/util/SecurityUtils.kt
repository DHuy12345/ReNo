package com.example.project_ck.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityUtils {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100000
    private const val KEY_LENGTH = 256
    private const val SALT_SIZE = 16

    fun hashPassword(password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE)
        random.nextBytes(salt)

        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded

        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        return "pbkdf2$$ITERATIONS$$saltBase64$$hashBase64"
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        return try {
            val parts = storedHash.split("$")
            if (parts.size != 4 || parts[0] != "pbkdf2") return false

            val iterations = parts[1].toInt()
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expectedHash = Base64.decode(parts[3], Base64.NO_WRAP)

            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, expectedHash.size * 8)
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val actualHash = factory.generateSecret(spec).encoded

            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (e: Exception) {
            false
        }
    }
}