package com.unscroll.app

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** What the user is currently looking at inside a guarded app. */
enum class Screen {
    /** Anything we don't care about (DMs, search, profile, regular videos...). */
    OK,
    /** One reel / short / post, e.g. opened from a DM. The current one stays; moving on to the next is undone. */
    SINGLE,
    /** A feed you can see the top of, but scrolling down is undone (Reddit). */
    FEED,
    /** A feed nobody opened on purpose (Instagram home, Reels tab, Explore grid). Covered up entirely. */
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
        val focused: Boolean,
        /** The text is real input, not a hint. */
        val typed: Boolean,
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
                    focused = n.isFocused,
                    typed = !n.text.isNullOrEmpty() && !n.isShowingHintText,
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

    /**
     * The part of a [Screen.BLOCKED] screen to cover: everything between the top bar (kept only if it holds
     * the DM button or a search box) and the bottom tab bar, so DMs, search and the other tabs stay reachable.
     */
    fun cover(s: Snapshot): IntRange {
        val h = s.screenH
        val top = s.nodes
            .filter { n -> n.bounds.bottom in 1..(h * 0.3).toInt() && (n.editable || isDmButton(n)) }
            .maxOfOrNull { it.bounds.bottom } ?: 0
        val bottom = s.nodes
            .filter { n -> n.bounds.top > h * 0.8 && n.bounds.height() > 0 && isTab(n) }
            .minOfOrNull { it.bounds.top } ?: h
        return top..bottom
    }

    private fun isDmButton(n: Snapshot.Node) =
        n.id.contains("inbox") || n.id.contains("direct") ||
            listOf("Messenger", "Direct", "Message", "Messages", "Chats").any { n.desc.startsWith(it, true) }

    private fun isTab(n: Snapshot.Node) =
        n.id.endsWith("_tab") ||
            listOf("Home", "Search", "Explore", "Reels", "Profile", "Create", "New post").any { n.desc.startsWith(it, true) }

    private fun instagram(s: Snapshot): Screen {
        val tab = { id: String -> s.selected { it.id == id && it.bounds.top > s.screenH * 0.75 } }
        if (tab("clips_tab")) return Screen.BLOCKED
        val viewer = s.hasFullScreen {
            it.id.startsWith("clips_viewer") || it.id == "root_clips_layout" || it.id.startsWith("clips_video")
        }
        if (viewer) return Screen.SINGLE // a reel opened from a DM, search, a profile...
        // The editable check keeps a chat from ever being covered, even if a hidden tab bar still says "home".
        if (tab("feed_tab") && s.nodes.none { it.editable }) return Screen.BLOCKED
        if (tab("search_tab")) {
            // The Explore grid sits under an idle search box. Typing in it, or anything you opened from it, is fine.
            val box = s.nodes.firstOrNull { it.editable }
            if (box != null && !box.focused && !box.typed && box.bounds.bottom < s.screenH * 0.3 &&
                !s.hasId { it.startsWith("row_feed_") }
            ) return Screen.BLOCKED
        }
        // A list of posts outside Home: the one you opened (e.g. from a DM) stays, the ones after it don't.
        if (s.hasId { it.startsWith("row_feed_") }) return Screen.SINGLE
        return Screen.OK
    }

    // Lite draws most of its UI itself and exposes very little, so this one leans on labels.
    private fun instagramLite(s: Snapshot): Screen {
        if (s.selected { it.desc.contains("Reels", true) || it.text.equals("Reels", true) }) return Screen.BLOCKED
        if (s.nodes.any { it.editable }) return Screen.OK // a chat or search box
        val actions = listOf("Like", "Comment", "Share").count { w ->
            s.nodes.any { it.desc.startsWith(w, true) }
        }
        if (actions == 3 && s.nodes.any { it.desc.contains("reel", true) || it.text.contains("reel", true) }) {
            return Screen.SINGLE
        }
        // Lite never marks the Home tab selected; feed posts are the ones labelled "N likes" / "View all N comments".
        val post = Regex("""^[\d.,]+[KM]? likes?$|^View all .*comments?$""", RegexOption.IGNORE_CASE)
        if (s.nodes.any { it.desc.startsWith("Home", true) } && s.nodes.any { post.containsMatchIn(it.desc) }) return Screen.BLOCKED
        return Screen.OK
    }

    private fun youtube(s: Snapshot): Screen {
        if (s.selected { it.desc.equals("Shorts", true) }) return Screen.SINGLE
        val shortsPlayer = s.hasFullScreen {
            it.id == "reel_recycler" || it.id.startsWith("reel_player") || it.id.startsWith("reel_watch") ||
                it.id == "shorts_container"
        }
        return if (shortsPlayer) Screen.SINGLE else Screen.OK
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
