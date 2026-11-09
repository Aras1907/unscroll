# Unscroll

I kept opening Instagram to answer one message and looking up forty minutes later, deep in Reels. Deleting the app didn't work because that's where my friends actually talk to me. So I built this.

Unscroll is a small Android app that keeps Instagram for messages and takes away the feeds. Whatever a friend sends you still opens. You just can't scroll on to the next thing.

**[Download the latest APK](https://github.com/Aras1907/unscroll/releases/latest)**

## What it does

### Instagram

- **Home feed, Reels tab and Explore grid are covered up.** A dark panel sits over them. The tab bar at the bottom stays usable, so you're one tap from your messages.
- **Reels and posts friends send you still work.** Tap one in a chat and it opens like it always did. You get the one you were sent. Swipe for the next one and it snaps back. The same goes for a reel you open from search or a profile.
- **Search, profiles and DMs work like normal.** Tap the search bar above the covered Explore page to search.
- **Instagram Lite isn't supported.** It draws its own screen and tells Android too little about it to block reliably.

### Other apps

- **YouTube Shorts:** you can watch a Short, but you can't swipe to the next. Regular videos, search and subscriptions are untouched.
- **Reddit feed:** you see the top of Home/Popular, but scrolling down gets pulled back up. Search, posts and your inbox work like normal.

Each app has its own switch. There's also a counter showing how much it blocked today. Watching that number climb is a little humbling.

## Setting it up

1. Download `Unscroll-v2.1.0.apk` from the [Releases page](https://github.com/Aras1907/unscroll/releases/latest) and install it. Your phone will ask you to allow installs from your browser or file manager first.
2. Open Unscroll and tap **Turn on the guard**. This opens Accessibility settings. Find **Unscroll guard** and switch it on.
3. **If the switch is greyed out:** Android 13+ blocks accessibility access for apps installed from an APK. Go to *Settings > Apps > Unscroll*, tap the ⋮ menu in the top corner, choose **Allow restricted settings**, then go back and try again.

That's it. Open Instagram and the home feed should be covered.

## Why it needs accessibility access

That's the only way an app can tell which screen another app is showing. Unscroll uses it to recognise Reels, Shorts and the feeds, to cover them up, and to scroll them back when you swipe. It gets notified about every app you open so it knows when you've left Instagram and can take the cover down, but it only looks inside Instagram, YouTube and Reddit. It has no internet permission at all, so nothing it sees can leave your phone, and it doesn't store any of it either. The only thing it saves is the block counter.

## Honest limitations

- It works by recognising the apps' screen layouts. When Instagram, YouTube or Reddit redesign something, a check can stop matching until it's updated. If something slips through, please open an issue and say which app version you're on.
- Tested on an Android 15 emulator with Instagram 454 (November 2026): the home feed, Reels tab and Explore are covered; a reel sent in a DM and a reel opened from search both stay put when you swipe. Instagram runs a lot of A/B tests, so your layout may differ.
- A very hard fling on a feed can show you a glimpse of the next post before it gets pulled back.
- It's a speed bump, not a prison. You can turn it off in settings whenever you want. The point is to make the mindless scroll a conscious choice.

## Building it yourself

```sh
./gradlew assembleRelease
```

Requires JDK 17 and the Android SDK (compileSdk 36). Without a `keystore.properties` file the release build is signed with your debug key.

Kotlin, Jetpack Compose, Material 3. Minimum Android 9.
