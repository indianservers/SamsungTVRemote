# Release artwork and screenshot provenance

- `app-icon-source.png`: user-supplied Smartphone Remote Controlling Smart TV image, preserved unchanged. `app-icon-512.png` is a resized copy. The supplied image already includes rounded artwork and white corners.
- `feature-graphic-1024x500.png`: original 1024×500 RGB PNG. It is promotional artwork, not a screenshot or proof of TV compatibility.
- `app-icon.svg` is the previous icon design; `feature-graphic.svg` remains the editable feature artwork.
- `tools/generate-brand-assets.ps1`: reproducible native drawing source for the PNG variations; uses Windows System.Drawing and Segoe UI. No remote artwork or Samsung logo is used.
- `tools/generate-app-icon.ps1` reproduces launcher density sizes and the 512px image from the supplied source. Android adaptive foreground uses that image inset on white to preserve its contents under launcher masks. Android themed icons retain the simple TV/remote monochrome silhouette; full-color icons use the supplied artwork.

Capture genuine production application screens into `release/screenshots/`. Do not copy the older Phase 2 fake-TV test captures from `qa/screenshots/` into a Play submission: their TV/app names are fixtures.

Phone captures should show actual app UI, correct supported-feature states and no misleading installed-TV/app claims. Add connected-remote screenshots only after real TV testing. Optional tablet screenshots remain a release-owner task. Inspect Console's current size/aspect-ratio constraints before upload.

References: [Play assets](https://support.google.com/googleplay/android-developer/answer/9866151), [icon specification](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).


The universal/ screenshot subdirectory contains the 4.0 production onboarding, empty-device, grouped-settings and brand-selector captures. Older top-level captures show the previous UI. Connected test-server images under qa are fixtures and are not store compatibility evidence.
