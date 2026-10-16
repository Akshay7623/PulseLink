package com.example

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Parcel
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.notification.NotificationReply
import com.example.service.PulseNotificationListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reads and answers chat and SMS notifications the way a paired device does, and checks that the
 * app which posted them receives the typed text. Runs on a phone; it posts nothing and messages nobody.
 */
@RunWith(AndroidJUnit4::class)
class NotificationReplyTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

  // Stands in for the chat or SMS app: the broadcast its "Reply" button would send
  private fun chatAppIntent(): PendingIntent {
    val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    return PendingIntent.getBroadcast(
      context,
      0,
      Intent(ACTION_CHAT_APP_REPLY).setPackage(context.packageName),
      PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
    )
  }

  private fun chatNotification(vararg actions: NotificationCompat.Action): Notification {
    val builder = NotificationCompat.Builder(context, "test").setContentTitle("Asha").setContentText("Hello")
    actions.forEach { builder.addAction(it) }
    return builder.build()
  }

  // What an SMS app posts for a conversation: its unread messages, "Mark as read" and a reply box
  private fun smsNotification(vararg messages: Triple<String, Long, Person?>): Notification {
    val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
    messages.forEach { (text, time, sender) -> style.addMessage(text, time, sender) }
    return NotificationCompat.Builder(context, "test")
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setStyle(style)
      .addAction(markAsReadAction())
      .addAction(replyAction())
      .build()
  }

  private fun replyAction() =
    NotificationCompat.Action.Builder(0, "Reply", chatAppIntent())
      .addRemoteInput(RemoteInput.Builder(KEY_CHAT_APP_TEXT).build())
      .build()

  private fun markAsReadAction() = NotificationCompat.Action.Builder(0, "Mark as read", chatAppIntent()).build()

  // A listener gets a notification from another process, so it only ever sees one read back from a parcel
  private fun asDelivered(notification: Notification): Notification {
    val parcel = Parcel.obtain()
    try {
      notification.writeToParcel(parcel, 0)
      parcel.setDataPosition(0)
      return Notification.CREATOR.createFromParcel(parcel)
    } finally {
      parcel.recycle()
    }
  }

  /** @return the text the chat app received, or null when nothing reached it */
  private fun answer(notification: Notification, text: String): String? {
    var received: String? = null
    val delivered = CountDownLatch(1)
    val chatApp =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          received = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CHAT_APP_TEXT)?.toString()
          delivered.countDown()
        }
      }
    ContextCompat.registerReceiver(
      context,
      chatApp,
      IntentFilter(ACTION_CHAT_APP_REPLY),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )

    try {
      assertTrue(NotificationReply.send(context, notification, text))
      assertTrue(delivered.await(5, TimeUnit.SECONDS))
      return received
    } finally {
      context.unregisterReceiver(chatApp)
    }
  }

  @Test
  fun typedTextReachesTheAppThatPostedTheNotification() {
    // The reply box is found even when it is not the first button
    val notification = chatNotification(markAsReadAction(), replyAction())

    assertEquals("On my way 👍", answer(notification, "On my way 👍"))
  }

  @Test
  fun notificationWithoutReplyBoxIsNotAnswered() {
    val notification = chatNotification(markAsReadAction())

    assertNull(NotificationReply.findAction(notification))
    assertFalse(NotificationReply.send(context, notification, "Hello"))
  }

  @Test
  fun smsConversationIsReadAndAnswered() {
    val amit = Person.Builder().setName("Amit").build()
    val sms =
      asDelivered(
        smsNotification(
          Triple("Kahan ho?", 1_000L, amit),
          Triple("Ghar pe", 2_000L, null),
          Triple("Call karo 📞", 3_000L, amit),
        )
      )

    val messages = PulseNotificationListener.chatMessages(sms.extras)

    assertEquals(listOf("Kahan ho?", "Ghar pe", "Call karo 📞"), messages.map { it.text })
    assertEquals(listOf("Amit", "", "Amit"), messages.map { it.sender })
    // The one without a sender is what the phone's own user wrote
    assertEquals(listOf(false, true, false), messages.map { it.fromMe })
    assertEquals(listOf(1_000L, 2_000L, 3_000L), messages.map { it.time })

    assertNotNull(NotificationReply.findAction(sms))
    assertEquals("5 min mein", answer(sms, "5 min mein"))
  }

  @Test
  fun onlyTheLatestMessagesOfALongSmsConversationAreKept() {
    val bank = Person.Builder().setName("+91 98765 43210").build()
    val sms = asDelivered(smsNotification(*Array(12) { Triple("Message ${it + 1}", it + 1L, bank) }))

    val messages = PulseNotificationListener.chatMessages(sms.extras)

    assertEquals((3..12).map { "Message $it" }, messages.map { it.text })
  }

  @Test
  fun notificationThatIsNotAConversationHasNoMessages() {
    assertTrue(PulseNotificationListener.chatMessages(asDelivered(chatNotification()).extras).isEmpty())
  }

  private companion object {
    const val ACTION_CHAT_APP_REPLY = "com.example.pulselink.test.CHAT_APP_REPLY"
    const val KEY_CHAT_APP_TEXT = "chat_app_text"
  }
}
