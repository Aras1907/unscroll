package com.unscroll.app

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import kotlin.math.abs

class GuardService : AccessibilityService() {

    /** A reel that was opened from a DM. It may be watched, but swiping to the next one ends it. */
    private class DmReel(val openedAt: Long) {
        var index = -1
        var travelled = 0
        var exitingSince = 0L
    }

    private lateinit var prefs: Prefs
    private lateinit var shield: Shield
    private val main = Handler(Looper.getMainLooper())

    private var current: Target? = null
    private var lastScreen = Screen.OK
    private var lastDmAt = 0L
    private var dmReel: DmReel? = null
    private var quietUntil = 0L
    private var evalPending = false

    override fun onServiceConnected() {
        prefs = Prefs(this)
        shield = Shield(this, ::primaryAction) {
            shield.hide()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!::shield.isInitialized || e.packageName == packageName) return
        val t = Target.of(e.packageName?.toString())
        if (t != null && e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && prefs.isOn(t)) onScroll(t, e)
        if (t != null || shield.isShowing || e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) scheduleEval()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (::shield.isInitialized) shield.hide()
        super.onDestroy()
    }

    private fun scheduleEval(delay: Long = 150) {
        if (evalPending) return
        evalPending = true
        main.postDelayed({ evalPending = false; evaluate() }, delay)
    }

    private fun evaluate() {
        val (t, root) = foreground() ?: return reset()
        if (!prefs.isOn(t)) return reset()
        if (t != current) {
            current = t
            lastScreen = Screen.OK
            dmReel = null
        }

        val now = SystemClock.uptimeMillis()
        val screen = Detectors.classify(t, Snapshot.of(root, screenH()))
        when (screen) {
            Screen.DM_THREAD -> { lastDmAt = now; dmReel = null }
            Screen.VIEWER -> {
                val reel = dmReel
                if (lastScreen != Screen.VIEWER) {
                    dmReel = if (prefs.allowDmReels && now - lastDmAt < DM_GRACE_MS) DmReel(now) else null
                } else if (reel != null && reel.exitingSince > 0 && now - reel.exitingSince > EXIT_TIMEOUT_MS) {
                    dmReel = null // the back press didn't take us out, so wall it off
                }
            }
            Screen.OK -> dmReel = null
            Screen.FEED, Screen.BLOCKED -> { dmReel = null; lastDmAt = 0 }
        }
        lastScreen = screen

        val wall = screen == Screen.BLOCKED || (screen == Screen.VIEWER && dmReel == null)
        when {
            wall -> block(t, screen)
            shield.isShowing && !(screen == Screen.FEED && shield.reason == Screen.FEED) -> shield.hide()
        }
    }

    private fun onScroll(t: Target, e: AccessibilityEvent) {
        val now = SystemClock.uptimeMillis()
        when (lastScreen) {
            Screen.FEED -> if (isFeedScroll(e)) block(t, Screen.FEED)
            Screen.VIEWER -> {
                val reel = dmReel ?: return
                if (reel.exitingSince > 0 || !coversScreen(e)) return
                if (now - reel.openedAt < SETTLE_MS) {
                    // Instagram scrolls the pager into place while opening; that isn't the user.
                    reel.index = e.fromIndex
                    return
                }
                reel.travelled += abs(e.scrollDeltaY.takeIf { it != -1 } ?: 0)
                val swiped = (reel.index >= 0 && e.fromIndex >= 0 && e.fromIndex != reel.index) ||
                    reel.travelled > screenH() / 3
                if (reel.index < 0) reel.index = e.fromIndex
                if (swiped) {
                    reel.exitingSince = now
                    prefs.countBlock()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Toast.makeText(this, "Just the one reel. Back to the chat.", Toast.LENGTH_SHORT).show()
                }
            }
            else -> Unit
        }
    }

    private fun block(t: Target, why: Screen) {
        if (shield.isShowing) return
        val now = SystemClock.uptimeMillis()
        if (now < quietUntil) {
            scheduleEval(quietUntil - now + 50)
            return
        }
        prefs.countBlock()
        shield.show(t, why)
    }

    private fun primaryAction(t: Target) {
        shield.hide()
        quietUntil = SystemClock.uptimeMillis() + 900
        when (t) {
            Target.YOUTUBE -> performGlobalAction(GLOBAL_ACTION_BACK)
            Target.REDDIT -> clickFirst { it.contentDescription?.toString().equals("Search", true) }
                .also { if (!it) performGlobalAction(GLOBAL_ACTION_HOME) }
            Target.INSTAGRAM, Target.INSTAGRAM_LITE -> {
                if (!clickInbox()) {
                    // Reels tab has no inbox button; hop to Home first, then try again.
                    clickFirst { it.viewIdResourceName?.endsWith(":id/feed_tab") == true ||
                        it.contentDescription?.toString()?.startsWith("Home", true) == true }
                    main.postDelayed({ if (!clickInbox()) performGlobalAction(GLOBAL_ACTION_HOME) }, 700)
                }
            }
        }
        scheduleEval(1000)
    }

    private fun clickInbox() = clickFirst { n ->
        val id = n.viewIdResourceName?.substringAfter(":id/") ?: ""
        val desc = n.contentDescription?.toString() ?: ""
        id == "action_bar_inbox_button" || id == "direct_tab" ||
            listOf("Message", "Direct", "Messenger", "Chats").any { desc.startsWith(it, true) }
    }

    /** Clicks the first node matching [pred] (or its nearest clickable parent). */
    private fun clickFirst(pred: (AccessibilityNodeInfo) -> Boolean): Boolean {
        val root = foreground()?.second ?: return false
        val stack = ArrayDeque(listOf(root))
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n.isVisibleToUser && pred(n)) {
                var c: AccessibilityNodeInfo? = n
                while (c != null && !c.isClickable) c = c.parent
                if (c?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(stack::addLast)
        }
        return false
    }

    /** The guarded app on top of the screen, if there is one. */
    private fun foreground(): Pair<Target, AccessibilityNodeInfo>? {
        val app = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val root = app?.root ?: rootInActiveWindow ?: return null
        val t = Target.of(root.packageName?.toString()) ?: return null
        return t to root
    }

    private fun reset() {
        current = null
        lastScreen = Screen.OK
        dmReel = null
        shield.hide()
    }

    private fun isFeedScroll(e: AccessibilityEvent): Boolean {
        val dx = e.scrollDeltaX
        val dy = e.scrollDeltaY
        if (dx != 0 && dx != -1 && (dy == 0 || dy == -1)) return false // stories tray etc.
        val src = e.source ?: return true
        val r = android.graphics.Rect().also(src::getBoundsInScreen)
        return r.height() > screenH() * 0.4
    }

    private fun coversScreen(e: AccessibilityEvent): Boolean {
        val src = e.source ?: return true
        val r = android.graphics.Rect().also(src::getBoundsInScreen)
        return r.height() > screenH() * 0.6
    }

    private fun screenH() = resources.displayMetrics.heightPixels

    companion object {
        private const val DM_GRACE_MS = 5000L
        private const val SETTLE_MS = 1200L
        private const val EXIT_TIMEOUT_MS = 1500L
    }
}
