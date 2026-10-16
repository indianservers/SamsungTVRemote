# Universal TV Remote

Native Kotlin / Compose Android app, package `com.indianservers.samsungsmarttvremote`. Version 4.1.0 (code 6), minSdk 26, compile/targetSdk 37. The working Samsung pairing/certificate/remote workflow is preserved, with a redesigned interface, 30-brand selector and capability-checked UPnP/DLNA media controls.

## Compatibility

- **Samsung:** existing WebSocket remote, D-pad/touchpad, focus-gated text, supported app requests, favorites and widgets. Behavior still depends on model/firmware.
- **Other brands:** local-media controls only when the selected model actually advertises usable UPnP RenderingControl/AVTransport services. A brand entry does not provide full LG webOS, Android/Google TV, Roku, VIDAA or proprietary menu control.
- **Sliders:** absolute volume needs an advertised range and actual value; playback seeking needs an actual seekable renderer timeline. No invented current volume, duration or streaming-app seek support.

See the [compatibility matrix](docs/UNIVERSAL_COMPATIBILITY.csv) and [complete UI/protocol report](docs/UNIVERSAL_UI_REPORT.md). Exact non-Samsung model datasheets and physical-TV acceptance remain outstanding; test fixtures do not establish hardware compatibility.

## Use

Choose Settings → TV brands, select a brand and find compatible televisions on the same local network. Saved devices retain their own identities/preferences. Samsung users review the actual certificate fingerprint if required and approve pairing on their TV. The package is unchanged from 3.0.1, so existing pairings and certificate approvals stay in the app.

Use the Remote, Devices, Cast and Settings navigation bar. Core controls are compact; expand Volume slider, playback & channels for media controls. Check media capabilities discovers a same-TV local-media service and reconnects the Samsung remote while retaining its token/certificate policy. Local-media HTTP is unencrypted and separate from the secure Samsung socket. Seek and volume requests are sent on drag completion and then queried back from the TV.

## Build and tests

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:bundleRelease --console=plain
```

For a restricted environment that cannot create Kotlin daemon files, add the quoted Gradle argument `'-Pkotlin.compiler.execution.strategy=in-process'`.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Release AAB: `app/build/outputs/bundle/release/app-release.aab`. The bundle is unsigned unless the owner supplies [controlled signing credentials](docs/RELEASE_SIGNING.md). Exact final hashes: [artifact manifest](release/ARTIFACTS.json).

The test suite covers Samsung transport/trust regression, service profile migration, URL/XML defenses, actual local SOAP fixture volume/seek commands, brand/navigation persistence, slider interaction, keyboard/apps/favorites, widgets and lifecycle. Final results are in the [UI report](docs/UNIVERSAL_UI_REPORT.md).

## Release materials

- [Store listing draft](release/listing/STORE_LISTING.md), [privacy draft](release/listing/PRIVACY_POLICY_DRAFT.md), [Play checklist](release/listing/PLAY_CONSOLE_CHECKLIST.md)
- [Security review](docs/SECURITY_REVIEW.md), [Samsung TLS investigation](docs/TLS_CERTIFICATE_FIX.md)
- [Artwork provenance](release/assets/ASSET_SPECIFICATIONS.md)
- Previous phase reports in `docs` describe the historical versions; older release screenshots show the preceding interface.

## Screen mirror and casting (4.1)

Open **Cast → Screen Mirror → Open phone mirroring controls** to choose a receiver in Android's native Cast/Smart View interface. The phone and TV must support the same protocol; the app does not capture the display or implement a Miracast/Google Cast sender.

For **Media Casting**, connect a TV and check media capabilities. A renderer must advertise SetAVTransportURI and Play. Choose one supported file, then explicitly tap Cast selected file, or send a direct media URL with its correct format. The TV must support the actual container/codecs. Local files are served over a temporary unencrypted HTTP URL restricted to the selected TV IP, with byte-range support. Leaving the app stops file sharing. Stop casting/playback requests DLNA Stop when available. Web streams may continue independently on the receiver.

See [casting validation and physical-TV instructions](docs/CASTING_REPORT.md). Debug APK upgrades require the same signing key: the prior 4.0 sandbox build has a different debug certificate from this normal Windows build. Do not uninstall an existing installation merely to bypass this mismatch; that deletes its saved settings.
