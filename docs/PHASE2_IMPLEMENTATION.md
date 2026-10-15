# Smart TV Remote — Phase 2

Native Android app, package `com.indianservers.samsungsmarttvremote`. Phase 2 extends the existing Kotlin / Compose / Material 3 / MVVM application and preserves its discovery, pairing, TLS policy, encrypted credentials, remote keys, and reconnection engine. No Phase 3 features, cloud backend, accounts, analytics, or advertisements are included.

## Build and install

Use Android Studio's bundled JDK and the SDK path in your untracked `local.properties`. Minimum API 26, compile/target API 37; version `2.0-phase2` (code 2). Existing validated Gradle/dependency versions were preserved.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. A release signing configuration is not included.

Run `gradlew.bat :app:connectedDebugAndroidTest` with the intended Android device attached. To target only one emulator when several devices are attached:

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w com.indianservers.samsungsmarttvremote.test/androidx.test.runner.AndroidJUnitRunner
```

The storage and advanced UI tests inject isolated DataStore files rather than clearing the application's pairing or customization data. The UI test uses a local fake WebSocket TV; its name and installed application are test fixtures, not production listings. Test screenshots are written to the test app's external `files/qa` directory.

## What's implemented

| Area | Behavior |
|---|---|
| Phase 1 | SSDP plus manual IPv4 validation, TV authorization, encrypted token reuse, foreground/Wi-Fi recovery, bounded reconnection and usable remote controls remain in place. |
| Main interface | Remote, Touchpad, Keyboard, Apps tabs; secondary Sources, Favorites, More panels; shared snow-white/cobalt theme; vector controls, accessible descriptions, press feedback, animated tab transitions and connection indicator. |
| Touchpad | Thresholded swipes issue one directional key. Tap and double-tap issue a single Select; hold/release maps to Back. Ambiguous, short, canceled and multi-finger gestures produce no speculative direction commands. Dotted trackpad, animated finger/trail feedback, adjustable per-TV sensitivity, relative volume buttons, Back/Home/Mute and screen-reader actions. |
| Keyboard | UTF-8/Base64 SendInputString adapter with broadcast preamble and SendInputEnd, 1,024-byte limit, malformed-Unicode checks and one ordered text batch through the shared transport. Send requires a live authorized connection and a TV remote-IME focus event. Clear/backspace on the phone are explicitly local. |
| Apps | ed.installedApp.get parser with limits, deduplication and known app-type mapping; per-TV metadata cache; explicit unverified custom ID/action configuration; two-column app cards and favorite toggles. No hardcoded installed apps or guessed model IDs. |
| App launch | Dedicated TVAppLauncher sends ed.apps.launch over the same authorized socket. Unknown capability is labeled Try launch. A TV 200 response is recorded as a request acknowledgment, not proof of a visibly opened app. Unknown app types cannot launch. |
| Sources | Candidate TV/HDMI cards remain disabled because no direct-port interface is verified. Source-menu key and live D-pad navigation provide the fallback, without a universal scripted key sequence. |
| More | Numeric keys 0–9, Enter, previous channel, channel list, Play/Pause/Stop/Rewind/Fast-forward/Back/Home. Media and channel keys remain model-dependent candidates. |
| Repetition | Volume, channel and directional holds use bounded Click repeats. The queue checks a live press guard before each repeat; cancellation/disposal invalidates queued repeats. No unverified Press/Release protocol assumption is introduced. |
| Favorites | Per-TV app favorites and pinned remote actions, first-three quick access, drag/drop reorder with finger-follow/elevation feedback, accessible reorder buttons, unpin/removal, 30-item bound, missing-app fallback. |
| Customization | Per-TV DataStore schema for favorites, metadata, sensitivity, default tab and hidden numeric/media/channel groups. Haptic preference continues to persist. |
| Capabilities | Central TVCapabilityManager distinguishes Unknown, Supported and Unsupported with evidence. TV events establish listing, text-focus and launch-ack evidence. Connection loss/switching resets session evidence; previously installed app metadata becomes cached/unverified. |

## Capability boundaries

- **Pointer mode remains gated.** No actual TV/pointer interface has been validated. The UI explains why directional mode remains selected. There is no guessed mouse-message transmission.
- **Direct HDMI selection remains gated.** Candidate HDMI cards do not assert that those physical ports exist. KEY_SOURCE opens the menu; the user navigates their TV's actual layout.
- **TV backspace remains gated.** There is no verified remote deletion mechanism for the selected TV. The enabled Backspace locally control edits the phone draft only; use the TV's own keyboard for remote deletion.
- Remote IME start means the TV advertised keyboard focus; it does not prove that a specific application will accept inserted text. Loss of focus or a nearby text-command error disables sending until another focus event arrives. Timeout or an uncorrelated protocol error remains Unknown, not fabricated proof of Unsupported.
- The app does not infer absolute volume, current input, playback state or installed apps from successful socket sends. Relative volume buttons and distinct Play/Pause keys avoid those assumptions. Rewind/fast-forward are seeking keys, not guaranteed previous/next track controls.
- Installed app types 2, 1 and 4 map to the researched DEEP_LINK or NATIVE_LAUNCH action types. Other types remain visible as unverified and cannot generate a launch request. Cached/custom shortcuts are clearly labeled and only offer Try launch.
- Every capability begins Unknown. Core remote keys are explicitly model-dependent candidates inherited from Phase 1, not capability assertions. The TV's generic ms.error cannot reliably identify a command; the inherited conservative policy disables the most recent key for that session and reports the uncertainty.
- No physical Samsung TV was available for this work. Hardware behavior, actual pointer support, real keyboard insertion, launch behavior and source selection must be accepted separately on named model/firmware combinations.

## Architecture and changed files

The existing ConnectionManager owns the only WebSocket. Its bounded synchronized queue now carries key commands and atomic advanced-message batches. It checks connection generation, current authorization, request cancellation and repeat guards, and exposes typed TV events. Features never open competing sockets. Queued text is in memory only and is not logged or persisted. Draft text is cleared on tab disposal/backgrounding; its state is deliberately not saveable.

New source files (under `app/src/main/java/com/indianservers/samsungsmarttvremote`):

- `domain/AdvancedModels.kt`: capability evidence, application/source/favorite/preference models and submission/event types.
- `domain/GestureInterpreter.kt`: threshold, axis, sensitivity and gesture-action decisions.
- `domain/AdvancedController.kt`: focus gating, request serialization, timeout/rejection handling, app listing and TVAppLauncher.
- `data/protocol/AdvancedProtocols.kt`: researched text/application payloads and strict response parsing; pointer availability gate.
- `data/storage/RemotePreferencesCodec.kt`: versioned local schema, bounds, cache origins and favorites operations.
- `presentation/Phase2Screens.kt`: four-tab remote shell, source/advanced panels and customization/capability settings.
- `presentation/TouchpadKeyboard.kt`: gesture surface, feedback/accessibility and sensitive draft UI.
- `presentation/AppsFavorites.kt`: app cards/custom identifiers, favorite launches and reorder UI.

Updated: `MainActivity.kt`, `RemoteGlyph.kt`, `domain/Protocol.kt`, `data/network/ConnectionManager.kt`, `data/storage/LocalStore.kt`, `presentation/RemoteViewModel.kt`, `ui/theme/Theme.kt`, manifest and app Gradle configuration. These extend the existing structure instead of recreating the project.

Tests: new `src/test/.../AdvancedTest.kt`, new Android touchpad/end-to-end UI tests, and updates to the Phase 1 protocol/storage/lifecycle tests. `docs/PHASE1_IMPLEMENTATION.md` preserves the prior Phase 1 record; its historical testing limitations are superseded where the Phase 2 report records new evidence.

## Security and network assumptions

API 37 local-network access is requested at runtime. Discovery validates private numeric IPv4 candidates against Samsung's API; SSDP announcements alone are not connection proof. Some firmware blocks that device-information endpoint, so manual entry cannot guarantee compatibility. Guest Wi-Fi isolation, blocked multicast and VPN routing can interfere. No IPv6, legacy-model adapter, Wake-on-LAN or IR support is claimed.

Normal TLS verification remains the default. The existing explicit, selected-private-IP certificate exception uses a separate per-connection client, checks certificate dates and pins the first fingerprint across retry attempts. Its first certificate is not independently authenticated, so the UI explains the trusted-LAN/IP-check requirement. Trust exceptions are not persisted across process restarts; no global TLS verification setting is disabled. Redirects remain disabled. Explicit non-token port-8001 devices carry an unencrypted-connection notice; TLS failures never silently downgrade.

Pairing credentials remain AES-GCM encrypted with an Android Keystore key. Backup and device transfer are excluded. Customizations and metadata stay local. Unknown feature support is not saved as verified model support.

## Verify with a physical Samsung TV

1. Install the APK on an Android phone and connect it and a powered-on Samsung TV to the same private Wi-Fi. Grant API 37 local-network permission where applicable; also exercise denial and recovery from Android settings.
2. Rescan or enter the TV's private IP, verify name/model/address, connect, and approve the authorization prompt using the TV's existing remote. Test denial, timeout, revoked access and reconnect/token reuse.
3. Confirm every Phase 1 key, numeric key and media action in a suitable TV screen. Hold/release and cancel repeatable controls. Record unsupported keys without assuming another firmware behaves identically. Test power off last; powering on still requires the physical remote.
4. On Touchpad, verify each axis, tap, double-tap, hold/release, canceled gesture and sensitivity setting. Check one swipe makes one intended selection step.
5. Open an actual TV search/text field. Confirm IME focus availability, send a short Unicode sample, and inspect the television for actual insertion. Test focus loss, rejection and oversized input. Confirm phone text is cleared on backgrounding and not restored after recreation.
6. Request the real app list. Check each returned name/ID/type, try launch, and visually verify the TV app opens. Compare the acknowledgment with actual screen behavior. Test a custom invalid/uninstalled ID and listing timeout without claiming successful launch.
7. Open Sources and navigate the real source menu. Verify that unverified direct input buttons remain disabled and no scripted HDMI sequence runs.
8. Star apps, pin actions, drag/reorder, remove favorites, hide groups and change the default tab. Relaunch and switch TVs; verify customization isolation and cached labels. Exercise a favorite whose app is no longer available.
9. Disconnect Wi-Fi and the TV during a text/launch action. Reconnect, rotate/background/resume, switch TVs and forget pairing. Confirm stale commands and capability evidence do not leak between sessions.
10. Check small-screen/large-font layouts, screen-reader actions, reduced-motion settings and actual model-specific capabilities before release. Record exact TV model, firmware, port and observed result. Fake-TV/emulator tests are not physical-TV compatibility evidence.

Protocol references: [SamsungTVWS remote payload implementation](https://github.com/xchwarze/samsung-tv-ws-api/blob/master/samsungtvws/remote.py), [remote event and installed-app parsing](https://github.com/xchwarze/samsung-tv-ws-api/blob/master/samsungtvws/event.py), [Android local-network permission](https://developer.android.com/privacy-and-security/local-network-permission). Payloads were implemented from protocol facts rather than copied library implementation code.

## Verification report

See [Phase 2 delivery and verification report](docs/PHASE2_REPORT.md) for final build/test results, feature evidence, limitations and files changed.

