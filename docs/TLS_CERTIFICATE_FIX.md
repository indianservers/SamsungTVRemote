# Samsung TV TLS certificate fix — 3.0.1

Date: 15 October 2026. Package: `com.indianservers.samsungsmarttvremote`; version 3.0.1, code 4.

## Confirmed root cause

The actual endpoint at `192.168.100.224:8002` was reachable. A certificate-only Windows TLS probe captured the certificate and explicitly rejected the handshake, sending no WebSocket request or pairing credentials. Validation reported **UntrustedRoot** and **RemoteCertificateNameMismatch**. The certificate dates are 21 September 2016 through 21 September 2036, so expiry is not the cause. Sanitized evidence: `qa/tv-tls-probe.json`.

The Android app running on the API 37.1 emulator also reached the real Samsung Q60AA 55 TV / QA55Q60AAKLXL and produced the new certificate review UI with `UntrustedChain`. Its fingerprint matched the independent probe:

```text
04:26:AC:7F:01:AF:E6:49:4C:5C:6C:00:80:70:CB:1D:AA:A3:C7:AB:F8:FC:F0:73:8F:D5:2C:33:33:62:EA:8A
```

Genuine screenshot: `qa/tv-certificate-review.png`. The review was canceled; this work did not approve the actual TV certificate or complete its physical pairing prompt.

The old code grouped every SSL handshake/peer exception under a generic certificate error. Its opt-in used an undisplayed, session-only first-observed pin, so it provided neither explicit identity approval nor persistent changed-certificate detection.

## Implemented security and pairing behavior

- Every secure connection attempts system chain verification first. Unapproved failures remain failures. The trust manager records the presented leaf for review without accepting it or sending an HTTP/WebSocket request.
- The dialog shows TV name/IP, full SHA-256 DER leaf fingerprint, reason, explicit Trust Certificate & Connect, and Cancel. Changed certificates show a security warning and require fresh approval. Expired/not-yet-valid certificates cannot be approved.
- Approved identity is encrypted with Android Keystore AES-GCM and bound to TV ID, IP and port 8002. A dedicated client accepts a fallback only for that exact leaf and valid dates. The hostname exception also requires that exact endpoint and leaf. There is no globally permissive trust manager or global hostname override.
- A changed certificate fails even if a system CA would otherwise trust it. An endpoint change requires reset and a new review. Missing trust and unreadable/corrupt trust are distinct: corrupt approval blocks connection until reset.
- Settings → Reset TV Certificate Trust disconnects and removes only certificate approval. Pairing token remains separate. Forgetting a TV removes its approval; resetting all local data removes all approvals.
- TLS success advances only to AwaitingAuthorization. The correctly Base64-encoded client name remains in the Samsung WebSocket URL. Only `ms.channel.connect` enables controls; a supplied token is encrypted and reused only on secure port 8002. Samsung rejection/time-out disables controls and clears the rejected pairing token.
- No automatic fallback to port 8001 was added. Existing explicitly selected legacy endpoints remain labeled unencrypted; no token is sent on them. This fix does not assume Q60AA supports an unencrypted remote socket.
- Sanitized diagnostics distinguish chain trust, hostname mismatch, expired/not-yet-valid certificates, changed identity, wrong endpoint, other TLS handshake failure, and unrelated HTTP/WebSocket failures. Raw exception text, credentials and payloads are not logged/exported. A TLS negotiation failure without a certificate is not mislabeled as an approvable certificate issue.

First approval is trust-on-first-use: a fingerprint identifies the observed certificate but does not independently prove TV ownership. Confirm the TV IP on its own network settings and use a known home network. No fingerprint is hardcoded in application source.

## Files changed

