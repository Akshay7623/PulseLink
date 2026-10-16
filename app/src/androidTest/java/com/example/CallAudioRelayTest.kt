package com.example

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.call.AudioLink
import com.example.call.CallAudioRelay
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the call sound relay of the phone a call is on against a connection inside the phone itself.
 * Needs the Microphone permission. It plays only silence, and whether a call is on is made up, so
 * no call is placed.
 */
@RunWith(AndroidJUnit4::class)
class CallAudioRelayTest {

  private val relay
    get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PulseApplication).callAudioRelay

  /**
   * @return the loudest sample of each sound frame the host sent while it was fed silence
   */
  private fun hostFrames(inCall: Boolean, frames: Int): List<Int> {
    assumeTrue("needs the Microphone permission", relay.hasMicPermission())
    val loudness = mutableListOf<Int>()

    ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
      val otherPhone = Socket(InetAddress.getLoopbackAddress(), server.localPort)
      val hostEnd = server.accept()
      val host = thread { relay.runHost(AudioLink.overWifi(hostEnd), inCall = { inCall }) }

      otherPhone.soTimeout = 5000
      val silence = ByteArray(CallAudioRelay.FRAME_BYTES)
      repeat(frames) {
        CallAudioRelay.writeFrame(otherPhone.getOutputStream(), CallAudioRelay.FRAME_AUDIO, silence)
        val (type, payload) = CallAudioRelay.readFrame(otherPhone.getInputStream())!!
        assertEquals(CallAudioRelay.FRAME_AUDIO, type)
        assertEquals(CallAudioRelay.FRAME_BYTES, payload.size)
        loudness += CallAudioRelay.loudness(payload)
      }

      otherPhone.close()
      host.join(5000)
      assertFalse("the host should stop once the other phone is gone", host.isAlive)
    }
    return loudness
  }

  @Test
  fun hostRelaysItsMicrophoneWhileACallIsOn() {
    val loudness = hostFrames(inCall = true, frames = 75)

    assertEquals(75, loudness.size)
    Log.i("CallAudioRelayTest", "in call: loudest sample ${loudness.max()}, frames with sound ${loudness.count { it > 0 }}")
  }

  @Test
  fun hostKeepsItsMicrophoneToItselfWhenNoCallIsOn() {
    val loudness = hostFrames(inCall = false, frames = 75)

    assertTrue("nothing the microphone hears may leave the phone outside a call", loudness.all { it == 0 })
  }
}
