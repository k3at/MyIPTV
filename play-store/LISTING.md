# MyIPTV — publishing pack

Everything needed to ship the app, plus an honest look at **where** to ship it.

---

## 1. Where to distribute — the alternatives

| Option | Cost | Review | Risk for an IPTV player | Verdict |
|--------|------|--------|------------------------|---------|
| **GitHub Releases + Obtainium** | Free | None | None | ⭐ Best fit |
| **IzzyOnDroid** (F-Droid-compatible repo) | Free | Light, automated | Low | ⭐ Good addition |
| **Self-hosted APK + your own domain** | Domain cost | None | None | ⭐ Good |
| F-Droid (main repo) | Free | Strict | Medium — they have pulled IPTV apps before ("facilitating copyright infringement") | ⚠️ Uncertain |
| **Google Play** | $25 once | Heavy | **High** — the biggest rejection/removal risk of any option | ⚠️ Optional |
| Amazon Appstore (Fire TV) | Free | Heavy | High, same reason | ⚠️ |
| Samsung Galaxy Store / Huawei AppGallery | Free | Medium | High, same reason | ⚠️ |
| Aptoide / APKPure / APKMirror | Free | None/light | Low | 😐 Reach is small |

### Why Play is risky for *this* app

MyIPTV is a **neutral player**: it ships no content and loads a playlist the user
provides. That is the friendlier end of the spectrum — plenty of M3U players are
live on Play today. But Google's *Intellectual Property* and *Deceptive Behavior*
policies put the burden on you to show you have rights to the content the app
reaches, and "it's just a player" is not a guaranteed defence. Rejections cost
time; repeated ones cost account strikes.

Two more Play-specific hurdles:

1. **New personal developer accounts cannot publish to production straight away.**
   You must run a **closed test with ~12 testers for 14 continuous days** before
   Google unlocks production access. Organization accounts skip this but need
   business verification.
2. **Reviewers must be able to use the app.** Because MyIPTV needs your own
   playlist credentials, an empty app can look "broken" to an automated or
   under-informed reviewer. Put clear instructions in the *App access* section.

### Recommendation

Ship where it works for **users on a TV box** — they sideload anyway:

1. **GitHub Releases** as the primary channel. Tag a version, attach
   `app-release.apk`, write short release notes.
