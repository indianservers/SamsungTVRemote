# Privacy policy draft — owner review required

Publisher: Indian Servers Private Limited
Contact: saisatish@indianservers.com
Public policy: https://indianservers.com/privacy-policy-4
Effective date: [OWNER_CHOSEN_EFFECTIVE_DATE]
Application: Universal TV Remote (`com.indianservers.samsungsmarttvremote`)

Complete the owner details, review actual distribution/support practices, and host the approved policy on public HTTPS before submission.

## Local operation and storage

The app communicates with a user-selected compatible television on the local network. It has no developer-operated control backend, accounts, advertising SDK or analytics service. The developer does not automatically receive TV details, pairing information, remote commands or typed input through this app.

TV identities, names, rooms, model information, local addresses, selection, preferences, app metadata, favorites and widget assignments are saved on the phone. Pairing tokens and approved Samsung certificate identities are encrypted locally using Android Keystore. Brand preference and advertised local-media service endpoints are also stored locally. Android backup/device transfer is excluded by app backup rules.

Remove one TV to delete its credentials/preferences/widget assignments, or use Privacy to reset local app data. Removing a profile does not revoke approval stored on the television; use its device manager for that.

## Transmissions to the TV

For selected compatible UPnP/DLNA renderers, the app queries advertised service schemas, volume and playback position/duration and sends user-requested volume/playback actions. These HTTP requests are unencrypted and are not sent to a developer backend. A three-second media refresh operates only during the foreground connection. Current media identifiers remain in memory and are excluded from diagnostic export.

Discovery reveals ordinary local-network connection information to reachable devices. Remote keys, app-launch identifiers and text you explicitly send go to the selected television. Draft text is not deliberately logged or persisted and is cleared on backgrounding or leaving the keyboard. The TV, its applications and the Android keyboard have their own policies.

Encrypted connections are preferred, but explicitly selected non-TLS modes exist. A scoped certificate exception does not independently authenticate the first accepted certificate. Use trusted networks, verify the TV address and avoid sensitive text over unencrypted connections. No universal security or encryption guarantee is made.

## Diagnostics and sharing

Diagnostics remain local unless you invoke Android's share sheet. Exports exclude TV names/identities/addresses, tokens, raw TV events, keyboard input and arbitrary error logs. They contain network/permission availability, protocol category, state, failure category and the last submission timestamp. No automatic upload occurs. Your chosen recipient has its own practices; the publisher must document actual support retention/deletion before publishing.

## Permissions and contact

Internet/network-state and multicast permissions support local communication, monitoring and discovery. Android 17/API 37 local-network permission is requested where required. Denial/revocation prevents communication until restored. Remote features do not request contacts, location, microphone, camera, advertising ID or broad storage access.

This is a general remote utility. The publisher must review audience/content declarations and relevant country requirements, publish changes as practices change and provide real privacy contact details. This draft does not establish legal or Play compliance.

## Explicit media casting and native mirroring

The Android document picker grants access to a file you choose; choosing it alone does not share it. When you explicitly cast it, a temporary HTTP server shares only that file with the selected TV IP using an unguessable path. This local traffic is unencrypted. Sharing ends when the app goes to the background, disconnects, switches receiver or you stop/clear it. No broad storage permission is requested. A direct media URL is sent to the selected TV, which fetches it under its own networking/privacy behavior. URLs remain in memory and are not included in diagnostic exports. The app does not capture your display, microphone or camera. Screen mirroring opens Android's system controls; the phone's mirroring service manages consent, transmission and stopping.