- New `app/src/main/java/com/indianservers/samsungsmarttvremote/data/network/TvCertificateTrust.kt`: exact identity matching, system verification, scoped fallback/hostname policy, sanitized failure classification and review model.
- `data/network/ConnectionManager.kt`: integrated review, approval, reset, persistent pin loading and failure handling while preserving Samsung authorization and ordered command transport.
- `data/storage/LocalStore.kt`: encrypted certificate persistence, strict corruption handling and per-TV cleanup; PairingStore extensions.
- `MainActivity.kt`, `presentation/ProductionScreens.kt`: guided certificate UI, changed-certificate warning and confirmed reset action; displayed app version.
- `app/build.gradle.kts`: version 3.0.1/code 4 and test-only OkHttp TLS fixtures. No new runtime dependency.
- New `app/src/test/java/com/indianservers/samsungsmarttvremote/TvCertificateTest.kt`; extended `app/src/androidTest/java/com/indianservers/samsungsmarttvremote/ProductionInstrumentedTest.kt`.
- Updated README, security review and artifact manifest; this report and sanitized probe/screenshot evidence.

## Build and automated verification

Final combined Gradle build succeeded: `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `lintDebug`, `bundleRelease`.

**52 JVM tests passed**, zero failures/errors. Ten added tests cover normal trusted chain, unknown self-signed rejection, exact approval across new clients, changed certificate despite standard CA trust, wrong TV/IP, expiry/future dates, unrelated TLS/WebSocket failures, reset, scoped hostname exceptions, canceled approval, and a real TLS/WebSocket fixture exercising Samsung authorization, token save/reuse, rejection and reset. The integration fixture listens on an available private local interface at port 8002; it does not contact the physical TV.

**16 Android tests passed** on API 37.1 emulator. Added checks cover full fingerprint/explicit approval UI, changed warning/cancellation, no expired-certificate approval, Keystore-encrypted persistence, independent reset/deletion and corrupt approval rejection. The earlier storage/lifecycle/advanced UI/touchpad/widget regression tests also pass. Runner evidence: `qa/tls-instrumentation.txt`.

Lint: **zero errors, 93 warnings**; this is not a warning-free claim. No new runtime remote/touchpad/keyboard/apps/widget feature protocol was introduced.

Artifacts:

- Debug-signed installable APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Optimized unsigned AAB: `app/build/outputs/bundle/release/app-release.aab` (zero signature entries verified). Owner-controlled signing remains required for Play upload.
- Exact sizes and SHA-256: `release/ARTIFACTS.json`.

## Physical-TV verification steps

1. Install the debug APK. Following the package rename to `com.indianservers.samsungsmarttvremote`, Android treats it as a separate app from the previous package; repeat certificate approval and pairing in the new app. Keep the previous installation if its local data is still needed.
2. Connect phone and Q60AA to the same Wi-Fi. Confirm the TV currently shows IP `192.168.100.224`. Grant local-network permission if requested.
3. Discover/select the TV and tap Connect. The Verify Your Samsung TV dialog should show the full fingerprint above if the certificate remains unchanged. Review the TV/network identity, then choose Trust Certificate & Connect yourself.
4. Accept Smart TV Remote on the television. The app should show AwaitingAuthorization until Samsung sends `ms.channel.connect`, then Connected. Reject once as a separate test and confirm controls stay disabled; retry and approve.
5. Test Home, D-pad and volume; test other features only where supported by this model. Close/reopen the app and verify saved certificate trust/token reuse. A TV may still request authorization based on its own access settings.
6. In Settings, choose Reset TV Certificate Trust and confirm. Reconnect should require fingerprint review again while retaining the separate pairing token. Forgetting the device should remove both.
7. Do not approve a changed fingerprint without investigating TV resets/firmware/network changes. An expired certificate requires date/firmware correction, not bypass. Check Connection Diagnostics for a categorized TLS or pairing error if any step fails.

Real-TV discovery, TLS certificate presentation and the Android review/cancel workflow were observed. Physical Samsung authorization, remote execution and reconnect acceptance after user approval remain unverified; no false Connected result or universal model compatibility is claimed.

