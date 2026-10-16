package com.example

import com.example.call.AudioLink
import com.example.call.BluetoothSoundLink
import com.example.call.CallAudioRelay
import com.example.model.CallResult
import com.example.security.Pairing
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The opening exchange of the Bluetooth call sound connection, run between its two halves over a
 * connection inside the test. No Bluetooth is involved.
 */
class BluetoothSoundLinkTest {

  private val nonce = "0123456789abcdef0123456789abcdef"

  // The two ends of one connection, the way a Bluetooth socket joins the two phones
  private fun connection(): Pair<AudioLink, AudioLink> {
    val toHost = PipedInputStream(8192)
    val toOtherPhone = PipedInputStream(8192)
    val host = AudioLink(toHost, PipedOutputStream(toOtherPhone), true, Closeable { })
    val otherPhone = AudioLink(toOtherPhone, PipedOutputStream(toHost), true, Closeable { })
    return host to otherPhone
  }

  // The host accepts only the proof made with its own pairing code for this one-time value
  private fun hostAnswer(host: AudioLink, hostCode: String, refusal: CallResult): () -> CallResult {
    val expected = Pairing.sign(
      Pairing.deriveKey(hostCode), nonce, BluetoothSoundLink.SIGNED_METHOD, BluetoothSoundLink.SIGNED_PATH, ""
    )
    var told: CallResult? = null
    val hostThread = thread {
      told = BluetoothSoundLink.hostHandshake(host, nonce, verify = { it == expected }, refusal = { refusal })
    }
    return {
      hostThread.join(5000)
      told!!
    }
  }

  /**
   * @return what the host answered, and what the other phone made of it
   */
  private fun handshake(hostCode: String, typedCode: String, refusal: CallResult = CallResult.OK): Pair<CallResult, CallResult> {
    val (host, otherPhone) = connection()
    val told = hostAnswer(host, hostCode, refusal)
    val understood = BluetoothSoundLink.clientHandshake(otherPhone, Pairing.deriveKey(typedCode))
    return told() to understood
  }

  @Test
  fun phoneThatKnowsThePairingCodeIsLetIn() {
    assertEquals(CallResult.OK to CallResult.OK, handshake(hostCode = "ABCD-EFGH", typedCode = "abcdefgh"))
  }

  @Test
  fun phoneWithAnotherCodeIsTurnedAway() {
    assertEquals(
      CallResult.NOT_PAIRED to CallResult.NOT_PAIRED,
      handshake(hostCode = "ABCD-EFGH", typedCode = "ABCD-EFGJ")
    )
  }

  @Test
  fun hostSaysWhyItWillNotRelayRightNow() {
    assertEquals(
      CallResult.RELAY_OFF to CallResult.RELAY_OFF,
      handshake(hostCode = "ABCD-EFGH", typedCode = "ABCD-EFGH", refusal = CallResult.RELAY_OFF)
    )
  }

  @Test
  fun proofMadeForAWifiRequestDoesNotOpenBluetooth() {
    val (host, otherPhone) = connection()
    val told = hostAnswer(host, "ABCD-EFGH", CallResult.OK)

    val (type, challenge) = CallAudioRelay.readFrame(otherPhone.input)!!
    assertEquals(BluetoothSoundLink.FRAME_CHALLENGE, type)
    val wifiProof = Pairing.sign(Pairing.deriveKey("ABCD-EFGH"), String(challenge), "POST", "/api/audio/stream", "{}")
    CallAudioRelay.writeFrame(otherPhone.output, BluetoothSoundLink.FRAME_PROOF, wifiProof.toByteArray())

    assertEquals(CallResult.NOT_PAIRED, told())
    val (answerType, reason) = CallAudioRelay.readFrame(otherPhone.input)!!
    assertEquals(BluetoothSoundLink.FRAME_ANSWER, answerType)
    assertEquals(CallResult.NOT_PAIRED.reason, String(reason))
  }

  @Test
  fun connectionThatDoesNotStartWithAChallengeIsGivenUp() {
    val wire = ByteArrayOutputStream()
    CallAudioRelay.writeFrame(wire, CallAudioRelay.FRAME_AUDIO, ByteArray(CallAudioRelay.FRAME_BYTES))
    val stranger = AudioLink(ByteArrayInputStream(wire.toByteArray()), ByteArrayOutputStream(), true, Closeable { })

    assertThrows(IOException::class.java) { BluetoothSoundLink.clientHandshake(stranger, Pairing.deriveKey("ABCD-EFGH")) }
  }
}
