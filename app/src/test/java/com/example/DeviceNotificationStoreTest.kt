package com.example

import com.example.model.CallState
import com.example.model.ChatMessage
import com.example.model.DeviceNotification
import com.example.notification.DeviceNotificationStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceNotificationStoreTest {

  private fun call(key: String, title: String, text: String, postTime: Long) =
    DeviceNotification(key = key, title = title, text = text, category = "call", postTime = postTime)

  @Before
  fun emptyStore() {
    DeviceNotificationStore.clear()
  }

  @Test
  fun callChangingStateStaysOneEntry() {
    DeviceNotificationStore.add(call("call", "Unknown", "Incoming call", 1))
    DeviceNotificationStore.add(call("call", "Asha", "Incoming call", 2))
    DeviceNotificationStore.add(call("call", "Asha", "Ongoing call", 3))

    assertEquals(listOf("Ongoing call"), DeviceNotificationStore.snapshot().map { it.text })
  }

  @Test
  fun onlyWhatIsStillShowingIsListed() {
    DeviceNotificationStore.add(call("call", "Asha", "Ongoing call", 1))
    DeviceNotificationStore.add(DeviceNotification(key = "chat", title = "Ravi", text = "Hi", postTime = 2))

    assertTrue(DeviceNotificationStore.remove("call"))

    assertEquals(listOf("chat"), DeviceNotificationStore.snapshot().map { it.key })
    assertEquals(listOf("chat"), DeviceNotificationStore.activeKeys())
    // Already gone, so there is nothing new to tell the peer
    assertFalse(DeviceNotificationStore.remove("call"))
  }

  @Test
  fun newestIsListedFirst() {
    DeviceNotificationStore.add(DeviceNotification(key = "old", title = "Old", postTime = 1))
    DeviceNotificationStore.add(DeviceNotification(key = "new", title = "New", postTime = 2))

    assertEquals(listOf("new", "old"), DeviceNotificationStore.snapshot().map { it.key })
  }

  @Test
  fun onlyChangedContentIsNewsForThePeer() {
    assertTrue(DeviceNotificationStore.add(call("call", "Asha", "Incoming call", 1)))
    assertFalse(DeviceNotificationStore.add(call("call", "Asha", "Incoming call", 2)))
    assertTrue(DeviceNotificationStore.add(call("call", "Asha", "Ongoing call", 3)))

    // The same words again after it was dismissed are a new notification
    DeviceNotificationStore.remove("call")
    assertTrue(DeviceNotificationStore.add(call("call", "Asha", "Ongoing call", 4)))
  }

  @Test
  fun sameWordsSentAgainInAChatAreNews() {
    val first = ChatMessage(sender = "Ravi", text = "Ok", time = 1)
    val second = ChatMessage(sender = "Ravi", text = "Ok", time = 2)
    val chat = DeviceNotification(key = "chat", title = "Ravi", text = "Ok", isChat = true, messages = listOf(first))

    assertTrue(DeviceNotificationStore.add(chat))
    assertFalse(DeviceNotificationStore.add(chat))
    assertTrue(DeviceNotificationStore.add(chat.copy(messages = listOf(first, second))))
  }

  @Test
  fun callBeingPickedUpIsNewsEvenWhenItsWordsStayTheSame() {
    val ringing = call("call", "Asha", "Phone", 1).copy(callState = CallState.RINGING)

    assertTrue(DeviceNotificationStore.add(ringing))
    assertFalse(DeviceNotificationStore.add(ringing))
    // The peer has to swap its Answer button for Hang up
    assertTrue(DeviceNotificationStore.add(ringing.copy(callState = CallState.ONGOING)))
  }

  @Test
  fun groupChatsNameWhoWroteEachMessage() {
    assertEquals("Hi", ChatMessage(sender = "Ravi", text = "Hi").displayText("Ravi"))
    assertEquals("Ravi: Hi", ChatMessage(sender = "Ravi", text = "Hi").displayText("Family"))
    assertEquals("You: On my way", ChatMessage(text = "On my way", fromMe = true).displayText("Ravi"))
  }
}
