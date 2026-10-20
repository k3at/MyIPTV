# Changelog

All notable changes to MyIPTV are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Versions match the git tag (`v1.0.0`) and `versionName` in `app/build.gradle.kts`.
When cutting a release, bump **both** `versionName` and `versionCode` — Android
refuses to install an update whose `versionCode` is not higher than the installed
one.

## [2.0.1] — 2026-10-20

Published as `MyIPTV-2.0.1.apk`.

### Changed

- **New app icon.** The launcher icon is now the app's own logo on a dark navy
  gradient sampled from the logo's blues, instead of the purple square with a
  generic play glyph. Android 8+ gets a real **adaptive icon**
  (`mipmap-anydpi-v26` plus a background and a foreground layer), drawn at a
  size that survives every launcher mask — circle, squircle or square — without
  clipping the logo; older versions keep a full-bleed PNG fallback. The logo in
  the app header and in Settings keeps its previous prominence through a
  dedicated `app_logo` drawable.
- **One generator for every asset.** `play-store/generate-art.ps1` now derives
  the launcher icons, the adaptive layers, the in-app logo, the TV banner and
  the Play store art (512px icon and both feature graphics) from a single
  `play-store/logo-source.png`. Replacing that file and re-running the script
  restyles the whole app and listing; nothing is hand-edited.

## [2.0.0] — 2026-10-19

Published as `MyIPTV-2.0.0.apk`.

### Added

- **Multiple panels ("profiles").** Settings now keeps a list of panels — each
  with its own host, username and password — so you can switch between a main
  line and a backup, or between providers, without retyping anything. The chip
  row at the top of Settings adds (`+ Add`), selects and removes (`✕`, with a
  confirmation) a panel; the fields below always belong to the selected one, and
  **Save & continue** connects to it. A panel added but not saved yet survives a
  language switch. Favourites and the "continue watching" point are kept **per
  panel**, so one provider's favourites never show up on another. Upgrading from
  1.0.x turns the existing credentials into the first profile
  automatically — nothing to re-enter.
- **"Sort by recently added"** for Live TV, Movies and Series, next to provider
  order and A–Z. Cycle it from the chip in the top bar; the choice is remembered.
  Items are ordered by the timestamp the panel reports (`added`, `last_modified`
  or `releaseDate`), so a panel that only reports one of those still sorts
  correctly. Unix seconds, milliseconds and `YYYY-MM-DD[ HH:MM:SS]` are all
  understood.
- **Arabic, and a language switch** (System default / English / العربية) in
  Settings. Every screen is translated — lists, player OSD, EPG row, errors — and
  the layout mirrors right-to-left while Arabic is active. The switch applies
  immediately, without a restart. On Android 13+ the choice is also offered in the
  system's per-app language settings.
- **Channel names in Arabic.** While the app is in Arabic, the names your panel
  sends are shown in Arabic even when the provider typed them in Latin. Known
  brands get their proper name ("beIN Sports" → «بي إن سبورت», "MBC" → «إم بي سي»,
  "Al Jazeera" → «الجزيرة») and anything else is transliterated («World News HD» →
  «وورلد نيوز HD»), while numbers and quality tags (4K, HD, SD) stay as they are
  and names that arrive in Arabic are left untouched. Search matches both the
  original and the Arabic name. Switching back to English shows the provider's own
  text — nothing is rewritten on disk.
- **Support for providers from any country.** Channel, category and EPG text is
  decoded from whatever the panel sends: its declared charset is honoured when
  present, otherwise strict UTF-8 is tried first, Windows-1256 (Arabic) is the
  fallback, and ISO-8859-1 is the last resort — so non-Latin playlists no longer
  load as mojibake.
- **A disk cache for the catalogue** (keyed to the account, cleaned up
  automatically and capped), so a restart paints your channels from the previous
  session and then refreshes in the background.

### Changed

- **Much faster and smoother.** Live, Movies and Series are fetched in parallel
  instead of one after another; search and category filtering run off the main
  thread (debounced, with stale results discarded) so typing never stutters; the
  channel and category lists use `DiffUtil` and update in place instead of
  redrawing; favourites flow through the same single update; logos decode
  straight to `RGB_565`; and the lists are partitioned per content type so
  switching tabs does no work.
- The **Settings screen is more compact**: the connection fields, the language
  switch and **Save & continue** all fit on a 720p TV screen without scrolling, in
  English and Arabic alike (Arabic's taller glyphs used to push the button off the
  bottom edge).

### Fixed

- The **refresh** and **settings** buttons can no longer be pushed off the screen
  by a long top bar. With a "continue watching" chip showing, the Arabic top bar
  was wider than a 720p TV screen and the two buttons were clipped and
  unreachable — Settings could not be opened at all. The tabs now sit on their own
  row in the landscape layout (and the sort / continue chips on theirs in
  portrait), so an overflowing label can only shorten itself.

### Removed

- **M3U / M3U8 playlist support.** MyIPTV is an **Xtream Codes** client now: the
  source-type switch, the playlist URL field and the M3U parser are gone. If you
  were using an M3U URL, the app opens Settings on the first launch after
  updating — enter your panel's host, username and password there. Most panels
  that hand out an `m3u_plus` link speak Xtream Codes on the same host.

## [1.0.1] — 2026-10-19

Published as `MyIPTV-1.0.1.apk`.

### Fixed

- **The TV-box remote can now reach every player control.** Previously the
  on-screen controls were not focusable, so a remote could only use the built-in
  shortcuts (volume, channel change) and had no way to touch the volume or
  brightness sliders, mute, aspect ratio, playback speed or the channel-list
  button. The controls are now real focus stops:

  - `OK` raises the control panel and lands on play/pause.
  - The arrows move the focus ring between controls; `OK` presses the focused
    control; left/right drag a focused slider (volume, brightness, seek).
  - `Back` dismisses the panel and returns the arrows to the shortcuts, which is
    also what happens when the panel hides itself a few seconds into playback.

  The custom D-pad shortcuts are unchanged while the panel is down: up/down for
  volume (or brightness), left/right to seek or zap channels.

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
