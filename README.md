# MyIPTV — IPTV player for Android TV & mobile

A lightweight, IBO-Player-style IPTV client. One codebase runs on **Android TV /
TV boxes** and **Android phones/tablets**. Connection details are entered **in the
app** (no rebuild needed) and saved on the device, and the app ships with **its own
logo and branding** (purple `#7C4DFF` on near-black `#101010`).

---

## ⬇️ Download & install

Releases are published on **GitHub Releases**:

| What | Where |
|------|-------|
| Latest release | https://github.com/k3at/MyIPTV/releases/latest |
| Direct download | `MyIPTV-<version>.apk` on that page |
| Automatic updates | [Obtainium](https://github.com/ImranR98/Obtainium) → paste `https://github.com/k3at/MyIPTV` |

Every release is signed with the same upload key, so new versions install straight
over old ones. Android TV / Fire TV users can also sideload with
`adb install -r MyIPTV-<version>.apk`.

- **How releases are cut:** [RELEASING.md](RELEASING.md)
- **What changed, per version:** [CHANGELOG.md](CHANGELOG.md)
- **Store route (optional, with caveats):** `play-store/LISTING.md`

---

## ✨ Features

- **In-app setup** — on first launch the app asks for your credentials; both **Xtream Codes** (host + username + password) and **M3U** (playlist URL) are supported. Change them any time from the gear button.
- **M3U / M3U8 parser** — handles `tvg-logo`, `group-title`, `tvg-name` and the running `#EXTGRP:` directive.
- **Xtream Codes API** — loads Live channels, Movies (VOD) **and Series** with category names.
- **Series browsing** — tap a series to pick a **season**, then an episode; episodes play through the same player (Xtream mode).
- **EPG (now / next)** — for live channels the player OSD shows the current programme and what's up next (Xtream `get_short_epg`).
- **Categories** — "All Channels", "Favorites" and every group from the source.
- **Live / Movies / Series tabs**, **search**, and **favorites** (long-press a channel, or use the star in the player).
- **Continue watching** — resumes your last channel from a chip in the toolbar.
- **Fullscreen player** (Media3/ExoPlayer) with a custom control overlay:
  - **play / pause**, big centre transport, **±10 s seek** and a scrub bar (movies & series),
  - **volume** slider + mute (system music stream, remembered between sessions),
  - **brightness** slider for the video window (remembered),
  - **aspect ratio** cycling — Fit / Fill / Zoom,
  - **playback speed** cycling — 0.5× to 2× (movies & series),
  - channel-list drawer for fast zapping and next/previous channel buttons,
  - TV remote support: **D-pad centre** plays/pauses, **left/right** seeks or zaps, **up/down** adjusts the volume *or* brightness (audio/video keys), **MENU** opens the channel list, **vol keys** change the volume,
  - HLS (`.m3u8`), progressive MP4 and MPEG-TS stream support.
- **Android TV ready** — Leanback launcher intent, TV banner, D-pad focus states and remote key handling.
- **Adaptive layout** — phone (portrait) and TV/landscape layouts share one codebase.

---

## 🚀 Configure (inside the app)

On **first launch** the app opens the **Connection** screen automatically. Pick your
source type and fill in the fields:

**Xtream Codes**
| Field | Example |
|---|---|
| Server URL | `http://your-panel:8080` |
| Username | `your_username` |
| Password | `your_password` |
| Live stream format | `HLS (m3u8)` or `MPEG-TS` |

**M3U Playlist**
| Field | Example |
|---|---|
| Playlist URL | `http://your-panel/get.php?username=...&password=...&type=m3u_plus&output=ts` |

Tap **Save & continue** and the app loads your playlist. To change any of it later,
tap the **gear button** in the top bar — no rebuild required.

- Settings are stored in the app's private `SharedPreferences` on the device.
- A bare host (e.g. `your-panel:8080`) is accepted; `http://` is added automatically.

> Only the non-user-facing knobs (timeouts, user agent, EPG on/off) live in `app/src/main/java/com/myiptv/app/config/AppConfig.kt`.

> Cleartext HTTP is enabled (`android:usesCleartextTraffic="true"`) because most IPTV panels serve plain `http://` streams.

---

## 🎮 Player controls

| Action | Touch | Remote / keyboard |
|---|---|---|
| Play / pause | centre button | `OK` / `Enter` / `Space` / media play-pause |
| Seek ±10 s (movies & series) | ⏪ / ⏩ buttons | D-pad ◀ / ▶ |
| Zap channel | ‹ / › buttons | D-pad ◀ / ▶ (live), `CH+`/`CH-`, media next/previous |
| Volume | volume slider | D-pad ▲ / ▼, `Vol+` / `Vol-` |
| Brightness | brightness slider | tap the ☀ icon, then D-pad ▲ / ▼ |
| Mute | speaker button | — |
| Aspect ratio | `Fit` / `Fill` / `Zoom` button | — |
| Playback speed | `1.0×` button | — |
| Channel list | list button | `MENU` |
| Hide the controls | tap the video | `Back` |

Volume, brightness, aspect ratio and speed are remembered between sessions.
Live streams hide the seek bar and speed control (no reliable seekable window).

---

## 🏗️ Build

### Option A — Android Studio
1. **Open** the `MyIPTV` folder in Android Studio (Quail / 2026.1 or newer, since AGP 9.4 is required for `compileSdk 37`).
2. Let Gradle sync, then **Run ▶** on a device/emulator, or **Build → Build APK(s)**.

### Option B — Command line
Requires JDK 17+ (Android Studio's bundled JBR works) and the Android SDK.

```powershell
# from the project root
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```

or use the helper:

```powershell
.\build.ps1          # debug APK
.\build.ps1 -Release # release APK (minified)
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Install on a device/TV box:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The SDK location is read from `local.properties` (`sdk.dir=...`). Regenerate it if
you move the project.

### Release signing

Release builds are signed with your own upload key instead of the debug key. The
details live in `keystore.properties` in the project root, which is **never
committed** (see `.gitignore`):

```properties
storeFile=keystore/upload-keystore.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

If that file is missing, the release build falls back to the debug key so a fresh
clone still builds — which is why the fallback exists and why you should not rely
on it for anything you actually publish.

> ⚠️ **Back up `keystore/upload-keystore.jks` and its password somewhere safe**
> (password manager + offline copy). If you lose the key you cannot publish an
> update to the same app listing. With Play App Signing the *upload* key can be
> reset, but only while you still have access to the Play Console account.

Because the published app now uses a different key than the earlier debug-signed
builds, installing over one of those requires an uninstall first:

```powershell
adb uninstall com.myiptv.app
adb install app\build\outputs\apk\release\app-release.apk
```

### Build artefacts

| Command | Output | Use |
|---------|--------|-----|
| `.\build.ps1` | `app/build/outputs/apk/debug/app-debug.apk` | local testing |
| `.\build.ps1 -Release` | `app/build/outputs/apk/release/app-release.apk` | sideloading / direct download |
| `.\build.ps1 -Bundle` | `app/build/outputs/bundle/release/app-release.aab` | Google Play upload |
| `.\build.ps1 -Release -Bundle` | both | release day |

---

## 🚀 Publishing

The primary distribution channel is **GitHub Releases** — free, no review, and it
reaches TV-box users directly.

| | |
|---|---|
| **Release pipeline** | `.github/workflows/release.yml` — builds a signed APK + AAB on every `v*` tag and publishes them with generated release notes |
| **Compile check / tester builds** | `.github/workflows/ci.yml` — debug APK on every push and PR |
| **Signing key** | `keystore/upload-keystore.jks`, restored in CI from repository secrets |
| **Guide** | [RELEASING.md](RELEASING.md) — one-time setup, cutting a release, Obtainium, troubleshooting |
| **Helper** | `tools/export-keystore-for-ci.ps1` — copies the keystore to the clipboard as base64 for the `KEYSTORE_BASE64` secret |

An optional Play Store route is prepared but **not recommended as the primary
channel** — see the policy caveats and the comparison of alternatives in
`play-store/LISTING.md`. Store assets and listing copy live in `play-store/`:

| File | Size | Where it goes |
|------|------|---------------|
| `icon-512.png` | 512×512 | Play Console → Store listing → App icon |
| `feature-graphic-1024x500.png` | 1024×500 | Play Console → Store listing → Feature graphic |
| `tv-banner-320x180.png` | 320×180 | Android TV listing banner |
| `LISTING.md` | — | Distribution options, title, descriptions, Data safety & content-rating answers |
| `privacy-policy.md` | — | Privacy policy text to host at a public URL |
| `screenshots/` | — | Phone, 7"/10" tablet and Android TV screenshots |

---

## 🧱 Tech stack / toolchain

| Item | Version |
|------|---------|
| Language | Kotlin 2.3.0 |
| Build | Gradle 9.6.0, Android Gradle Plugin 9.4.0 |
| compileSdk / targetSdk | 37 |
| minSdk | 23 (Android 6.0 — required by Material Components 1.14; covers most TV boxes) |
| Playback | AndroidX Media3 / ExoPlayer 1.11.1 |
| Networking | OkHttp 4.12.0 |
| Images | Glide 4.16.0 |
| UI | Material Components 1.14.0, ViewBinding, RecyclerView |

> AGP 9 enables its new DSL and built-in Kotlin by default. This project opts out
> of both (`android.newDsl=false`, `android.builtInKotlin=false` in
> `gradle.properties`) to use the classic, widely-documented Kotlin plugin setup.

---

## 📁 Project structure

```
app/src/main/java/com/myiptv/app/
├── config/AppConfig.kt          ← app constants (UA, timeouts, EPG toggle)
├── MyIptvApplication.kt         ← shared settings + repository + prefs + play queue
├── data/
│   ├── Models.kt                ← Channel / Episode / EpgEntry / CategoryRow
│   ├── Settings.kt              ← in-app connection settings (source + credentials)
│   ├── M3uParser.kt             ← M3U/M3U8 parser
│   ├── XtreamApi.kt             ← Xtream Codes client
│   ├── PlaylistRepository.kt    ← load() + in-memory cache
│   └── Prefs.kt                 ← favorites + last channel
└── ui/
    ├── MainActivity.kt          ← browse (tabs, categories, search, grid)
    ├── SettingsActivity.kt      ← first-run setup / change credentials
    ├── SeriesActivity.kt        ← seasons + episodes (Xtream series)
    ├── PlayerActivity.kt        ← playback + drawer + remote keys + EPG
    ├── CategoryAdapter.kt
    ├── ChannelAdapter.kt
    ├── EpisodeAdapter.kt
    └── PlayerChannelAdapter.kt

app/src/main/res/
├── layout/          ← phone (portrait) layouts
├── layout-land/     ← TV / landscape layouts
├── drawable/        ← vectors + backgrounds
├── drawable-xhdpi/tv_banner.png
└── mipmap-*/ic_launcher.png
```

> **Large playlists:** the channel list is handed to the player through the in-memory
> `MyIptvApplication.playQueue`, never through an Intent extra. Real providers can expose
> tens of thousands of channels, which would blow the ~1 MB Binder transaction limit and
> make `startActivity` crash with `TransactionTooLargeException` the moment a channel is
> picked. Only the start index travels in the Intent; if the process was recreated and the
> queue is gone, the player falls back to the last-played channel stored in `Prefs`.

---

## 🔧 Customising the branding

- **App name**: `res/values/strings.xml` → `app_name`, and `AppConfig.APP_NAME`.
- **Colors**: `res/values/colors.xml` (`brand_primary`, `brand_secondary`, `bg_dark`, …).
- **Launcher icon / TV banner**: replace the PNGs in `res/mipmap-*` and
  `res/drawable-xhdpi/tv_banner.png` (320×180).

---

## 🗺️ Roadmap / extending

The data layer already tags items as `LIVE`, `VOD` or `SERIES`, so adding more content is
straightforward:
- **XMLTV EPG**: the current EPG uses Xtream's lightweight `get_short_epg`
  (now/next only). For a full guide, parse the XMLTV `xmltv.php` endpoint and render
  a timeline.
- **Series search** across seasons/episodes.
- **Search backend**: move filtering into the repository for very large playlists.
```
