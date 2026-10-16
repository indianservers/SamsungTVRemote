# Screen mirror and media casting — 4.1.0

## Implemented

The Cast navigation destination contains two separate workflows. Screen Mirror launches Android's public ACTION_CAST_SETTINGS, with guidance if that activity is unavailable. The system service owns screen sharing, approval and stopping; the app does not capture the screen or infer a successful mirror session. This is a native-control handoff, not a new Miracast, Google Cast SDK or AirPlay sender. Reference: https://developer.android.com/reference/android/provider/Settings#ACTION_CAST_SETTINGS

Media Casting loads a selected local file or direct HTTP/HTTPS media URL using actual UPnP AVTransport SetAVTransportURI followed by Play, including escaped DIDL-Lite metadata. Both actions must be advertised by the selected receiver. Existing capability-based volume and timeline sliders remain available. Stop sends the receiver's advertised Stop action and closes local sharing; acknowledgments do not prove visible playback.

Supported file MIME types are MP4/WebM video, MP3/M4A audio and JPEG/PNG images. Actual codecs and renderer behavior determine playback. Web pages, DRM streams and arbitrary streaming-app control are not supported. A brand selector alone does not establish compatibility.

## Security and lifecycle

Android's document picker supplies a content URI; no broad storage permission is added. Selection preflights MIME and positive size but does not transmit content. Explicit Cast starts a server bound to the phone's local IPv4, with an unguessable temporary path, one selected document and source-address restriction to the selected TV. GET/HEAD, bounded headers and byte ranges are implemented. Only three clients are admitted. Stopping closes listeners, sockets and open streams.

Local file transfer is unencrypted HTTP. Keep the app open: backgrounding, disconnecting, switching receiver or clearing the file closes sharing. URL casting sends the URL directly to the receiver; it may continue independently after backgrounding. URLs and capability paths are not exported in diagnostics. No screen/microphone/camera capture or foreground capture service was introduced. Samsung certificate/token handling remains separate.

## Changed components

- CastingModels.kt: media URL/MIME policy and byte-range parsing.
- LocalCastServer.kt: temporary selected-file HTTP service.
- CastingController.kt: file preflight, receiver guards, lifecycle and submission state.
- LocalMediaSession.kt / ConnectionManager.kt: serialized capability-checked load/stop requests.
- CastingScreen.kt / MainActivity.kt / RemoteViewModel.kt: Cast tabs, system mirroring entry and cleanup.
- CastingTest.kt / ProductionInstrumentedTest.kt: protocol, HTTP, policy and UI coverage.
- Gradle version, README, privacy/listing drafts and artifact manifest.

## Physical-TV verification

1. Install an update signed with the same key as the existing app. Keep the current package and saved Samsung approvals. Connect phone and TV to a trusted shared Wi-Fi network.
2. Open Cast → Screen Mirror → Open phone mirroring controls. Select the TV and approve in system/TV UI. Verify movement/audio and stop in system controls. If no receiver appears, check the phone/TV mirroring protocol; Samsung remote pairing alone is insufficient.
3. Connect the TV in Devices. Open Media Casting and Check media capabilities. Unsupported receivers must keep casting disabled.
4. Choose a small supported photo/video/audio file. Confirm choosing alone does not play anything. Tap Cast selected file and verify actual TV output. Test volume/seek only when reported available; seek may be unsupported for images/live streams.
5. Stop playback, then repeat and background the app. Verify local-file transfer ends. Switch receiver/disconnect and verify the old sharing URL no longer serves content.
6. Cast a known direct media URL with the correct MIME choice. Verify TV output and Stop. Test an unsupported codec and unreachable URL without interpreting request acknowledgment as playback.
7. Reconnect Samsung and verify existing D-pad, touchpad, keyboard, apps, favorites and widgets.

Physical-TV mirroring, codec playback and network behavior require owner verification; emulator/HTTP fixtures cannot certify specific TV models.

## Debug signing

The previous 4.0 sandbox-built debug APK used certificate SHA-256 f8f951dd48a4e26b4ac749d65cdb5bbfdbb3e016cf61a24ad98709e877e20174. This Windows build uses the existing local debug key, SHA-256 4c19e54940c6970b0d04e57bec6e36d917dfc5830a3250c5f815fb3e6832ded5. Android rejects an in-place update across those keys. The previous key was not available in this environment. No existing app was uninstalled or data erased. A separate isolated emulator is used for new-version validation. Production updates require the owner's consistent release key; the release bundle remains unsigned.

## Build and automated validation

Debug APK, Android test APK, optimized release AAB, unit tests and lint completed successfully with Gradle (max-workers=1). 64 JVM tests passed with zero failures, including media policy/range, real selected-file HTTP behavior and SOAP load/play/stop fixtures. All 21 Android instrumented tests passed on a fresh isolated API 37.1 / 16 KB emulator, including both new casting tests and existing Samsung/trust/navigation/widget/touchpad regression tests. Lint: zero errors, 103 existing warnings. Release AAB contains zero ZIP signing entries and requires owner signing. Artifact sizes and SHA-256 values are recorded in release/ARTIFACTS.json.

The initial update attempt against the existing 4.0 emulator was rejected for signing incompatibility. That installation was preserved; no pass from its older test APK is counted in the 21-test result above.

Visual QA: both Cast tabs were captured and inspected at 1080×2400 with the existing snow-white/blue theme. Screenshots are release/screenshots/casting-mirror.png and casting-media.png. The fresh emulator briefly showed a System UI responsiveness dialog after the suite; waiting restored the UI. This was outside the app process; no physical-device stability claim is inferred.
