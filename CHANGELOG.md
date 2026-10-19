# Changelog

All notable changes to MyIPTV are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Versions match the git tag (`v1.0.0`) and `versionName` in `app/build.gradle.kts`.
When cutting a release, bump **both** `versionName` and `versionCode` — Android
refuses to install an update whose `versionCode` is not higher than the installed
one.

## [Unreleased]

Nothing yet.

## [1.0.0] — 2026-10-19

First release. Published as `MyIPTV-1.0.0.apk`.

### Added

- **In-app configuration.** All connection details are entered inside the app on
  first run — no config file, no rebuild. Choose between:
  - **M3U** — a playlist URL.
  - **Xtream Codes** — host, username and password.
  `http://` is prepended automatically when the scheme is omitted.
- **Live TV browsing** — channel grid, category list, and search across the whole
  playlist.
- **Movies (VOD)** and **Series** (with seasons and episodes), Xtream providers
  only. M3U sources show an empty Series tab.
- **Full internal player**, no hand-off to an external app:
  - Play / pause, ±10 second seek, scrub bar with elapsed and total time.
  - Volume slider, mute, and an on-screen level indicator.
  - Window brightness slider.
  - Aspect ratio cycle: Fit / Fill / Zoom.
  - Playback speed 0.5×–2× (movies and series only; live is locked to 1×).
  - Previous / next channel for zapping.
  - Now / next programme info from `get_short_epg` where the provider supplies it.
  - Controls auto-hide after 5 seconds.
- **Phone, tablet and Android TV** from one artefact — portrait layouts in
  `res/layout/`, TV/landscape layouts in `res/layout-land/`. Fully D-pad
  navigable; all remote keys are handled in `PlayerActivity.onKeyDown`.
- **Favourites**, and last-played channel persistence so the app reopens where you
  left off.
- **Large playlist support** — verified with a 20,000-channel playlist. The channel
  list is handed to the player through an in-memory queue rather than an Intent
  extra, avoiding `TransactionTooLargeException` on big playlists.
- Cleartext HTTP support, because many IPTV providers do not offer TLS.

### Notes

- Series and EPG require an Xtream Codes source; M3U playlists are treated as
  live-only.
- Credentials are stored in the app's private `SharedPreferences`. They are never
  sent anywhere except the provider server you configure.
- The app bundles no content, channels or playlists of any kind.

[Unreleased]: https://github.com/k3at/MyIPTV/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/k3at/MyIPTV/releases/tag/v1.0.0
