# Google Play disclosures review — 16 October 2026

Implemented visible, feature-specific notices before mirroring, file selection/casting and URL casting. They explain recipient, data involved, explicit action, unencrypted local transfer, stopping and sensitive-screen exposure. Native system consent is preserved. About now uses the installed package version and clearly states independent ownership, trademark attribution, model-specific compatibility and no DRM/authorization bypass. Privacy explains local discovery, selected files, media links, system mirroring, diagnostics and deletion. Diagnostic export describes the shared report before opening Android's chooser.

Policy sources reviewed:
- User Data / prominent disclosure, consent and privacy policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Impersonation: https://support.google.com/googleplay/android-developer/answer/9888374
- Deceptive behavior: https://support.google.com/googleplay/android-developer/answer/9888077

These changes support accurate disclosure; they do not certify Play compliance or approval. No fictional Google certification or mandatory universal disclaimer is presented. Explicit Cast buttons and Android's file picker/system mirroring approval remain the affirmative user actions; no bundled blanket consent is substituted for platform permissions.

Release prerequisites still open:
1. Publisher must provide real legal/developer identity, support/privacy contact, retention/deletion details for any received support material, effective date and a publicly accessible privacy-policy URL. The existing policy draft contains owner placeholders and must not be submitted unchanged. Once supplied, add the public policy link and contact in-app as well as Play Console.
2. Reconcile Data Safety with the actual signed artifact, dependencies, selected file/link/text transfers and diagnostic sharing. Do not assume that LAN transmission or user initiation automatically exempts every disclosure.
3. Review Google's secure handling requirements against unencrypted legacy DLNA/file transfer and optional port 8001. A warning or user consent is not a waiver of policy requirements. Restrict/replace affected functionality if required for distribution; current notices do not assert that HTTP is policy-approved.
4. Recheck store branding, screenshots and compatibility claims. A disclaimer does not excuse misleading titles, icons or unsupported feature claims.
5. Sign and test the final release, complete Play declarations and review current Console findings. No publishing or Play submission was performed.

No new permissions, SDKs, data collection, screen capture or network endpoints were introduced by this disclosure revision.

Artifact scope: the rebuilt debug APK includes these notices. The existing 4.1 release AAB predates this disclosure revision and must be rebuilt and signed before distribution. Android instrumented tests (21 passing) belong to the preceding functional revision; they were not rerun for these presentation-only changes.


Owner supplied contact saisatish@indianservers.com and public policy https://indianservers.com/privacy-policy-4. Both are displayed with browser/email actions in Privacy and About. No email is sent automatically. The browsing tool could not access the hosted page, so its content/availability and consistency remain unverified. Publisher legal identity and effective date still require owner review.


Follow-up verification: an HTTPS request returned HTTP 200 and the company policy text was read. It describes multiple products, potential analytics/permissions and encryption in transit in general terms; it lacks this app's specific local casting/TV data explanation. An owner-review supplement is provided at release/listing/TV_REMOTE_PRIVACY_SUPPLEMENT.md. The website was not modified. Browser verification initially failed, but direct HTTPS verification succeeded.


Final validation with supplied contact links: debug APK build, 64 unit tests and lint succeeded (zero lint errors). No new Android instrumentation run was needed for this text/link revision. The existing release AAB still predates it.
