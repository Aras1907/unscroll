package com.unscroll.app

import android.content.Context
import java.time.LocalDate

enum class Target(val pkg: String, val key: String) {
    INSTAGRAM("com.instagram.android", "ig"),
    INSTAGRAM_LITE("com.instagram.lite", "ig_lite"),
    YOUTUBE("com.google.android.youtube", "yt"),
    REDDIT("com.reddit.frontpage", "reddit");

    companion object {
        fun of(pkg: String?) = entries.firstOrNull { it.pkg == pkg }
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("unscroll", Context.MODE_PRIVATE)

    fun isOn(t: Target) = sp.getBoolean(t.key, true)
    fun setOn(t: Target, on: Boolean) = sp.edit().putBoolean(t.key, on).apply()

    var allowDmReels: Boolean
        get() = sp.getBoolean("allow_dm_reels", true)
        set(v) = sp.edit().putBoolean("allow_dm_reels", v).apply()

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
