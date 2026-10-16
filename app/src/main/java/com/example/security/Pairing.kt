package com.example.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The pairing code that guards what costs money or opens a microphone: phone calls and the call
 * sound relay. Each device has its own code, shown in its settings. Another device has to be given
 * that code once, and from then on proves it knows the code with every request, without ever
 * sending the code itself over the network.
 */
class Pairing(context: Context) {

    private val prefs = context.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    // Challenges handed out and not used yet, with the time each one stops being accepted
    private val challenges = LinkedHashMap<String, Long>()
    private val cachedKeys = HashMap<String, ByteArray>()

    /** This device's own code, made up the first time it is needed. */
    val myCode: String
        get() = prefs.getString(KEY_MY_CODE, null) ?: newCode().also {
            prefs.edit().putString(KEY_MY_CODE, it).apply()
        }

    /** The code the user typed in for the device they control from here, empty when none yet. */
    var partnerCode: String
        get() = prefs.getString(KEY_PARTNER_CODE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PARTNER_CODE, normalize(value)).apply()

    /** @return null until the user has entered the other device's code */
    fun partnerKey(): ByteArray? = partnerCode.takeIf { it.length == CODE_LENGTH }?.let { keyFor(it) }

    private fun newCode(): String = (1..CODE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    // Deriving a key is slow on purpose, so each code is worked out once
    @Synchronized
    private fun keyFor(code: String): ByteArray {
        if (cachedKeys.size > MAX_CACHED_KEYS) cachedKeys.clear()
        return cachedKeys.getOrPut(code) { deriveKey(code) }
    }

    /**
     * @return a one-time value a peer has to sign its next request with
     */
    @Synchronized
    fun newChallenge(): String {
        val now = System.currentTimeMillis()
        challenges.entries.removeAll { it.value < now }
        while (challenges.size >= MAX_CHALLENGES) challenges.remove(challenges.keys.first())

        val nonce = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        challenges[nonce] = now + CHALLENGE_LIFETIME_MS
        return nonce
    }

    /**
     * @return true when the request was signed with this device's code, using a challenge that was
     * handed out here and has not been used before
     */
    fun verify(nonce: String?, signature: String?, method: String, path: String, body: String): Boolean {
        if (nonce.isNullOrEmpty() || signature.isNullOrEmpty()) return false
        val expiresAt = synchronized(this) { challenges.remove(nonce) } ?: return false
        if (expiresAt < System.currentTimeMillis()) return false

        val expected = sign(keyFor(myCode), nonce, method, path, body)
        return MessageDigest.isEqual(expected.toByteArray(), signature.lowercase().toByteArray())
    }

    companion object {
        const val HEADER_NONCE = "X-Pulse-Nonce"
        const val HEADER_SIGNATURE = "X-Pulse-Sign"
        const val CODE_LENGTH = 8

        private const val KEY_MY_CODE = "pairing_code"
        private const val KEY_PARTNER_CODE = "partner_pairing_code"
        private const val CHALLENGE_LIFETIME_MS = 30_000L
        private const val MAX_CHALLENGES = 64
        private const val MAX_CACHED_KEYS = 4

        // No 0/O or 1/I, which look alike when the code is read off another screen
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val KEY_SALT = "PulseLink-pairing-v1"
        private const val KEY_ITERATIONS = 20_000

        /** Upper case without the dash or spaces the code is shown or typed with. */
        fun normalize(code: String): String = code.uppercase().filter { it in ALPHABET }.take(CODE_LENGTH)

        /** The code the way it is shown to the user: ABCD-EFGH. */
        fun format(code: String): String = code.chunked(4).joinToString("-")

        fun deriveKey(code: String): ByteArray {
            val spec = PBEKeySpec(normalize(code).toCharArray(), KEY_SALT.toByteArray(), KEY_ITERATIONS, 256)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        }

        /**
         * @return the proof that goes into HEADER_SIGNATURE: it ties the code to this one request,
         * so a copy of it is useless for any other request
         */
        fun sign(key: ByteArray, nonce: String, method: String, path: String, body: String): String {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
            return mac.doFinal("$nonce\n$method\n$path\n$body".toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