2. Tell users to install **[Obtainium](https://github.com/ImranR98/Obtainium)**
   and point it at your repo — they get automatic update notifications, which
   Play would otherwise provide.
3. Optionally submit to **IzzyOnDroid** for extra reach with a light review.
4. Treat Play as a *later* experiment, behind the 12-tester closed test. If the
   closed test gets rejected, you have lost nothing.

---

## 2. Store assets (already generated)

Re-generate any time with `play-store/generate-art.ps1` — it renders from the same
brand colours as the launcher icon, so nothing is upscaled:

| File | Size | Used for |
|------|------|----------|
| `icon-512.png` | 512×512 | Play: app icon; other stores: master icon |
| `feature-graphic-1024x500.png` | 1024×500 | Play: feature graphic |
| `feature-graphic-1024x500-safezone.png` | 1024×500 | Same, content pulled inward (safer if cropped) |
| `tv-banner-320x180.png` | 320×180 | Play: Android TV banner |
| `screenshots/` | see below | All stores |

Generator script: `%TEMP%\opencode\gen-store-art.ps1` (re-run any time; it renders
from the same brand colours as the launcher icon, so nothing is upscaled).

### Screenshot requirements

| Form factor | Size | Minimum count |
|-------------|------|---------------|
| Phone | 16:9 or 9:16, 320–3840 px per side | 2 (Play requires 2 for phone) |
| 7" tablet | as above | 2 (only if you list tablet support) |
| Android TV | 1920×1080 **landscape** | 4 recommended for a TV listing |

Capture them from a real device or emulator:

```powershell
adb shell screencap -p /sdcard/shot.png
adb pull /sdcard/shot.png screenshots/tv-01-browse.png
```

Useful shots: channel browse grid, live player with the OSD up, settings screen,
movie detail, series/seasons screen.

---

## 3. Store listing copy

**App name** (30 chars max): `MyIPTV`
> `MyIPTV` is a generic name and several similar listings exist. If Play ever
> complains about confusion or trademark, be ready to rename (e.g. `MyIPTV Player`).
> Changing it later means a new listing, so pick carefully now.

**Short description** (80 chars max, 80 used):

```
IPTV player for your own M3U or Xtream Codes playlists. Live TV, films & series.
```

**Full description** (4000 chars max):

```
MyIPTV is a fast, clean IPTV player for Android TV, TV boxes, phones and tablets.
Bring your own playlist — the app ships with no channels, no subscriptions and no
bundled content.

BUILT FOR YOUR OWN PLAYLIST
• M3U / M3U8 playlist URL
• Xtream Codes login (host + username + password)
Type your details straight into the app — no config files, no sideloading tweaks.
Both input types are configured on screen, and the app remembers them.

LIVE TV, MOVIES AND SERIES
• Live channels with categories and a full channel list
• Movies (VOD) with posters, descriptions and a dedicated detail screen
• Series with seasons and episodes
*Series browsing and EPG require an Xtream Codes source.*

BUILT FOR THE COUCH
• Full remote-control navigation — everything is reachable with the D-pad
  (Android TV remote, TV box remote, or game controller)
■ Phone and tablet layout included in the same app: no separate download
■ Fast search across your whole playlist
■ Favourites
■ Last channel is remembered, so the app reopens where you left off

THE PLAYER

A full internal player, not a hand-off to another app:

• Play / pause, ±10 second seek, and a scrub bar with elapsed and total time
• Volume plus mute, with an on-screen level indicator
• Brightness control
■ Aspect ratio: Fit / Fill / Zoom
■ Playback speed 0.5× to 2× for movies and series (live stays 1×)
■ Previous / next channel for quick zapping
■ Now / next programme info when your provider supplies it
■ Auto-hiding controls so nothing sits over the picture

WHAT IT DOES NOT DO

• No content, channels or playlists are bundled — you supply your own
• No accounts, no sign-up, no telemetry
• Nothing is uploaded to us; your credentials stay on your device

You need your own playlist (M3U URL or Xtream Codes login) from a provider you are
entitled to use. MyIPTV is only a player and does not provide, host, or endorse any
streams.

**Keywords**: iptv, m3u, m3u8, xtream, xtream codes, iptv player, live tv, tv box,
android tv, iptv smarters alternative, iptv smarters pro, iptv player for android tv

---

## 4. Play Console questionnaire answers

### App access (App content → App access)
> The app requires a playlist the user must already own.
> Select **"All functionality is available without special access"** is *wrong* here
> — instead choose **"Some functionality is restricted"** and supply instructions
> and a working demo login, otherwise the reviewer sees an empty app and rejects it.

Recommended text:

```
On first launch the app shows a setup screen where connection details are entered.
Choose "Xtream Codes" or "M3U" and enter the provider details.

Test credentials:
  Type: Xtream Codes
  Host:  <your demo host>
  User:  <demo user>
  Pass:  <demo password>

Alternatively choose M3U and paste: <your demo playlist URL>

Then tap Save. Live TV, Movies and Series appear. Select any channel to open the
built-in player.
```

> ⚠️ You need a demo/test playlist that you are allowed to expose to a reviewer.
> If you cannot provide one, the Play path is effectively closed — another reason
> to prefer GitHub Releases.

**Data safety** — answer honestly:

| Question | Answer |
|----------|--------|
| Does your app collect or share any required user data types? | **No** |
| Is all user data encrypted in transit? | n/a (nothing collected) |
| Do you provide a way for users to request data deletion? | Yes — uninstalling, or **Settings → clear/reset** |

Rationale: playlist credentials are stored in the app's private storage and sent
only to the server address *the user themselves* enters. The developer receives
nothing. No analytics, ad or crash SDKs are included.

**Ads**: No ads.

**Target audience**: 18+ only. Do **not** opt into "Designed for Families".

**Content rating (IARC questionnaire)**: category *Video/Media player* or
*Utility*. Expect questions such as "does the app give access to unmoderated
internet content?" — answer yes, and you will likely land on *Mature / PEGI 16–18*
rather than *Everyone*. That is correct and preferable to being removed later.

**Government app**: No. **Financial features**: No. **Health**: No. **News**: No.

**Privacy policy URL**: required. Host `privacy-policy.md` publicly — GitHub Pages
is free:

1. Create a repo (can be private-content but Pages must be public).
2. Put `privacy-policy.md` in it, enable **Settings → Pages**.
3. Use the resulting URL, e.g. `https://<user>.github.io/myiptv-privacy/`.

---

## 4b. Play Console submission checklist

1. Register the developer account (the $25 fee must be paid before anything uploads).
2. **Play App Signing**: accept it. You keep the *upload* key (already created at
   `keystore/upload-keystore.jks`); Google holds the *app signing* key.
3. Create the app: default language, name `MyIPTV`, app/game = App, free.
4. Upload `app-release.aab` to **Closed testing** first — not production. Personal
   accounts are locked out of production until the 12-tester / 14-day rule is met.
5. Fill in Store listing (copy above, assets above).
6. Complete App content: privacy policy, ads, content rating, target audience,
   data safety, app access instructions.
7. Add 12+ testers (email list), publish the closed test, wait 14 continuous days.
8. Apply for production access, then promote the release.
9. Every future update: bump `versionCode` in `app/build.gradle.kts`, rebuild the
   bundle, upload. **Never** lose the upload keystore.

---

## 5. Android TV notes

* The manifest already declares a Leanback launcher intent and a 320×180 banner,
  and both are `required="false"`, so the same artefact installs on phones and TV.
* Play treats Android TV as a separate form factor in the listing: you must tick
  the TV box and upload TV screenshots + banner, otherwise TV users cannot find it.
* A physical **D-pad remote is required** for a good TV experience — the app is
  fully D-pad navigable, verified on a 720p TV emulator.

---

## 6. Honest summary

* **Technically** the app is ready: real upload key, minified release, AAB, signed
  APK, store artwork, listing copy, privacy policy.
* **Commercially**, GitHub Releases + Obtainium gets it into users' hands today
  with zero cost and zero rejection risk.
* **Play** is achievable but is a moderation gamble on top of a mandatory
  12-tester/14-day ramp. Keep the APK channel running regardless.
