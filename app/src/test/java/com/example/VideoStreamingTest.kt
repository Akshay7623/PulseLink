package com.example

import com.example.model.SharedVideo
import com.example.network.ByteRange
import com.example.security.StreamTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoStreamingTest {

  @Test
  fun noRangeSendsTheWholeFile() {
    assertEquals(ByteRange(0, 999, isPartial = false), ByteRange.forRequest(null, 1000))
    assertEquals(1000L, ByteRange.forRequest(null, 1000)!!.length)
  }

  @Test
  fun rangeFromAPositionRunsToTheEnd() {
    assertEquals(ByteRange(0, 999, isPartial = true), ByteRange.forRequest("bytes=0-", 1000))
    assertEquals(ByteRange(400, 999, isPartial = true), ByteRange.forRequest("bytes=400-", 1000))
  }

  @Test
  fun closedRangeSendsExactlyThatPart() {
    val range = ByteRange.forRequest("bytes=100-199", 1000)!!
    assertEquals(ByteRange(100, 199, isPartial = true), range)
    assertEquals(100L, range.length)
    // The last byte of the file can be asked for by itself
    assertEquals(ByteRange(999, 999, isPartial = true), ByteRange.forRequest("bytes=999-999", 1000))
  }

  @Test
  fun rangePastTheEndIsCutToTheFile() {
    assertEquals(ByteRange(900, 999, isPartial = true), ByteRange.forRequest("bytes=900-5000", 1000))
  }

  @Test
  fun rangeCountedFromTheEnd() {
    assertEquals(ByteRange(800, 999, isPartial = true), ByteRange.forRequest("bytes=-200", 1000))
    assertEquals(ByteRange(0, 999, isPartial = true), ByteRange.forRequest("bytes=-5000", 1000))
  }

  @Test
  fun positionsOutsideTheFileHaveNothingToSend() {
    assertNull(ByteRange.forRequest("bytes=1000-", 1000))
    assertNull(ByteRange.forRequest("bytes=0-", 0))
    assertNull(ByteRange.forRequest("bytes=-0", 1000))
  }

  @Test
  fun rangesThatMakeNoSenseGetTheWholeFile() {
    val whole = ByteRange(0, 999, isPartial = false)
    assertEquals(whole, ByteRange.forRequest("bytes=abc-", 1000))
    assertEquals(whole, ByteRange.forRequest("bytes=5-2", 1000))
    assertEquals(whole, ByteRange.forRequest("items=0-5", 1000))
    assertEquals(whole, ByteRange.forRequest("bytes=0-5,10-20", 1000))
  }

  @Test
  fun filmsLargerThanFourGigabytesWork() {
    val size = 8_000_000_000L
    assertEquals(
      ByteRange(5_000_000_000L, size - 1, isPartial = true),
      ByteRange.forRequest("bytes=5000000000-", size)
    )
  }

  @Test
  fun passWorksOnlyWhileItLasts() {
    var time = 1_000L
    val tokens = StreamTokens { time }
    val token = tokens.issue()

    assertTrue(tokens.isValid(token))
    assertFalse(tokens.isValid(null))
    assertFalse(tokens.isValid(""))
    assertFalse(tokens.isValid("0123456789abcdef0123456789abcdef"))

    time += StreamTokens.LIFETIME_MS + 1
    assertFalse(tokens.isValid(token))
    // Once run out it stays out
    time = 1_000L
    assertFalse(tokens.isValid(token))
  }

  @Test
  fun usingAPassKeepsItGoing() {
    var time = 1_000L
    val tokens = StreamTokens { time }
    val token = tokens.issue()

    time += StreamTokens.LIFETIME_MS - 1
    assertTrue(tokens.isValid(token))
    time += StreamTokens.LIFETIME_MS - 1
    assertTrue(tokens.isValid(token))
  }

  @Test
  fun passesAreUniqueAndCanBeWithdrawn() {
    val tokens = StreamTokens()
    val first = tokens.issue()
    val second = tokens.issue()
    assertNotEquals(first, second)

    tokens.clear()
    assertFalse(tokens.isValid(first))
    assertFalse(tokens.isValid(second))
  }

  @Test
  fun oldestPassGivesWayWhenTooManyAreOut() {
    val tokens = StreamTokens()
    val first = tokens.issue()
    val latest = (1..8).map { tokens.issue() }

    assertFalse(tokens.isValid(first))
    assertTrue(latest.all { tokens.isValid(it) })
  }

  @Test
  fun videoFactsAreWrittenForPeople() {
    assertEquals("0:09", SharedVideo(durationMs = 9_000).durationText)
    assertEquals("12:05", SharedVideo(durationMs = 725_000).durationText)
    assertEquals("2:03:04", SharedVideo(durationMs = 7_384_000).durationText)

    assertEquals("900 KB", SharedVideo(sizeBytes = 900_000).sizeText)
    assertEquals("350 MB", SharedVideo(sizeBytes = 350_000_000).sizeText)
    assertEquals("1.4 GB", SharedVideo(sizeBytes = 1_400_000_000).sizeText)

    assertEquals("1080p", SharedVideo(width = 1920, height = 1080).qualityText)
    assertEquals("1080p", SharedVideo(width = 1080, height = 1920).qualityText)
    assertEquals("4K", SharedVideo(width = 3840, height = 2160).qualityText)
    assertEquals("", SharedVideo().qualityText)
  }
}
