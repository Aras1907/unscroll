package com.unscroll.app

import android.content.Context
import java.time.LocalDate

enum class Target(val pkg: String) {
    INSTAGRAM("com.instagram.android"),
    YOUTUBE("com.google.android.youtube"),
    REDDIT("com.reddit.frontpage");

    companion object {
        fun of(pkg: String?) = entries.firstOrNull { it.pkg == pkg }
    }
}

/** Each switch in the app. */
enum class Guard(val key: String) {
    // Keys kept from 1.x so existing switches carry over.
    INSTAGRAM("ig_reels"),
    YOUTUBE_SHORTS("yt_shorts"),
    REDDIT_FEED("reddit_feed");

    companion object {
        fun of(t: Target, s: Screen): Guard? = if (s == Screen.OK) null else when (t) {
            Target.INSTAGRAM -> INSTAGRAM
            Target.YOUTUBE -> YOUTUBE_SHORTS
            Target.REDDIT -> REDDIT_FEED
        }
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("unscroll", Context.MODE_PRIVATE)

    fun isOn(g: Guard) = sp.getBoolean(g.key, true)
    fun setOn(g: Guard, on: Boolean) = sp.edit().putBoolean(g.key, on).apply()

    val blockedToday get() = sp.getInt(todayKey(), 0)
    val blockedTotal get() = sp.getInt("blocked_total", 0)

    fun countBlock() {
        sp.edit()
            .putInt(todayKey(), blockedToday + 1)
            .putInt("blocked_total", blockedTotal + 1)
            .apply()
    }

    private fun todayKey() = "blocked_" + LocalDate.now()
}
