package com.example

import com.example.call.CallAudioRelay
import com.example.security.Pairing
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingTest {

  @Test
  fun codeIsReadTheSameHoweverItIsTyped() {
    assertEquals("ABCDEFGH", Pairing.normalize("abcd-efgh"))
    assertEquals("ABCDEFGH", Pairing.normalize(" ABCD EFGH "))
    assertEquals("ABCD-EFGH", Pairing.format("ABCDEFGH"))
    // Anything typed past the eighth character is dropped
    assertEquals("ABCDEFGH", Pairing.normalize("ABCD-EFGH-JK"))
  }

  @Test
  fun signatureFitsOnlyTheRequestItWasMadeFor() {
    val key = Pairing.deriveKey("ABCD-EFGH")
    val signature = Pairing.sign(key, "nonce1", "POST", "/api/call/dial", """{"number":"198"}""")

    assertEquals(signature, Pairing.sign(Pairing.deriveKey("abcdefgh"), "nonce1", "POST", "/api/call/dial", """{"number":"198"}"""))
    // A copied signature is no use for another number, another action, another challenge or another code
    assertNotEquals(signature, Pairing.sign(key, "nonce1", "POST", "/api/call/dial", """{"number":"199"}"""))
    assertNotEquals(signature, Pairing.sign(key, "nonce1", "POST", "/api/call/end", """{"number":"198"}"""))
    assertNotEquals(signature, Pairing.sign(key, "nonce2", "POST", "/api/call/dial", """{"number":"198"}"""))
    assertNotEquals(signature, Pairing.sign(Pairing.deriveKey("ABCD-EFGJ"), "nonce1", "POST", "/api/call/dial", """{"number":"198"}"""))
  }

  @Test
  fun soundFramesArriveAsTheyWereSent() {
    val sound = ByteArray(CallAudioRelay.FRAME_BYTES) { (it % 251).toByte() }
    val wire = ByteArrayOutputStream()
    CallAudioRelay.writeFrame(wire, CallAudioRelay.FRAME_AUDIO, sound)
    CallAudioRelay.writeFrame(wire, CallAudioRelay.FRAME_END, "no_call".toByteArray())

    val input = ByteArrayInputStream(wire.toByteArray())
    val first = CallAudioRelay.readFrame(input)!!
    val second = CallAudioRelay.readFrame(input)!!

    assertEquals(CallAudioRelay.FRAME_AUDIO, first.first)
    assertArrayEquals(sound, first.second)
    assertEquals(CallAudioRelay.FRAME_END, second.first)
    assertEquals("no_call", String(second.second))
    assertNull(CallAudioRelay.readFrame(input))
  }

  @Test
  fun frameCutOffByADroppedConnectionIsAnError() {
    val wire = ByteArrayOutputStream()
    CallAudioRelay.writeFrame(wire, CallAudioRelay.FRAME_AUDIO, ByteArray(CallAudioRelay.FRAME_BYTES))
    val cutOff = wire.toByteArray().copyOf(100)

    assertThrows(EOFException::class.java) { CallAudioRelay.readFrame(ByteArrayInputStream(cutOff)) }
  }

  @Test
  fun loudnessIsTheLoudestSample() {
    assertEquals(0, CallAudioRelay.loudness(ByteArray(CallAudioRelay.FRAME_BYTES)))
    // 16-bit little-endian samples: 1000, -20000, 300
    val pcm = byteArrayOf(0xE8.toByte(), 0x03, 0xE0.toByte(), 0xB1.toByte(), 0x2C, 0x01)
    assertEquals(20000, CallAudioRelay.loudness(pcm))
  }
}
