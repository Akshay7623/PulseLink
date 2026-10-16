package com.example.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Exists only so the call sound relay may use the microphone while a phone call is in progress:
 * Android mutes it for every other kind of app. It asks for no events and never reads the screen.
 */
class PulseAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
