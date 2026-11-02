# Unscroll

I kept opening Instagram to answer one message and looking up forty minutes later, deep in Reels. Deleting the app didn't work because that's where my friends actually talk to me. So I built this.

Unscroll is a small Android app that turns off scrolling where it hurts. You can still open and watch whatever is on screen. You just can't swipe on to the next thing.

**[Download the latest APK](https://github.com/Aras1907/unscroll/releases/latest)**

## What it does

It doesn't block anything or throw up a lock screen. When you swipe, it quietly scrolls you back to where you were.

### Short videos

- **Instagram Reels:** the reel in front of you plays normally, whether you opened the Reels tab or tapped a reel somewhere else. Swipe up for the next one and it snaps back.
- **Reels your friends send you still work.** Tap one in a chat and it plays like it always did. You get the one you were sent, not the hundred after it.
- **Instagram Lite Reels:** same idea, on a best-effort basis. Lite draws most of its screen itself and tells Android very little about what's on it, so detection there is rougher.
- **YouTube Shorts:** you can watch a Short, but you can't swipe to the next. Regular videos, search and subscriptions are untouched.

### Feeds

This is its own section, so you can keep Reels locked and decide separately about the feed.

- **Instagram feed:** you see the top of your home feed (stories, the first post, the DM button), but scrolling down gets pulled back up.
- **Reddit feed:** same for Home/Popular. Search, posts and your inbox work like normal.

Every one of these has its own switch. There's also a counter showing how many swipes it undid today. Watching that number climb is a little humbling.

## Setting it up

1. Download `Unscroll-v1.0.0.apk` from the [Releases page](https://github.com/Aras1907/unscroll/releases/latest) and install it. Your phone will ask you to allow installs from your browser or file manager first.
2. Open Unscroll and tap **Turn on the guard**. This opens Accessibility settings. Find **Unscroll guard** and switch it on.
3. **If the switch is greyed out:** Android 13+ blocks accessibility access for apps installed from an APK. Go to *Settings > Apps > Unscroll*, tap the ⋮ menu in the top corner, choose **Allow restricted settings**, then go back and try again.

That's it. Open Reels and try swiping to the next one.

## Why it needs accessibility access

That's the only way an app can tell which screen another app is showing. Unscroll uses it to recognise Reels, Shorts and the feeds, and to scroll them back when you swipe. It has no internet permission at all, so nothing it sees can leave your phone, and it doesn't store any of it either. The only thing it saves is the block counter.

## Honest limitations

- It works by recognising the apps' screen layouts. When Instagram, YouTube or Reddit redesign something, a check can stop matching until it's updated. If something slips through, please open an issue and say which app version you're on.
- The snap-back was tested on an Android 15 emulator against a vertical pager and a feed list built the same way these apps build theirs. It hasn't been tested on every version of the real apps or every account (Instagram runs a lot of A/B tests).
- A very hard fling on a feed can show you a glimpse of the next post before it gets pulled back.
- It's a speed bump, not a prison. You can turn it off in settings whenever you want. The point is to make the mindless scroll a conscious choice.

## Building it yourself

```sh
./gradlew assembleRelease
```

Requires JDK 17 and the Android SDK (compileSdk 36). Without a `keystore.properties` file the release build is signed with your debug key.

Kotlin, Jetpack Compose, Material 3. Minimum Android 9.
