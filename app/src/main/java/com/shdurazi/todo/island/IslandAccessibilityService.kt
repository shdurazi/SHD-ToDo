package com.shdurazi.todo.island

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * A home for the island's window. An accessibility service is allowed to add overlay windows that sit above the status
 * bar and the lock screen, which a normal "display over other apps" window cannot. It listens to nothing and does nothing
 * else (see res/xml/island_accessibility.xml: it cannot read the screen).
 */
class IslandAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        IslandController.a11yConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // nothing to do
    }

    override fun onInterrupt() {
        // nothing to do
    }

    override fun onUnbind(intent: Intent?): Boolean {
        IslandController.a11yDisconnected(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        IslandController.a11yDisconnected(this)
        super.onDestroy()
    }
}
