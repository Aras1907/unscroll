package com.unscroll.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen wall drawn over the guarded app. Swallows every touch underneath it. */
class Shield(
    private val context: Context,
    private val onPrimary: (Target) -> Unit,
    private val onLeave: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val isShowing get() = view != null
    var reason: Screen? = null
        private set

    fun show(target: Target, why: Screen) {
        if (view != null) return
        reason = why
        val v = build(target, why)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        v.alpha = 0f
        wm.addView(v, lp)
        v.animate().alpha(1f).setDuration(180).start()
        view = v
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        reason = null
    }

    private fun build(target: Target, why: Screen): View {
        val dp = context.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val (title, body) = copy(target, why)
        val primary = when (target) {
            Target.INSTAGRAM, Target.INSTAGRAM_LITE -> "Open my DMs"
            Target.YOUTUBE -> "Take me back"
            Target.REDDIT -> "Search for something"
        }
        val leave = "Close " + when (target) {
            Target.INSTAGRAM -> "Instagram"
            Target.INSTAGRAM_LITE -> "Instagram Lite"
            Target.YOUTUBE -> "YouTube"
            Target.REDDIT -> "Reddit"
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32.dp(), 0, 32.dp(), 0)
        }
        column.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_launcher_fg)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1B1A2E.toInt()) }
        }, LinearLayout.LayoutParams(112.dp(), 112.dp()).apply { bottomMargin = 28.dp() })
        column.addView(TextView(context).apply {
            text = title
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        })
        column.addView(TextView(context).apply {
            text = body
            textSize = 16f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 12.dp(); bottomMargin = 40.dp() })
        column.addView(pill(primary, filled = true) { onPrimary(target) }, LinearLayout.LayoutParams(-1, 56.dp()))
        column.addView(pill(leave, filled = false) { onLeave() },
            LinearLayout.LayoutParams(-1, 56.dp()).apply { topMargin = 12.dp() })

        return FrameLayout(context).apply {
            setBackgroundColor(0xF5101014.toInt())
            isClickable = true // eat touches so nothing scrolls underneath
            addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        }
    }

    private fun pill(label: String, filled: Boolean, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (filled) 0xFF1A0E0C.toInt() else Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = 100f * context.resources.displayMetrics.density
            if (filled) setColor(0xFFFF6B5B.toInt()) else setStroke((1.5f * context.resources.displayMetrics.density).toInt(), 0x55FFFFFF)
        }
        setOnClickListener { onClick() }
    }

    private fun copy(target: Target, why: Screen): Pair<String, String> = when {
        target == Target.YOUTUBE -> "Shorts are off" to
            "Long videos are still all yours. The 30-second slot machine isn't."
        target == Target.REDDIT -> "End of the feed" to
            "You came here for something. Search for it, find it, then go do your thing."
        why == Screen.FEED -> "That's enough feed" to
            "Nothing down there is going to be worth the next hour. Your messages are one tap away."
        else -> listOf(
            "Not this time" to "Reels are where afternoons go to disappear. Reels your friends send you still open fine.",
            "You were about to lose an hour" to "Go reply to someone instead. Reels sent in your DMs still work.",
        ).random()
    }
}
