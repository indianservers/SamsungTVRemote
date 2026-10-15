# Smart TV Remote — Phase 1

Native Kotlin / Jetpack Compose Android app. Package and application ID: `com.indianservers.samsungsmarttvremote`. Minimum Android 8 (API 26); compile/target API 37. Local operation, no account, ads, backend, analytics, or Phase 2 features.

## Build and install

Open this folder in Android Studio, select its bundled JDK, and sync Gradle. Keep `local.properties` pointed at your Android SDK. The project preserves the supplied AGP 9.3.3, Gradle 9.5.0, Kotlin Compose plugin 2.2.10, and Compose BOM. OkHttp 4.12.0 and DataStore 1.1.7 are deliberately pinned to versions verified by the build, rather than automatically upgrading dependencies.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed development build. Distribution signing is not configured.

For an attached device or a running emulator, run `gradlew.bat :app:connectedDebugAndroidTest` to exercise Android-backed encrypted storage and rotation/recreation tests. Use a clean app data state for the discovery-screen lifecycle test.

## Implementation and source map

- `MainActivity.kt`: Material 3 discovery, remote, and connection settings screens; state preserved through recreation; lifecycle-aware state collection; runtime local-network permission; accessible control names; responsive, scrollable layout; cobalt D-pad, press animation, ripples, optional persisted haptics, and press-and-hold volume/channel controls.
- `RemoteGlyph.kt` and `res/drawable/ic_remote.xml`: vector-drawn remote controls and app/splash identity. Android provides the startup splash without an artificial delay.
- `presentation/RemoteViewModel.kt`: MVVM orchestration, discovery results, Wi-Fi callbacks, foreground/background cleanup, saved-TV restoration, and address-change recovery when a stable device ID is rediscovered.
- `domain/Protocol.kt`: TV/status models, protocol abstraction, centralized key mappings, JSON parsing/encoding, private-address validation, deduplication, and retry policy.
- `data/discovery/TvDiscovery.kt`: SSDP `ssdp:all` and MediaRenderer searches; multicast lock; cancellation and socket cleanup; private-address candidate filtering; validation against Samsung's `/api/v2/` device information; bounded response size. Manual IP uses the same validation. SSDP advertisements alone are never treated as proof of compatibility.
- `data/network/ConnectionManager.kt`: actual OkHttp WebSocket pairing and key transmission, authorization timeout, token refresh/removal on rejection, bounded command queue, ordered 120 ms command spacing, ping liveness, four exponential-backoff retries, per-session/per-attempt stale-callback guards, socket ownership during TV switching, and honest error/status reporting.
- `data/storage/LocalStore.kt`: DataStore preferences for TV identity/IP/model/port and haptics. Pairing tokens are encrypted with AES-GCM using an Android Keystore key; no plaintext token persistence. Backup and device transfer are excluded. PairingStore separates persistence from connection logic for fake-server tests.
- `src/test/.../ProtocolTest.kt`: protocol and fake-TV server tests.
- `src/androidTest/.../RemoteInstrumentedTest.kt`: Android storage and lifecycle tests.

## Samsung protocol and security assumptions

The app targets Samsung TVs exposing the modern Tizen remote API. It validates the `device.type` or manufacturer as Samsung, prefers `wss://IP:8002/api/v2/channels/samsung.remote.control`, URL-encodes the Base64 app name and supplied token, and enables controls only after `ms.channel.connect`. It handles tokens in `data.token` and the matching client's attributes. It never approves or bypasses the TV authorization dialog.

Port 8001 is selected only when the device information explicitly reports `TokenAuthSupport=false`; the discovery card explains that this connection is unencrypted. That field is a protocol hint, not a guarantee that every firmware supports the endpoint. No automatic insecure fallback from a rejected TLS connection is performed.

Normal platform TLS verification is used first. A certificate failure exposes an explicit per-app-session local-device trust option, with its risk explained before connecting. The exception is restricted to a separately constructed client for the selected private numeric IP, checks certificate validity dates, pins the first certificate fingerprint across retry attempts, and never changes global TLS defaults. The first accepted certificate is not independently authenticated; use this only on a trusted LAN after checking the TV's IP. Trust is not persisted across process restarts. HTTP/WebSocket redirects are disabled. Lint's custom-trust-manager warning remains visible because the deliberate exception merits review.

