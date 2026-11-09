package com.unscroll.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Covers the Instagram feeds (home, Reels tab, Explore grid) and undoes scrolling past the one reel or post
 * you opened, e.g. from a DM. YouTube Shorts and the Reddit feed get the scroll-back only.
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
    private var cover: View? = null

    override fun onServiceConnected() {
        prefs = Prefs(this)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!::prefs.isInitialized) return
        val pkg = e.packageName?.toString()
        val t = Target.of(pkg)
        if (t == null) {
            // Another app or the home screen came up: the cover has to go with Instagram.
            if (cover != null && pkg != packageName && e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) scheduleEval()
            return
        }
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) onScroll(t, e)
        scheduleEval()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideCover()
        super.onDestroy()
    }

    private fun scheduleEval() {
        if (evalPending) return
        evalPending = true
        main.postDelayed({ evalPending = false; evaluate() }, 150)
    }

    private fun evaluate() {
        val root = rootInActiveWindow
            ?: windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }?.root
            ?: return
        val pkg = root.packageName?.toString()
        val t = Target.of(pkg)
        if (t == null) {
            if (pkg != packageName) {
                hideCover()
                current = null
                screen = Screen.OK
            }
            return
        }
        val snap = Snapshot.of(root, screenH())
        val s = Detectors.classify(t, snap)
        if (t != current || s != screen) {
            if (pending != t to s) {
                pending = t to s
                scheduleEval()
                return
            }
            current = t
            screen = s
            screenSince = SystemClock.uptimeMillis()
            anchor = -1
            travelled = 0
            backedOut = false
        }
        pending = null

        val guard = Guard.of(t, s)
        if (s == Screen.BLOCKED && guard != null && prefs.isOn(guard)) showCover(Detectors.cover(snap)) else hideCover()
    }

    private fun onScroll(t: Target, e: AccessibilityEvent) {
        if (t != current || screen == Screen.BLOCKED) return
        val guard = Guard.of(t, screen) ?: return
        if (!prefs.isOn(guard)) return

        val src = e.source ?: return
        val r = Rect().also(src::getBoundsInScreen)
        val minHeight = if (screen == Screen.SINGLE) 0.6 else 0.4
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
            (screen == Screen.SINGLE && travelled > screenH() / 2)
        if (!forward || now - lastSnap < SNAP_GAP_MS) return
        lastSnap = now
        travelled = 0

        if (!scrollBack(src)) {
            // No way to scroll this view back, so leave the screen instead (once).
            if (screen != Screen.SINGLE || backedOut) return
            backedOut = true
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        prefs.countBlock()
        if (now - lastToast > TOAST_GAP_MS) {
            lastToast = now
            val msg = if (screen == Screen.SINGLE) "Just this one. Scrolling is off here." else "Feed scrolling is off."
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

    /** Puts an opaque, touch-eating panel over screen rows [area], or moves the one already up. */
    private fun showCover(area: IntRange) {
        val wm = getSystemService(WindowManager::class.java)
        val existing = cover
        val lp = (existing?.layoutParams as? WindowManager.LayoutParams) ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val height = (area.last - area.first).coerceAtLeast(1)
        if (existing != null && lp.y == area.first && lp.height == height) return
        lp.y = area.first
        lp.height = height
        // Bounds read mid-animation (an app opening) are scaled; look again once it has settled.
        main.postDelayed({ evaluate() }, 600)
        if (existing == null) {
            cover = coverView().also { wm.addView(it, lp) }
            prefs.countBlock()
        } else {
            wm.updateViewLayout(existing, lp)
        }
    }

    private fun hideCover() {
        val v = cover ?: return
        cover = null
        runCatching { getSystemService(WindowManager::class.java).removeView(v) }
    }

    private fun coverView(): View {
        fun text(s: String, size: Float, alpha: Float) = TextView(this).apply {
            text = s
            setTextColor(Color.WHITE)
            this.alpha = alpha
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            gravity = Gravity.CENTER
        }
        val pad = (32 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(18, 18, 18))
            isClickable = true // swallow taps and swipes so nothing underneath scrolls
            addView(text("The feed is off", 24f, 1f))
            addView(text("Messages, search and your profile still work.", 15f, 0.7f))
        }
    }

    private fun screenH() = resources.displayMetrics.heightPixels

    companion object {
        private const val SETTLE_MS = 1000L
        private const val SNAP_GAP_MS = 350L
        private const val TOAST_GAP_MS = 4000L
    }
}
