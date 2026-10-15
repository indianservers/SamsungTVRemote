# Controlled release signing

The project never creates a production key. The debug APK uses Android's development signing configuration. Release optimization is enabled through AGP 9.3's `optimization { enable = true }`, which enables code and resource optimization, with default Android optimized rules and the app's small ProGuard rules file.

The owner must create/manage an upload key under their own controls, keep it outside this repository, and arrange appropriate backup/recovery and Play App Signing. Existing production applications must retain their correct signing identity and increment the version code.

Inject all four environment variables into the build process through the owner's secret manager/CI or a secured local environment:

| Variable | Meaning |
|---|---|
| SMARTREMOTE_KEYSTORE | Absolute path to the owner-controlled upload keystore |
| SMARTREMOTE_STORE_PASSWORD | Keystore password |
| SMARTREMOTE_KEY_ALIAS | Upload-key alias |
| SMARTREMOTE_KEY_PASSWORD | Key password |

Providing only some variables fails configuration with a value-free error. No password values are written to Gradle files, printed, or committed. Keystore/signing files are ignored by Git. Do not paste secrets into shell command history, chat, artifacts or logs.

With the environment supplied, run `gradlew.bat :app:bundleRelease`. Verify the resulting bundle's signature/certificate, test the signed/minified build, and upload through the owner's Play Console internal-testing workflow.

With no variables, the same command produces an **unsigned** optimized AAB. This task's release bundle was inspected and had zero META-INF cryptographic signature entries. It is a build-validation artifact, not a production-upload-ready bundle. Do not use the debug key as a production signing substitute.

Outputs:
- `app/build/outputs/bundle/release/app-release.aab`
- `app/build/outputs/mapping/release/mapping.txt` and related R8 outputs
- `app/build/outputs/apk/debug/app-debug.apk`

No Play publication, production key creation or account change is performed by this project.
