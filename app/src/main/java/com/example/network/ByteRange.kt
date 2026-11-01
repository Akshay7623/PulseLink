package com.example.network

/**
 * The part of a file a request asked for. This is what lets a player skip around a long video
 * without downloading everything before that point.
 *
 * @param isPartial false when the whole file is being sent because no usable range was asked for
 */
data class ByteRange(val start: Long, val end: Long, val isPartial: Boolean) {
    val length: Long get() = end - start + 1

    companion object {
        /**
         * @param header the request's Range header in lower case, null when it had none
         * @param size the file's size in bytes
         * @return what to send, or null when the file has nothing at the requested position.
         * A range this server does not understand gets the whole file, as HTTP allows.
         */
        fun forRequest(header: String?, size: Long): ByteRange? {
            val whole = ByteRange(0, size - 1, isPartial = false)
            if (header == null || !header.startsWith("bytes=") || ',' in header) return whole

            val spec = header.removePrefix("bytes=").trim()
            val first = spec.substringBefore('-', "")
            val last = spec.substringAfter('-', "")

            if (first.isEmpty()) {
                // "-500" means the last 500 bytes
                val count = last.toLongOrNull() ?: return whole
                if (count <= 0 || size == 0L) return null
                return ByteRange((size - count).coerceAtLeast(0), size - 1, isPartial = true)
            }

            val start = first.toLongOrNull() ?: return whole
            if (start >= size) return null
            val end = if (last.isEmpty()) size - 1 else last.toLongOrNull()?.coerceAtMost(size - 1) ?: return whole
            if (end < start) return whole
            return ByteRange(start, end, isPartial = true)
        }
    }
}
