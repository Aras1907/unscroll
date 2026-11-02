package com.unscroll.app

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** What the user is currently looking at inside a guarded app. */
enum class Screen {
    /** Anything we don't care about (search, profile, settings...). */
    OK,
    /** A DM conversation. Remembered so a reel opened from it can be let through. */
    DM_THREAD,
    /** A scrollable home feed: fine to look at, blocked as soon as it's scrolled. */
    FEED,
    /** A full-screen reel / short viewer. Blocked unless it was opened from a DM. */
    VIEWER,
    /** Always blocked (the Reels tab, the Shorts tab). */
    BLOCKED,
}

/** Flattened copy of the bits of the view tree we need, so detectors stay simple. */
class Snapshot(val screenH: Int) {
    class Node(
        val id: String,
        val desc: String,
        val text: String,
        val cls: String,
        val selected: Boolean,
        val editable: Boolean,
        val bounds: Rect,
    )

    val nodes = ArrayList<Node>(256)

    fun hasId(pred: (String) -> Boolean) = nodes.any { it.id.isNotEmpty() && pred(it.id) }

    /** A node matching [pred] that covers most of the screen, i.e. a full-screen player rather than a thumbnail. */
    fun hasFullScreen(pred: (Node) -> Boolean) =
        nodes.any { pred(it) && it.bounds.height() > screenH * 0.6 }

    fun selected(pred: (Node) -> Boolean) = nodes.any { it.selected && pred(it) }

    fun labelled(vararg words: String) = nodes.any { n ->
        words.any { w -> n.desc.equals(w, true) || n.text.equals(w, true) }
    }

    companion object {
        private const val MAX_NODES = 2500

        fun of(root: AccessibilityNodeInfo, screenH: Int): Snapshot {
            val snap = Snapshot(screenH)
            val stack = ArrayDeque<AccessibilityNodeInfo>()
            stack.addLast(root)
            while (stack.isNotEmpty() && snap.nodes.size < MAX_NODES) {
                val n = stack.removeLast()
                val r = Rect().also(n::getBoundsInScreen)
                snap.nodes += Node(
                    id = n.viewIdResourceName?.substringAfter(":id/") ?: "",
                    desc = n.contentDescription?.toString() ?: "",
                    text = n.text?.toString() ?: n.hintText?.toString() ?: "",
                    cls = n.className?.toString() ?: "",
                    selected = n.isSelected,
                    editable = n.isEditable,
                    bounds = r,
                )
                for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let(stack::addLast)
            }
            return snap
        }
    }
}

object Detectors {

    fun classify(t: Target, s: Snapshot): Screen = when (t) {
        Target.INSTAGRAM -> instagram(s)
        Target.INSTAGRAM_LITE -> instagramLite(s)
        Target.YOUTUBE -> youtube(s)
        Target.REDDIT -> reddit(s)
    }

    private fun instagram(s: Snapshot): Screen {
        if (s.selected { it.id == "clips_tab" || it.desc.equals("Reels", true) }) return Screen.BLOCKED
        val viewer = s.hasFullScreen {
            it.id.startsWith("clips_viewer") || it.id == "root_clips_layout" || it.id.startsWith("clips_video")
        }
        if (viewer) return Screen.VIEWER
        val thread = s.hasId { it.contains("thread_composer") || it.startsWith("direct_thread") } ||
            s.nodes.any { it.editable && it.text.startsWith("Message", true) }
        if (thread) return Screen.DM_THREAD
        if (s.selected { it.id == "feed_tab" || it.id == "search_tab" }) return Screen.FEED
        return Screen.OK
    }

    // Lite draws most of its UI itself and exposes very little, so this one leans on labels.
    private fun instagramLite(s: Snapshot): Screen {
        if (s.selected { it.desc.contains("Reels", true) || it.text.equals("Reels", true) }) return Screen.BLOCKED
        val thread = s.nodes.any { it.editable && (it.text.contains("Message", true) || it.desc.contains("Message", true)) }
        if (thread) return Screen.DM_THREAD
        val actions = listOf("Like", "Comment", "Share").count { w ->
            s.nodes.any { it.desc.startsWith(w, true) }
        }
        if (actions == 3 && s.nodes.any { it.desc.contains("reel", true) || it.text.contains("reel", true) }) {
            return Screen.VIEWER
        }
        if (s.selected { it.desc.startsWith("Home", true) }) return Screen.FEED
        return Screen.OK
    }

    private fun youtube(s: Snapshot): Screen {
        if (s.selected { it.desc.equals("Shorts", true) }) return Screen.BLOCKED
        val shortsPlayer = s.hasFullScreen {
            it.id == "reel_recycler" || it.id.startsWith("reel_player") || it.id.startsWith("reel_watch") ||
                it.id == "shorts_container"
        }
        return if (shortsPlayer) Screen.BLOCKED else Screen.OK
    }

    private fun reddit(s: Snapshot): Screen {
        // The home/popular feed is the only screen with both the bottom tab bar and voting buttons.
        val bottom = s.screenH * 0.8
        val tabBar = s.nodes.count { n ->
            n.bounds.top > bottom && listOf("Communities", "Inbox", "Chat", "Create", "Answers").any {
                n.desc.startsWith(it, true) || n.text.equals(it, true)
            }
        } >= 2
        val posts = s.nodes.any { it.desc.contains("upvote", true) || it.id.contains("vote", true) }
        return if (tabBar && posts) Screen.FEED else Screen.OK
    }
}
