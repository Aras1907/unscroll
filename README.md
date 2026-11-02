# Unscroll

I kept opening Instagram to answer one message and looking up forty minutes later, deep in Reels. Deleting the app didn't work because that's where my friends actually talk to me. So I built this.

Unscroll is a small Android app that puts a wall in front of the endless parts of Instagram, YouTube and Reddit and leaves the useful parts alone.

**[Download the latest APK](https://github.com/Aras1907/unscroll/releases/latest)**

## What it does

- **Instagram:** the Reels tab is walled off. The home feed and Explore are fine to glance at, but the moment you start scrolling them the wall comes up, with a button that takes you straight to your DMs.
- **Reels your friends send you still work.** Tap a reel in a chat and it plays normally. Swipe up for the next one and Unscroll sends you back to the conversation. You get the one you were sent, not the hundred after it.
- **Instagram Lite:** same idea, on a best-effort basis. Lite draws most of its screen itself and tells Android very little about what's on it, so detection there is rougher.
- **YouTube:** only Shorts are blocked, both the Shorts tab and the Shorts player. Regular videos, search and subscriptions are untouched.
- **Reddit:** scrolling the Home/Popular feed is blocked. Search, subreddits you look up on purpose, posts and your inbox still work.

Every app can be switched on or off, and the "reels from DMs" exception has its own toggle. There's a small counter on the home screen showing how many times it stopped you today. Seeing that number go up is a little humbling.

## Setting it up

1. Download `Unscroll-v1.0.0.apk` from the [Releases page](https://github.com/Aras1907/unscroll/releases/latest) and install it. Your phone will ask you to allow installs from your browser or file manager first.
2. Open Unscroll and tap **Turn on the guard**. This opens Accessibility settings. Find **Unscroll guard** and switch it on.
3. **If the switch is greyed out:** Android 13+ blocks accessibility access for apps installed from an APK. Go to *Settings > Apps > Unscroll*, tap the ⋮ menu in the top corner, choose **Allow restricted settings**, then go back and try again.

That's it. Open Instagram and try to scroll.

## Why it needs accessibility access

That's the only way an app can tell which screen another app is showing. Unscroll uses it to recognise the Reels tab, the Shorts player and the feeds, and to press "back" for you when you swipe past a DM reel. It has no internet permission at all, so nothing it sees can leave your phone, and it doesn't store any of it either. The only thing it saves is the block counter.

## Honest limitations

- It works by recognising the apps' screen layouts. When Instagram, YouTube or Reddit redesign something, a check can stop matching until it's updated. If something slips through, please open an issue and say which app version you're on.
- I tested the YouTube Shorts blocking on an Android 15 emulator. The Instagram and Reddit checks are built on their current layouts but haven't been tested against every version or every account (Instagram runs a lot of A/B tests).
- It's a speed bump, not a prison. You can turn it off in settings whenever you want. The point is to make the mindless scroll a conscious choice.

## Building it yourself

```sh
./gradlew assembleRelease
```

Requires JDK 17 and the Android SDK (compileSdk 36). Without a `keystore.properties` file the release build is signed with your debug key.

Kotlin, Jetpack Compose, Material 3. Minimum Android 9.
