package com.example.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.CallResult
import com.example.model.CallState

/**
 * Lets a paired device place, answer and end phone calls on this device. Nothing is allowed until
 * the user turns on "Allow Remote Calls" here and grants the Phone permission. The call itself, and
 * its sound, stay on this device.
 */
class CallController(private val context: Context) {

    private val prefs = context.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_REMOTE_CALLS, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_REMOTE_CALLS, value).apply()

    fun hasPermission(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // Why a paired device may not control calls right now, or null when it may
    private fun refusal(): CallResult? = when {
        !isEnabled -> CallResult.DISABLED
        !hasPermission() -> CallResult.NO_PERMISSION
        telecomManager == null -> CallResult.UNSUPPORTED
        else -> null
    }

    /**
     * @return CallState.RINGING or ONGOING for a phone call a paired device may act on, empty otherwise
     */
    @SuppressLint("MissingPermission")
    fun callState(): String {
        if (refusal() != null) return ""
        return try {
            @Suppress("DEPRECATION")
            when (telephonyManager?.callState) {
                TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
                TelephonyManager.CALL_STATE_OFFHOOK -> CallState.ONGOING
                else -> ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call state", e)
            ""
        }
    }

    /**
     * Calls a number from this device's SIM. The call goes to the connected Bluetooth headset;
     * with none connected it starts on the loudspeaker, since whoever asked for the call is not
     * holding this device to their ear.
     * @param speaker use the loudspeaker even when a Bluetooth headset is connected
     */
    @SuppressLint("MissingPermission")
    fun dial(rawNumber: String, speaker: Boolean): CallResult {
        refusal()?.let { return it }
        val number = dialableNumber(rawNumber) ?: return CallResult.INVALID_NUMBER

        return try {
            val startOnSpeaker = speaker || BluetoothHeadset.connectedName(context) == null
            val extras = Bundle().apply {
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, startOnSpeaker)
                // With two SIMs set to "ask every time" the call would wait for a tap on this device's screen
                if (telecomManager?.getDefaultOutgoingPhoneAccount("tel") == null) {
                    telecomManager?.callCapablePhoneAccounts?.firstOrNull()?.let {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
                    }
                }
            }
            telecomManager?.placeCall(Uri.fromParts("tel", number, null), extras)
            CallResult.OK
        } catch (e: Exception) {
            Log.e(TAG, "Error placing call", e)
            CallResult.FAILED
        }
    }

    @SuppressLint("MissingPermission")
    fun answer(): CallResult {
        refusal()?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CallResult.UNSUPPORTED
        if (callState() != CallState.RINGING) return CallResult.NO_CALL

        return try {
            @Suppress("DEPRECATION")
            telecomManager?.acceptRingingCall()
            CallResult.OK
        } catch (e: Exception) {
            Log.e(TAG, "Error answering call", e)
            CallResult.FAILED
        }
    }

    /**
     * Hangs up the call in progress, or declines the one that is ringing.
     */
    @SuppressLint("MissingPermission")
    fun end(): CallResult {
        refusal()?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return CallResult.UNSUPPORTED

        return try {
            @Suppress("DEPRECATION")
            if (telecomManager?.endCall() == true) CallResult.OK else CallResult.NO_CALL
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call", e)
            CallResult.FAILED
        }
    }

    companion object {
        private const val TAG = "CallController"
        private const val KEY_ALLOW_REMOTE_CALLS = "allow_remote_calls"

        // One "make and manage phone calls" prompt covers all of them
        val PERMISSIONS: Array<String> = buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(Manifest.permission.ANSWER_PHONE_CALLS)
        }.toTypedArray()

        private val DIALABLE = Regex("""\+?[0-9]{3,15}""")

        /**
         * @return the number without spacing, or null when it is not a plain phone number. Codes with
         * * or # are refused: they can change SIM settings such as call forwarding.
         */
        fun dialableNumber(raw: String): String? {
            val number = raw.filterNot { it.isWhitespace() || it in "-()" }
            return number.takeIf { DIALABLE.matches(it) }
        }
    }
}
