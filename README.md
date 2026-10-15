# Smart TV Remote — Phase 3

Native Kotlin / Compose Android app, package `com.indianservers.samsungsmarttvremote`. Version 3.0.1 (code 4), minimum API 26, compile/target API 37. Phase 3 adds independent saved TVs, configurable home-screen widgets, five appearance modes, diagnostics/export, onboarding, help and release preparation while preserving the earlier remote features.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:bundleRelease --console=plain
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Optimized bundle: `app/build/outputs/bundle/release/app-release.aab`. The current bundle is **unsigned**; follow [release signing](docs/RELEASE_SIGNING.md) with owner-controlled credentials before upload.

## Use

The package is now `com.indianservers.samsungsmarttvremote`. Android installs this as a separate app from the previous package; saved TVs, certificate approvals and pairing tokens do not transfer automatically.

Complete or skip onboarding, allow local-network access when requested, and put phone and TV on the same Wi-Fi. Discover or manually add the TV and accept its authorization request. Devices supports switching, friendly names, rooms and independent removal. Settings contains appearance, widgets, diagnostics, help and privacy controls.

Each widget maps to one saved TV. Controls open the app for a live authorized connection and explicit send confirmation. TV capabilities remain model-dependent; pointer mode, direct HDMI selection and remote text deletion remain gated pending verification.

## Validation and release

See the [TLS fix report](docs/TLS_CERTIFICATE_FIX.md) for the latest build and regression results. Earlier Phase 3 validation: 42 JVM tests and 12 Android emulator tests passed. Lint: zero errors, 91 warnings. Debug APK and optimized AAB built successfully. The actual Q60AA certificate review was verified; physical pairing/remote acceptance and signed-release testing remain required.

- [Phase 3 implementation and validation report](docs/PHASE3_REPORT.md)
- [Security review](docs/SECURITY_REVIEW.md)
- [Compatibility matrix](docs/SAMSUNG_COMPATIBILITY_MATRIX.csv)
- [Artifact sizes and SHA-256 hashes](release/ARTIFACTS.json)
- [Play submission checklist](release/listing/PLAY_CONSOLE_CHECKLIST.md)
- [Store listing](release/listing/STORE_LISTING.md) and [privacy policy draft](release/listing/PRIVACY_POLICY_DRAFT.md)
- [Asset specifications](release/assets/ASSET_SPECIFICATIONS.md)
- [Preserved Phase 2 implementation](docs/PHASE2_IMPLEMENTATION.md)

Production screenshots in `release/screenshots` are genuine emulator captures. Older QA images may show simulated TVs and are not hardware compatibility evidence.