Only private IPv4 addresses are supported in this phase. No hostname, IPv6, legacy encrypted Samsung adapters, Wake-on-LAN, IR, casting, keyboard, streaming, or app launch feature is claimed. Discovery uses SSDP because no single Samsung-specific mDNS service is consistently advertised; there is no NSD service assumption. Access-point client isolation, guest Wi-Fi, VPN routing, blocked multicast, and firmware disabling port 8001 device information can prevent discovery/validation. Manual IP cannot overcome a TV that does not expose the required device API.

Every available remote control sends a real candidate Samsung key. Firmware support varies; there is no universal key-capability endpoint. A TV protocol error conservatively disables the most recently sent key for the current selected-TV session. Because error messages do not correlate commands reliably, confirm key behavior on hardware. These controls do not prove execution: WebSocket enqueue success only proves submission to the transport. Power is labeled off because a socket cannot reliably turn on a sleeping or unplugged TV. Play and Pause use separate commands so there is no invented playback-state toggle.

## Verification status (15 October 2026)

- Debug app and Android test APK compile successfully.
- 14 JVM tests passed: nine protocol/discovery/retry tests, four connection tests against a local fake WebSocket TV, and the retained template smoke test. Coverage includes all key message types, optional/nested token parsing, rejection and stale-token clearing, ordered/throttled transmission, reconnect/token reuse, explicit disconnected-send failure, malformed responses, deduplication, and bounded backoff calculation.
- Android lint completed without errors. Warnings include available dependency updates, unused template resources, version-catalog suggestions, and the explicitly isolated TV certificate strategy.
- Android storage/Keystore, application launch, visual layout, rotation, permission behavior, Wi-Fi interruption, and real-TV operation have **not** been executed on a device. The installed emulator failed its disk-space check and no Android device was attached. Instrumentation test sources compile; this is not a claim that they ran.
- No physical Samsung TV has been tested. Real-device acceptance is pending. Automated fake-server results do not establish a supported model list or production readiness.

## Physical TV acceptance procedure

1. Install the debug APK on an Android phone; connect phone and powered-on Samsung TV to the same private Wi-Fi. Disable guest/client isolation. On API 37+, grant local-network permission; deny once to check the denied state, then grant from Android app settings if required.
2. Rescan. Check TV name/model/IP against the TV's network settings. If absent, enter that private IPv4 address. Confirm multiple TVs appear without duplicate rows.
3. Connect and approve **Smart TV Remote** using the television's existing remote. Verify controls remain disabled until authorization. If TLS validation fails, inspect the TV IP and review the explicit certificate option. Separately test a rejected prompt and a prompt left unanswered.
4. Verify each D-pad direction, OK, Home, Back, Menu, Source, Mute, Volume, Channel, Play, and Pause on a suitable TV screen. Hold volume/channel to check repeat behavior. Record model, firmware, port, successful and unsupported keys. Do not infer capabilities from other models.
5. Disconnect/reconnect, rotate the phone, background/resume the app, and force-stop/relaunch it. Confirm the saved TV and token restore, haptics persist, and stale connections never remain enabled.
6. Disable/re-enable Wi-Fi and power-cycle the TV. Verify reconnect states and the bounded retry limit. Change the TV's DHCP address, then rescan and verify stable-ID address recovery. Revoke access on the TV and verify token clearing plus renewed authorization.
7. Forget the paired TV and confirm restart does not reconnect to it. Test power off last; powering back on requires the physical remote in Phase 1.
8. Run the connected Android test suite and complete visual/accessibility checks on a small phone, large font setting, landscape orientation, and an API 37 phone before release.

References: [Android local-network permission](https://developer.android.com/privacy-and-security/local-network-permission), [Samsung WebSocket reference implementation](https://github.com/xchwarze/samsung-tv-ws-api/blob/master/samsungtvws/connection.py). The remote WebSocket interface is firmware-dependent, not a universal Samsung public compatibility guarantee.
