package com.example.notification

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * The reply box a chat app puts on its own notification (WhatsApp "Reply", SMS quick reply).
 * Filling it in sends the answer from that app, the same as typing it under the notification.
 */
object NotificationReply {

    private const val TAG = "NotificationReply"

    fun findAction(notification: Notification): Notification.Action? {
        // Some apps offer the reply box only to watches
        val actions = notification.actions.orEmpty().toList() + Notification.WearableExtender(notification).actions
        return actions.firstOrNull { action ->
            action.actionIntent != null && action.remoteInputs?.any { it.allowFreeFormInput } == true
        }
    }

    /**
     * @return false when the notification has no reply box or its app did not take the answer
     */
    fun send(context: Context, notification: Notification, text: String): Boolean {
        val action = findAction(notification) ?: return false
        return try {
            val results = Bundle()
            action.remoteInputs.forEach { results.putCharSequence(it.resultKey, text) }
            val fillIn = Intent()
            RemoteInput.addResultsToIntent(action.remoteInputs, fillIn, results)
            action.actionIntent.send(context, 0, fillIn)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending reply", e)
            false
        }
    }
}
