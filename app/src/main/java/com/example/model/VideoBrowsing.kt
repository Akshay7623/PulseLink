package com.example.model

enum class VideoSortField { NAME, DATE }

/**
 * The order videos and folders are listed in. A date means when the video was added to the phone.
 */
data class VideoSort(
    val sortBy: VideoSortField = VideoSortField.DATE,
    val ascending: Boolean = false
) {
    val directionText: String
        get() = when (sortBy) {
            VideoSortField.NAME -> if (ascending) "A to Z" else "Z to A"
            VideoSortField.DATE -> if (ascending) "Oldest first" else "Newest first"
        }

    val videoOrder: Comparator<SharedVideo>
        get() {
            val byName = Comparator<SharedVideo> { a, b -> compareNaturally(a.name, b.name) }
            val chosen = when (sortBy) {
                VideoSortField.NAME -> byName
                VideoSortField.DATE -> compareBy<SharedVideo> { it.dateAddedSeconds }.then(byName)
            }
            return (if (ascending) chosen else chosen.reversed()).thenBy { it.id }
        }

    val folderOrder: Comparator<VideoFolder>
        get() {
            val byName = Comparator<VideoFolder> { a, b -> compareNaturally(a.name, b.name) }
            val chosen = when (sortBy) {
                VideoSortField.NAME -> byName
                VideoSortField.DATE -> compareBy<VideoFolder> { it.latestDateSeconds }.then(byName)
            }
            return (if (ascending) chosen else chosen.reversed()).thenBy { it.id }
        }

    /**
     * Choosing another field starts from the order people usually want from it: A to Z, or newest first.
     */
    fun withField(newField: VideoSortField): VideoSort =
        if (newField == sortBy) this else VideoSort(newField, ascending = newField == VideoSortField.NAME)

    fun flipped(): VideoSort = copy(ascending = !ascending)
}

/**
 * The videos of one folder on a device
 */
data class VideoFolder(
    val id: Long,
    val name: String,
    val videos: List<SharedVideo>
) {
    val latestDateSeconds: Long = videos.maxOfOrNull { it.dateAddedSeconds } ?: 0

    val summaryText: String
        get() = "${videoCountText(videos.size)} · ${formatVideoSize(videos.sumOf { it.sizeBytes })}"
}

/**
 * Sorts videos into their folders. Folders are told apart by their id, because two different
 * folders can have the same name, such as "Camera" on the phone and on a memory card.
 */
fun List<SharedVideo>.toFolders(): List<VideoFolder> =
    groupBy { it.folderId }.map { (id, videos) ->
        VideoFolder(id, videos.first().folder.ifEmpty { "Other videos" }, videos)
    }

fun videoCountText(count: Int): String = if (count == 1) "1 video" else "$count videos"

/**
 * Compares names the way people read them: capitals do not matter and "Episode 2" comes before
 * "Episode 10".
 */
fun compareNaturally(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        if (a[i].isDigit() && b[j].isDigit()) {
            val startA = i
            val startB = j
            while (i < a.length && a[i].isDigit()) i++
            while (j < b.length && b[j].isDigit()) j++
            // Compared as text so a very long number cannot overflow: without leading zeros, the
            // longer number is the larger one
            val numberA = a.substring(startA, i).trimStart('0')
            val numberB = b.substring(startB, j).trimStart('0')
            if (numberA.length != numberB.length) return numberA.length.compareTo(numberB.length)
            val order = numberA.compareTo(numberB)
            if (order != 0) return order
        } else {
            val order = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
            if (order != 0) return order
            i++
            j++
        }
    }
    return (a.length - i).compareTo(b.length - j)
}
