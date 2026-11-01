package com.example

import com.example.model.SharedVideo
import com.example.model.VideoSort
import com.example.model.VideoSortField
import com.example.model.compareNaturally
import com.example.model.toFolders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBrowsingTest {

  private fun video(id: Long, name: String, date: Long = 0, folderId: Long = 1, folder: String = "Movies", size: Long = 0) =
    SharedVideo(id = id, name = name, dateAddedSeconds = date, folderId = folderId, folder = folder, sizeBytes = size)

  private val byName = VideoSort(VideoSortField.NAME, ascending = true)
  private val newestFirst = VideoSort(VideoSortField.DATE, ascending = false)

  @Test
  fun numbersInNamesSortAsNumbers() {
    val names = listOf("Episode 10.mp4", "Episode 2.mp4", "Episode 1.mp4")
    assertEquals(
      listOf("Episode 1.mp4", "Episode 2.mp4", "Episode 10.mp4"),
      names.sortedWith { a, b -> compareNaturally(a, b) }
    )
  }

  @Test
  fun capitalsDoNotChangeTheOrder() {
    val names = listOf("banana", "Apple", "cherry")
    assertEquals(listOf("Apple", "banana", "cherry"), names.sortedWith { a, b -> compareNaturally(a, b) })
    assertEquals(0, compareNaturally("MOVIE", "movie"))
  }

  @Test
  fun naturalOrderHandlesOddNames() {
    assertTrue(compareNaturally("a", "ab") < 0)
    assertTrue(compareNaturally("", "a") < 0)
    assertEquals(0, compareNaturally("", ""))
    assertEquals(0, compareNaturally("Part 007", "Part 7"))
    assertTrue(compareNaturally("Part 7", "Part 0012") < 0)
    // Far too long for any number type
    assertTrue(compareNaturally("99999999999999999999999998", "99999999999999999999999999") < 0)
  }

  @Test
  fun videosSortByNameBothWays() {
    val videos = listOf(video(1, "Beta"), video(2, "alpha"), video(3, "Gamma"))
    assertEquals(listOf(2L, 1L, 3L), videos.sortedWith(byName.videoOrder).map { it.id })
    assertEquals(listOf(3L, 1L, 2L), videos.sortedWith(byName.flipped().videoOrder).map { it.id })
  }

  @Test
  fun videosSortByDateBothWays() {
    val videos = listOf(video(1, "a", date = 200), video(2, "b", date = 100), video(3, "c", date = 300))
    assertEquals(listOf(3L, 1L, 2L), videos.sortedWith(newestFirst.videoOrder).map { it.id })
    assertEquals(listOf(2L, 1L, 3L), videos.sortedWith(newestFirst.flipped().videoOrder).map { it.id })
  }

  @Test
  fun videosFromTheSameMomentStillHaveAFixedOrder() {
    val videos = listOf(video(2, "b", date = 100), video(1, "a", date = 100), video(3, "a", date = 100))
    assertEquals(listOf(1L, 3L, 2L), videos.sortedWith(newestFirst.flipped().videoOrder).map { it.id })
  }

  @Test
  fun choosingAFieldStartsFromItsUsualDirection() {
    assertEquals(VideoSort(VideoSortField.NAME, ascending = true), newestFirst.withField(VideoSortField.NAME))
    assertEquals(VideoSort(VideoSortField.DATE, ascending = false), byName.withField(VideoSortField.DATE))
    // Tapping the field already chosen keeps the direction the user picked
    val zToA = byName.flipped()
    assertEquals(zToA, zToA.withField(VideoSortField.NAME))
  }

  @Test
  fun sortDirectionIsWrittenForPeople() {
    assertEquals("A to Z", byName.directionText)
    assertEquals("Z to A", byName.flipped().directionText)
    assertEquals("Newest first", newestFirst.directionText)
    assertEquals("Oldest first", newestFirst.flipped().directionText)
  }

  @Test
  fun videosGoIntoTheirFolders() {
    val folders = listOf(
      video(1, "a", folderId = 10, folder = "Movies", size = 1_000_000_000),
      video(2, "b", folderId = 20, folder = "Downloads", size = 5_000_000),
      video(3, "c", folderId = 10, folder = "Movies", size = 500_000_000)
    ).toFolders().associateBy { it.id }

    assertEquals(2, folders.size)
    assertEquals(listOf(1L, 3L), folders.getValue(10).videos.map { it.id })
    assertEquals("2 videos · 1.5 GB", folders.getValue(10).summaryText)
    assertEquals("1 video · 5 MB", folders.getValue(20).summaryText)
  }

  @Test
  fun foldersWithTheSameNameStaySeparate() {
    val folders = listOf(
      video(1, "a", folderId = 10, folder = "Camera"),
      video(2, "b", folderId = 20, folder = "Camera")
    ).toFolders()

    assertEquals(2, folders.size)
  }

  @Test
  fun aFolderWithNoNameStillHasOneToShow() {
    assertEquals("Other videos", listOf(video(1, "a", folderId = 0, folder = "")).toFolders().single().name)
  }

  @Test
  fun foldersSortByNameBothWays() {
    val folders = listOf(
      video(1, "a", folderId = 1, folder = "Movies"),
      video(2, "b", folderId = 2, folder = "camera"),
      video(3, "c", folderId = 3, folder = "Downloads")
    ).toFolders()

    assertEquals(listOf("camera", "Downloads", "Movies"), folders.sortedWith(byName.folderOrder).map { it.name })
    assertEquals(listOf("Movies", "Downloads", "camera"), folders.sortedWith(byName.flipped().folderOrder).map { it.name })
  }

  @Test
  fun foldersSortByTheirNewestVideo() {
    val folders = listOf(
      video(1, "a", date = 100, folderId = 1, folder = "Old"),
      video(2, "b", date = 900, folderId = 1, folder = "Old"),
      video(3, "c", date = 500, folderId = 2, folder = "Middle")
    ).toFolders()

    assertEquals(listOf("Old", "Middle"), folders.sortedWith(newestFirst.folderOrder).map { it.name })
    assertEquals(listOf("Middle", "Old"), folders.sortedWith(newestFirst.flipped().folderOrder).map { it.name })
  }
}
