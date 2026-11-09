# Unscroll

Android app (Kotlin, Jetpack Compose, Material 3, minSdk 28) that stops doom scrolling in Instagram, YouTube Shorts and the Reddit home feed. Instagram: the home feed, Reels tab and Explore grid are covered by an accessibility overlay (`Screen.BLOCKED`), leaving the bottom tabs (DMs live there) and the Home/Explore top bar usable. A reel or post opened from a DM, search or a profile is `Screen.SINGLE`: it shows, and scrolling on to the next item is undone. YouTube Shorts (`SINGLE`) and the Reddit feed (`FEED`) only get the scroll-back.

Public repo: https://github.com/Aras1907/unscroll. Users install the APK from GitHub Releases.

## Layout

- `app/src/main/java/com/unscroll/app/`
  - `GuardService.kt`: the AccessibilityService. It classifies the foreground screen (debounced, and a screen change must show up in two evaluations in a row), remembers the position you landed on (`anchor`), and on a forward `TYPE_VIEW_SCROLLED` performs `ACTION_SCROLL_BACKWARD` on the scrolled view or its nearest scrollable parent. If the view can't scroll back while on SINGLE, it presses back once. On BLOCKED it shows the cover (`TYPE_ACCESSIBILITY_OVERLAY`, no permission needed) over `Detectors.cover()`, and takes it down when another app comes to the front.
  - `Screens.kt`: `Snapshot` (flattened node tree) and `Detectors` (per-app rules that return `Screen.OK / SINGLE / FEED / BLOCKED`). App updates break things here first.
  - `Prefs.kt`: `Target` (package) and `Guard` (one per switch in the UI) enums, SharedPreferences, block counter.
  - `MainActivity.kt`: the settings screen (status card, counters, "Instagram" and "Other apps" sections).
- `app/src/main/res/xml/guard_service.xml`: service config. It deliberately has no `packageNames` filter, because the service needs other apps' window events to remove the cover. Guarded packages live in `Target`.
- The app deliberately has **no INTERNET permission**. The README promises this, so keep it that way.

## Detection notes

- Instagram (verified on 454.0.0.54.77, Nov 2026): a tab counts as selected if it or a child is selected (Home marks a child, Reels marks the tab). `clips_tab` selected means BLOCKED. A full-screen `clips_viewer*` / `root_clips_layout` / `clips_video*` means SINGLE (DM, search and profile reels). `feed_tab` selected with no editable field means BLOCKED. `search_tab` selected plus grid tiles described "... at row N, column M" means BLOCKED (Explore). Search results hide the tab bar, so they never match. Any other list with `row_feed_*` rows means SINGLE.
- Instagram keeps the other tabs' pages in the tree with empty bounds (left > right). `Snapshot.of` skips empty-bounds subtrees; without that, Home looked like a full-screen reel.
- `action_bar_search_edit_text` on Explore reports editable to accessibility even though it's a button.
- YouTube: Shorts pivot tab selected (`content-desc="Shorts"`, selected) or a full-screen `reel_recycler` / `reel_player*` / `reel_watch*`.
- Reddit: bottom tab bar (Communities/Inbox/Chat/Create) plus vote buttons means FEED.
- Instagram Lite is deliberately unsupported (removed after 2.0.0): it exposes too little to block reliably.

## Environment quirks (VSCodium flatpak sandbox)

- The emulator's fake SIM is set to Peru (Claro, 71610) by editing `system-images/android-35/google_apis/x86_64/data/misc/modem_simulator/` (`.bak` copies of the originals sit next to them). The emulator copies these into the AVD on every boot, so editing the AVD copy does nothing.
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
- Real Instagram is installed on the emulator (via Aurora Store) and logged in to the owner's test account. Test against it with screenshots; use `uiautomator dump` only to inspect layout. Swipe slowly (`input swipe 400 1800 400 400 400`): a fast 150 ms swipe doesn't advance Instagram's reel pager even with the guard off, so it proves nothing. There's no Google account, so Reddit can't come from Play. If real Instagram is ever unavailable, build a throwaway app in the scratchpad with `applicationId = "com.instagram.android"` that mimics the view IDs (vertical ViewPager2 `clips_viewer_view_pager`, RecyclerView of `row_feed_*` rows, bottom tabs `feed_tab`/`search_tab`/`clips_tab`/`profile_tab`, `action_bar_inbox_button`, an EditText search box on the search tab). It shares Instagram's package name, so it can't be installed alongside the real app. Never commit it, and uninstall it afterwards.

## Releasing

- Release signing key: `~/.android/unscroll-release.jks`, configured through the git-ignored `keystore.properties`. Never commit either. Updates must be signed with this key or they won't install over the existing version.
- Bump `versionCode` / `versionName` in `app/build.gradle.kts`, build, then:

```sh
mkdir -p build/release && cp app/build/outputs/apk/release/app-release.apk build/release/Unscroll-vX.Y.Z.apk
flatpak-spawn --host gh release create vX.Y.Z "$PWD/build/release/Unscroll-vX.Y.Z.apk" --repo Aras1907/unscroll --title "Unscroll X.Y.Z" --notes "..."
```

- Keep the README and release notes in plain, human language and be honest about what hasn't been tested.
- Commit messages end with the Co-Authored-By line.
