# Phase 3 source security review

This is an implementation/source review and automated verification, not an independent penetration test or security certification.

## Fixed findings

- The former Forget path cleared all preferences. Device removal now deletes only that profile's token, favorites/settings and widget assignments. An explicit confirmed reset handles all-data deletion.
- The WebSocket URL previously appended a cached token regardless of transport. Tokens are now appended only on TLS port 8002. Non-TLS reconnect tests verify no token appears in the URL.
- HTTP authorization/protocol failures no longer start retry loops. Ordinary recoverable failures retain bounded backoff, and rediscovery is bounded per selected-device/network epoch.
- Strict canonical private IPv4 validation rejects public, malformed, signed, leading-zero and path-containing addresses. Device identifiers and persisted profile endpoints are checked before use; discovery validates Samsung's bounded device-information response.
- Diagnostics export uses a fixed allowlist rather than redacting arbitrary raw logs. User text, tokens, identities, addresses and exception messages cannot enter the report serializer.

## Controls reviewed

- Android Keystore AES-GCM token storage; cryptography runs on IO. Independent per-TV token/preferences keys. Backup/transfer exclusions remain enabled.
- Only selected/discovered/previously paired devices are exposed by production connection flows. One connection manager owns all feature traffic; session generation, cancellation and bounded queues prevent TV switching from mixing commands.
- Normal TLS verification is the default. The device-specific exception requires explicit review of the SHA-256 leaf certificate fingerprint. The approved identity is Keystore-encrypted and bound to TV ID, private IP and port. Each connection attempts system verification first; the fallback accepts only the exact approved leaf and valid dates. The hostname exception requires that same endpoint and leaf. Changed certificates are rejected for fresh review; reset removes approval. No global trust manager/hostname policy changes. First approval remains trust-on-first-use and requires the user to verify their TV/network.
- Network Security Configuration allows cleartext for dynamic private-IP discovery and explicitly non-TLS TVs, because XML cannot express private subnets. Production call sites validate private addresses and disable redirects. Local cleartext traffic is not represented as encrypted or universally secure.
- No hardcoded credentials, analytics logging, raw event logging or typed-text persistence was found in main source. Keyboard drafts clear on background/disposal; capability gates remain intact.
- Exported launcher/configuration activities require explicit user-visible actions. Widget requests validate real widget ownership/configuration and a saved device; commands require live-session Send confirmation in the app.
- Widget receivers and FileProvider are non-exported. Widget PendingIntents are immutable, explicit and differentiated by URI; the provider shares only a dedicated diagnostics cache path with temporary read access.
- Widgets use bounded asynchronous metadata updates and activity PendingIntents, not background TV-control receivers, services, polling or new always-on sockets.
- Runtime local-network requests are restricted to Android 17 with target API 37+. Permission denial/revocation closes/prevents communication. No microphone, camera, location, contacts, advertising-ID or broad-storage permission is added.

## Remaining release review

Validate signed/minified runtime behavior, physical TV authorization/revocation/TLS quirks, real network interruptions, widget hosts, dependency updates and Play pre-launch findings. Complete Data Safety and actual support/privacy practices with the publisher. Unsupported/unverified TV capabilities remain disabled or explicitly labeled candidates.
