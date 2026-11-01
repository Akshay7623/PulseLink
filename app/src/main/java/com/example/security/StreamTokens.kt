package com.example.security

import java.security.SecureRandom

/**
 * Passes for watching this device's videos. A pairing challenge works for one request only, but a
 * player makes a new request every time the user skips, so a device that proved it has the pairing
 * code gets one of these to send with all of them. They are kept in memory only.
 */
class StreamTokens(private val now: () -> Long = System::currentTimeMillis) {

    private val random = SecureRandom()

    // Passes handed out, oldest first, with the time each one stops being accepted
    private val tokens = LinkedHashMap<String, Long>()

    @Synchronized
    fun issue(): String {
        val time = now()
        tokens.entries.removeAll { it.value < time }
        while (tokens.size >= MAX_TOKENS) tokens.remove(tokens.keys.first())

        val token = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        tokens[token] = time + LIFETIME_MS
        return token
    }

    /**
     * @return true for a pass handed out here that has not run out. Using one gives it its full
     * lifetime again, so a long film paused for a while keeps working.
     */
    @Synchronized
    fun isValid(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        val time = now()
        val expiresAt = tokens[token] ?: return false
        if (expiresAt < time) {
            tokens.remove(token)
            return false
        }
        tokens[token] = time + LIFETIME_MS
        return true
    }

    @Synchronized
    fun clear() = tokens.clear()

    companion object {
        const val HEADER = "X-Pulse-Stream"
        const val LIFETIME_MS = 12 * 60 * 60 * 1000L
        private const val MAX_TOKENS = 8
    }
}
