# Unscroll

I kept opening Instagram to answer one message and looking up forty minutes later, deep in Reels. Deleting the app didn't work because that's where my friends actually talk to me. So I built this.

Unscroll is a small Android app that keeps Instagram for messages and takes away the feeds. Whatever a friend sends you still opens. You just can't scroll on to the next thing.

**[Download the latest APK](https://github.com/Aras1907/unscroll/releases/latest)**

## What it does

### Instagram

Instagram becomes a messaging app.

- **Home feed, Reels tab and Explore send you straight to your DMs.** There's no dark panel covering them any more. The moment one of them opens, Unscroll taps over to your messages. (If it can't find the messages button, it falls back to covering the feed like before.)
- **Only your DMs scroll.** The inbox and chats work like normal. Everywhere else, like profiles or a grid of posts, you see what's on screen, and scrolling down snaps back.
- **Reels and posts friends send you still open.** You get the one you were sent. Swipe for the next one and it snaps back.
- **Search and comments still scroll.** Any screen with a text box is left alone, so a chat can never get locked by mistake.
- **Instagram Lite isn't supported.** It tells Android too little about its screens to block reliably.

**Why the feed still loads in the background:** Unscroll can't stop Instagram from downloading the feed. It would need root, or an internet connection of its own to filter Instagram's traffic, and even then the feed and your messages come from the same encrypted servers, so it couldn't block one without breaking the other. What it can do is make sure you never land on the feed.

### Other apps

- **YouTube Shorts:** you can watch a Short, but you can't swipe to the next. Regular videos, search and subscriptions are untouched.
- **Reddit feed:** you see the top of Home/Popular, but scrolling down gets pulled back up. Search, posts and your inbox work like normal.

Each app has its own switch. There's also a counter showing how much it blocked today. Watching that number climb is a little humbling.

## Setting it up

1. Download `Unscroll-v3.0.0.apk` from the [Releases page](https://github.com/Aras1907/unscroll/releases/latest) and install it. Your phone will ask you to allow installs from your browser or file manager first.
2. Open Unscroll and tap **Turn on the guard**. This opens Accessibility settings. Find **Unscroll guard** and switch it on.
3. **If the switch is greyed out:** Android 13+ blocks accessibility access for apps installed from an APK. Go to *Settings > Apps > Unscroll*, tap the ⋮ menu in the top corner, choose **Allow restricted settings**, then go back and try again.

That's it. Open Instagram and it should drop you into your DMs.

## Why it needs accessibility access

That's the only way an app can tell which screen another app is showing. Unscroll uses it to recognise Reels, Shorts and the feeds, to tap you over to your messages, and to scroll things back when you swipe. It gets notified about every app you open so it knows when you've left Instagram and can take the cover down, but it only looks inside Instagram, YouTube and Reddit. It has no internet permission at all, so nothing it sees can leave your phone, and it doesn't store any of it either. The only thing it saves is the block counter.

## Honest limitations

- It works by recognising the apps' screen layouts. When Instagram, YouTube or Reddit redesign something, a check can stop matching until it's updated. If something slips through, please open an issue and say which app version you're on.
- **3.0.0 hasn't been tested on a phone yet.** The jump to your DMs and the DM-only scrolling are new, and some of Instagram's button names were guessed. If it doesn't drop you into your messages, you'll see the old dark cover instead. Please open an issue if anything goes wrong.
- 2.1.0 was tested on an Android 15 emulator with Instagram 454 (November 2026). Instagram runs a lot of A/B tests, so your layout may differ.
- A very hard fling on a feed can show you a glimpse of the next post before it gets pulled back.
- It's a speed bump, not a prison. You can turn it off in settings whenever you want. The point is to make the mindless scroll a conscious choice.

## Building it yourself

```sh
./gradlew assembleRelease
```

Requires JDK 17 and the Android SDK (compileSdk 36). Without a `keystore.properties` file the release build is signed with your debug key.

Kotlin, Jetpack Compose, Material 3. Minimum Android 9.
