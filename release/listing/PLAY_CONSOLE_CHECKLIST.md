# Release-owner checklist

## Data Safety

- Audit the final artifact and dependencies for SDK/network behavior. Current source has no analytics, ads, accounts or automatic upload backend.
- Inventory local profiles, identifiers, addresses, encrypted tokens, preferences, app metadata, favorites and widget configuration.
- Assess user-initiated text/remote transfers to TVs and optional diagnostic sharing against the official definitions. LAN traffic is still off the phone; do not automatically mark all handling excluded.
- Review user-initiated sharing exceptions and ephemeral processing definitions. Complete declarations for the actual implementation and support workflow.
- Do not claim all traffic is encrypted: disclosed port-8001 modes exist. Storage encryption does not establish transit encryption.
- Make the policy, in-app text and Data Safety answers consistent; do not claim an independent security audit.

## Play submission

1. Complete real Samsung model/firmware testing and the compatibility matrix.
2. Retain target API 37 and verify Android 17 permission denial/revocation. Current ordinary phone-app submission requirements are API 36+; recheck Console at upload.
3. Supply an owner-controlled upload signing key through the documented environment workflow. Keep keys/passwords outside Git and review Play App Signing. The unsigned bundle is not upload-ready.
4. Test the signed/minified release on hardware, including 16 KB page-size devices, memory pressure, TalkBack, large fonts, reduced motion, widget hosts, process death and revocation. Review pre-launch reports.
5. Host the approved privacy policy and supply real publisher/support details. Complete Data Safety, app access, audience, rating, ads, permission and compatibility declarations and account-specific testing/verification requirements.
6. Upload the 512×512 icon and 1024×500 feature graphic. Use genuine app screenshots, not test-server screenshots with fictitious TVs. Add physical-TV and optional tablet captures when available.
7. Upload a signed, incremented-version AAB to internal testing and validate installation/upgrade/R8 before wider testing.
8. Resolve findings. The owner must explicitly start a staged production rollout; this task does not publish automatically.

## Official references checked 15 October 2026

- [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Preview assets](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Icon specification](https://developer.android.com/distribute/google-play/resources/icon-design-specifications)
- [Data Safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469)
- [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Local-network permission](https://developer.android.com/privacy-and-security/local-network-permission)
