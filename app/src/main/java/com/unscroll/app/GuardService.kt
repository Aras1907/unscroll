package com.unscroll.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

/**
 * Lets you look at whatever is on screen, but undoes scrolling forward in Reels, Shorts and feeds.
 * A reel opened from a DM plays like any other; swiping to the next one just snaps back.
 */
class GuardService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())

    private var current: Target? = null
    private var screen = Screen.OK
    private var screenSince = 0L
    /** Position of the item you landed on. Anything past it gets scrolled back. */
    private var anchor = -1
    /** For views that don't report positions: how far you've dragged a short. */
    private var travelled = 0
    private var lastSnap = 0L
    private var lastToast = 0L
    private var backedOut = false
    private var evalPending = false
    /** A screen change only counts once two evaluations in a row agree, so loading blips don't reset [anchor]. */
    private var pending: Pair<Target, Screen>? = null

    override fun onServiceConnected() {
        prefs = Prefs(this)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!::prefs.isInitialized) return
        val t = Target.of(e.packageName?.toString()) ?: return
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) onScroll(t, e)
        scheduleEval()
    }

    override fun onInterrupt() {}

    private fun scheduleEval() {
        if (evalPending) return
        evalPending = true
        main.postDelayed({ evalPending = false; evaluate() }, 150)
    }

    private fun evaluate() {
        val (t, root) = foreground() ?: return
        val now = SystemClock.uptimeMillis()
        val s = Detectors.classify(t, Snapshot.of(root, screenH()))
        if (t == current && s == screen) {
            pending = null
            return
        }
        if (pending != t to s) {
            pending = t to s
            scheduleEval()
            return
        }
        pending = null
        current = t
        screen = s
        screenSince = now
        anchor = -1
        travelled = 0
        backedOut = false
    }

    private fun onScroll(t: Target, e: AccessibilityEvent) {
        if (t != current) return
        val guard = Guard.of(t, screen) ?: return
        if (!prefs.isOn(guard)) return

        val src = e.source ?: return
        val r = Rect().also(src::getBoundsInScreen)
        val minHeight = if (screen == Screen.SHORTS) 0.6 else 0.4
        if (r.height() < screenH() * minHeight) return // comments sheet, stories tray, etc.
        val dx = e.scrollDeltaX
        val dy = e.scrollDeltaY
        if (dx != 0 && dx != -1 && (dy == 0 || dy == -1)) return // sideways

        val now = SystemClock.uptimeMillis()
        val idx = e.fromIndex
        if (anchor < 0 && now - screenSince < SETTLE_MS) {
            // Apps scroll their pager into place while a screen opens; that's not the user.
            anchor = idx
            return
        }
        if (anchor < 0) anchor = 0
        if (idx in 0 until anchor) anchor = idx

        if (idx < 0 && dy > 0) travelled += dy

        val forward = idx > anchor ||
            (screen == Screen.FEED && dy > 0) ||
            (screen == Screen.SHORTS && travelled > screenH() / 2)
        if (!forward || now - lastSnap < SNAP_GAP_MS) return
        lastSnap = now
        travelled = 0

        if (!scrollBack(src)) {
            // No way to scroll this view back, so leave the screen instead (once).
            if (screen != Screen.SHORTS || backedOut) return
            backedOut = true
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        prefs.countBlock()
        if (now - lastToast > TOAST_GAP_MS) {
            lastToast = now
            val msg = if (screen == Screen.SHORTS) "Just this one. Scrolling is off here." else "Feed scrolling is off."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /** Scrolls [node], or the nearest scrollable view around it, back by a page. */
    private fun scrollBack(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isScrollable && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
            n = n.parent
        }
        return false
    }

    /** The guarded app on top of the screen, if there is one. */
    private fun foreground(): Pair<Target, AccessibilityNodeInfo>? {
        val root = rootInActiveWindow
            ?: windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }?.root
            ?: return null
        val t = Target.of(root.packageName?.toString()) ?: return null
        return t to root
    }

    private fun screenH() = resources.displayMetrics.heightPixels

    companion object {
        private const val SETTLE_MS = 1000L
        private const val SNAP_GAP_MS = 350L
        private const val TOAST_GAP_MS = 4000L
    }
}
