# Releasing MyIPTV

This is the workflow for shipping the app through **GitHub Releases** — no store,
no review, no fees. It assumes you have never set up Git on this machine.

Everything below is one-time except section 3, which you repeat for every release.

---

## 0. Prerequisites

* **Git** installed. It is not on this machine yet:

  ```powershell
  winget install --id Git.Git -e --source winget
  ```

  Restart your terminal afterwards so `git` lands on `PATH`.
* A **GitHub account**.
* Your **keystore** (`keystore/upload-keystore.jks`) and its password — see the
  main README. This file is git-ignored and must never be committed.

Set your Git identity once:

```powershell
git config --global user.name  "Your Name"
git config --global user.email "you@example.com"
```

---

## 1. One-time: create the repository

Create an empty repo on GitHub (no README, no .gitignore — the project already has
one), then from the project root:

> ✅ Already done for this project: `https://github.com/k3at/MyIPTV` exists and
> `origin` is configured. Skip to §2 unless you are setting up a fresh clone.

```powershell
cd "C:\Users\k3ats\Documents\Default Project\MyIPTV"
git init -b main
git add .
git status          # confirm keystore/ and keystore.properties are NOT listed
git commit -m "MyIPTV 1.0.0"
git remote add origin https://github.com/k3at/MyIPTV.git
git push -u origin main
```

> ⚠️ **Check `git status` output before committing.** `keystore.properties` and
> `keystore/` must not appear. They are in `.gitignore`, but verify — publishing
> your signing key would let anyone ship a fake update.

Two housekeeping tasks:

1. Replace `USER/REPO` in `CHANGELOG.md` with your actual repo path.
2. Ensure the Gradle wrapper stays executable on Linux CI (the workflow runs
   `chmod +x gradlew` anyway, but recording the bit is cleaner):

   ```powershell
   git update-index --chmod=+x gradlew
   git commit -m "chore: make gradlew executable"
   ```

---

## 2. One-time: add the signing secrets

The GitHub runner has no keystore, so `release.yml` restores it from repository
secrets.

### Preferred: let a script upload them

`KEYSTORE_BASE64` is **3640 characters**, which is slow and error-prone to paste
by hand — a truncated paste fails the release with a confusing error. This script
uploads all four values through the GitHub CLI instead:

```powershell
winget install --id GitHub.cli -e     # one time
gh auth login                         # one time; sign in through the browser
powershell -NoProfile -ExecutionPolicy Bypass -File tools\set-github-secrets.ps1
```

It reads the passwords from `keystore.properties` and the keystore from
`keystore/upload-keystore.jks`, prints only the *length* of each value (never the
value), and lists the secrets at the end so you can confirm there are exactly four.
It also deletes the unused `K3AT` secret if it is still around.

### Manual alternative

**Run the helper — it prints every value you need:**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\export-keystore-for-ci.ps1
```

It reads the passwords out of `keystore.properties`, copies the base64 keystore to
your clipboard, and prints a table like this:

```
 Repository secrets for .github/workflows/release.yml
 Settings -> Secrets and variables -> Actions -> New repository secret

  NAME               VALUE
  ---------------- -----
  KEYSTORE_BASE64    (on your clipboard)
  KEYSTORE_PASSWORD  …
  KEY_ALIAS          upload
  KEY_PASSWORD       …
```

Then in GitHub: **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | already on your clipboard — just paste it |
| `KEYSTORE_PASSWORD` | printed by the helper |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | the same password |

Add `-Print` if you also want the base64 echoed to the screen (it is ~3 KB, so it
goes to the clipboard by default rather than scrolling past).

> ℹ️ The keystore's base64 is **3640 characters** long and decodes to a
> **2728-byte** JKS. If the release job complains about `KEYSTORE_BASE64`, read its
> error annotation: it reports the character count it received, so you can tell a
> truncated paste from a wrong password without reading the whole log.

That command can be re-run any time; it lives in the project as
`tools/export-keystore-for-ci.ps1`.

If any secret is missing the release job **fails on purpose** rather than
publishing a build signed with the public debug key.

---

## 3. Every release

### a. Bump the version

In `app/build.gradle.kts`, increase **both** values:

```kotlin
versionCode = 2          // must always go up
versionName = "1.0.1"    // user-visible
```

Android only accepts an update whose `versionCode` is higher than the installed
app, so this step is not optional.

### b. Update `CHANGELOG.md`

Move anything useful out of `[Unreleased]` into a new `## [1.0.1] — YYYY-MM-DD`
section.

