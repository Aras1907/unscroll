# Unscroll

Android app (Kotlin, Jetpack Compose, Material 3, minSdk 28) that disables scrolling in Instagram Reels, Instagram Lite Reels, YouTube Shorts and, as a separate section, the Instagram and Reddit home feeds. It never blocks a screen. When the user swipes forward, it scrolls back. Reels opened from DMs play normally; only the swipe to the next one is undone.

Public repo: https://github.com/Aras1907/unscroll. Users install the APK from GitHub Releases.

## Layout

- `app/src/main/java/com/unscroll/app/`
  - `GuardService.kt`: the AccessibilityService. It classifies the foreground screen (debounced, and a screen change must show up in two evaluations in a row), remembers the position you landed on (`anchor`), and on a forward `TYPE_VIEW_SCROLLED` performs `ACTION_SCROLL_BACKWARD` on the scrolled view or its nearest scrollable parent. If the view can't scroll back while on SHORTS, it presses back once.
  - `Screens.kt`: `Snapshot` (flattened node tree) and `Detectors` (per-app rules that return `Screen.OK / SHORTS / FEED`). App updates break things here first.
  - `Prefs.kt`: `Target` (package) and `Guard` (one per switch in the UI) enums, SharedPreferences, block counter.
  - `MainActivity.kt`: the settings screen (status card, counters, "Short videos" and "Feeds" sections).
- `app/src/main/res/xml/guard_service.xml`: service config. `packageNames` must list every guarded package.
- The app deliberately has **no INTERNET permission**. The README promises this, so keep it that way.

## Detection notes

- Instagram: `clips_viewer*` / `root_clips_layout` / `clips_video*` covering >60% of the screen, or `clips_tab` selected, means SHORTS. `feed_tab` selected means FEED. Explore is deliberately not a feed, because search results live under the same tab.
- YouTube: Shorts pivot tab selected (`content-desc="Shorts"`, selected) or a full-screen `reel_recycler` / `reel_player*` / `reel_watch*`.
- Reddit: bottom tab bar (Communities/Inbox/Chat/Create) plus vote buttons means FEED.
- Instagram Lite draws its own UI and exposes little. Its rules are label-based and are being reworked against a real logged-in Lite install.

## Environment quirks (VSCodium flatpak sandbox)

- adb: `/home/aras/android-sdk/platform-tools/adb` (not on PATH). Build tools: `/home/aras/android-sdk/build-tools/35.0.0`.
- `gh` exists only on the host. Run it as `flatpak-spawn --host gh ...`. The host can't see sandbox `/tmp` paths, so copy files under the project folder (for example `build/release/`) before passing them to `gh`.
- `git push` uses SSH and works directly.

## Build, install, test

```sh
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell "settings put secure enabled_accessibility_services com.unscroll.app/com.unscroll.app.GuardService; settings put secure accessibility_enabled 1"
```

- Reinstalling can unbind the service, so re-run the `settings put` above after every install.
- **Don't use `uiautomator dump` while testing behaviour.** It takes over accessibility and briefly unbinds Unscroll, so the next swipe goes through and the test looks like a failure. Use it only to inspect a layout, and check behaviour from screenshots (`adb exec-out screencap`) or logcat.
- There's no Google account on the emulator, so Instagram and Reddit can't come from Play. To test the scroll-back mechanics without them, build a throwaway app in the scratchpad with `applicationId = "com.instagram.android"` that mimics the view IDs (vertical ViewPager2 `clips_viewer_view_pager`, RecyclerView feed with `feed_tab` selected). Never commit it, and uninstall it afterwards.

## Releasing

- Release signing key: `~/.android/unscroll-release.jks`, configured through the git-ignored `keystore.properties`. Never commit either. Updates must be signed with this key or they won't install over the existing version.
- Bump `versionCode` / `versionName` in `app/build.gradle.kts`, build, then:

```sh
mkdir -p build/release && cp app/build/outputs/apk/release/app-release.apk build/release/Unscroll-vX.Y.Z.apk
flatpak-spawn --host gh release create vX.Y.Z "$PWD/build/release/Unscroll-vX.Y.Z.apk" --repo Aras1907/unscroll --title "Unscroll X.Y.Z" --notes "..."
```

- Keep the README and release notes in plain, human language and be honest about what hasn't been tested.
- Commit messages end with the Co-Authored-By line.