### c. Commit and tag

The tag must be `v` + `versionName` — the workflow checks this and fails if they
disagree.

```powershell
git add .
git commit -m "Release 1.0.1"
git tag v1.0.1
git push origin main
git push origin v1.0.1
```

Pushing the tag starts the **Release** workflow. Watch it under the repo's
**Actions** tab. When it finishes, a release appears on the Releases page with:

* `MyIPTV-1.0.1.apk` — signed with your upload key, installable directly.
* `MyIPTV-1.0.1.aab` — kept for a future Play/Amazon submission.
* Release notes generated from the commit log.

You can also re-run it manually from **Actions → Release → Run workflow** by
entering an existing tag.

---

## 4. How users install and update

### Manual install

Download `MyIPTV-x.y.z.apk` from the Releases page and open it. On Android TV and
Fire TV this usually means:

```powershell
adb install -r MyIPTV-1.0.1.apk
```

or installing **Downloader** from the store and entering the direct release URL.

### Automatic updates with Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) watches a GitHub repo and
notifies users when a new APK is published — the closest thing to Play Store
updates outside Play.

Give users these steps:

1. Install Obtainium (it has a GitHub release of its own).
2. **Add App →** paste your repo URL, e.g. `https://github.com/k3at/MyIPTV`.
3. Leave the defaults (source *GitHub*, it auto-detects the `.apk` asset) and
   confirm.
4. When a new release is published, Obtainium offers the update.

Requirements for this to work smoothly — all already handled:

* A **stable signing key** across releases (done: `keystore/upload-keystore.jks`).
* `versionCode` increasing each release (§3a).
* An `.apk` asset attached to each GitHub Release (done by `release.yml`).

Useful settings for users with a specific device:

| Field | Value |
|-------|-------|
| APK filter regex | `MyIPTV-.*\.apk$` |
| Version extraction | default (reads `versionName` from the APK) |
| Allow prereleases | off unless you publish them deliberately |

---

## 5. Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| `Missing repository secrets: KEYSTORE_BASE64` | Add the secrets from §2. |
| `KEYSTORE_BASE64: 19 characters pasted … Expected 3640` | The secret holds the wrong value — usually a truncated or partial paste. Re-upload it with §2. |
| `base64 must be a multiple of 4 characters long` | Same cause: the pasted value is not the full base64. |
| `KEYSTORE_BASE64 holds N character(s) outside the base64 alphabet` | The value was copied as wrapped console text, from a CRLF file, or from a `-----BEGIN …-----` block. Paste the raw value only. |
| `keytool cannot open the restored keystore with KEYSTORE_PASSWORD` | `KEYSTORE_PASSWORD` does not match the keystore in `KEYSTORE_BASE64`. Use §2's script so both come from the same source. |
| `the restored keystore has no entry under the alias in KEY_ALIAS` | `KEY_ALIAS` is wrong. It is `upload` for this project. |
| `Tag 'v1.0.1' does not match versionName '1.0.0'` | You tagged without bumping `versionName`. Bump it, commit, delete the tag (`git push --delete origin v1.0.1`) and re-tag. |
| `/usr/bin/env: 'bash\r': No such file or directory` or `./gradlew: Permission denied` | CRLF line endings / lost exec bit. `git update-index --chmod=+x gradlew`. Also add `* text=auto eol=lf` to a `.gitattributes`. |
| Build fails: `Unsupported class file major version` or a Java-version complaint | Change `java-version` in both workflow files to `'25'` — that is what the project builds with locally. |
| `Failed to find target with hash string 'android-37'` | AGP should auto-download the platform. If not, add a step running `sdkmanager "platforms;android-37" "build-tools;36.0.0"`. |
| Users report "App not installed" on update | The new APK was signed with a different key than the installed one. Uninstall once, then install. Never lose the keystore. |

---

## 6. Optional extras

* **IzzyOnDroid** — an F-Droid-compatible repository with a light automated
  review, popular with power users. Submit by opening an issue on
  [`IzzyOnDroid/repo`](https://github.com/IzzyOnDroid/repo) pointing at your repo;
  it then builds and distributes your APK, and users get updates via any F-Droid
  client.
* **A `.gitattributes`** to force LF for shell scripts:

  ```
  * text=auto eol=lf
  *.bat text eol=crlf
  *.png binary
  *.jks binary
  ```
* **Play Store later** — the same AAB workflow output can be uploaded there. See
  `play-store/LISTING.md` for the console checklist and the policy caveats.
