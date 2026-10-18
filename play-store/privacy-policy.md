# Privacy Policy — MyIPTV

**Last updated: 19 October 2026**

This policy describes what the MyIPTV Android application ("the app") does with
your information. The short version: **the app collects nothing and sends nothing
to the developer.**

## Who we are

MyIPTV is developed by an independent developer ("we", "us"). Contact:
`<your-contact-email>`.

## What the app stores, and where

The app stores the following **on your device only**, in the app's private
storage area that other apps cannot read:

* The playlists and connection details you type in — for M3U, the playlist URL;
  for Xtream Codes, the server host, username and password.
* Your preferences: favourites, last watched channel, and player settings
  (volume, brightness, aspect ratio, playback speed).

None of this is transmitted to us. We have no servers and no way to see it.

## Where your data goes

When you play content, the app contacts **the server address you entered
yourself** — your own IPTV provider. Your playlist credentials are sent to that
server, because that is how the protocol works. That provider is not us; please
review their privacy policy separately.

* Some providers use `http://` rather than `https://`. If yours does, the traffic
  between the app and your provider is **not encrypted**, and someone on the same
  network could observe it. This is a limitation of the provider, not something
  the app can fix. Where the provider supports `https://`, use it.
* Channel logos and movie artwork are loaded directly from URLs supplied by your
  provider by the image-loading library bundled with the app.

## What the app does NOT do

* No account, sign-up, or login to us.
* No analytics, telemetry, crash reporting, advertising or tracking SDKs.
* No collection of your device identifiers, location, contacts, files or usage.
* No sharing of any data with third parties by us.
* No access to your photos, camera, microphone or contacts.

The app requests only `INTERNET`, `ACCESS_NETWORK_STATE` and `WAKE_LOCK`
permissions — to stream content, to tell you when you are offline, and to keep
the screen awake during playback.

## Backups

Android's built-in backup feature may copy the app's private data (including the
connection details you entered) to your own Google account, if you have device
backup enabled. That backup is controlled by you and lives in your account; we
have no access to it. You can disable it in Android Settings, or remove the app's
data from it by uninstalling.

## Children

The app is not directed at children. It is a media player intended for adults who
are responsible for the playlists they configure. It is not listed as a
family/children's app.

## Content responsibility

The app ships with no content, no channels and no playlists. You must supply your
own M3U playlist or Xtream Codes login from a provider you are legally entitled
to use. You are responsible for ensuring you have the right to access whatever
you configure. We do not host, provide, curate or endorse any content.

## Your control over your data

Because everything is stored locally, you are always in full control:

* **Clear connection details / playlist settings** in the app's settings screen.
* **Delete everything** by uninstalling the app, or via Android's
  *Settings → Apps → MyIPTV → Storage → Clear data*.

There is no request to make to us, because we hold nothing.

## Changes

If this policy changes, the updated version will be published at this address with
a new "last updated" date.

## Contact

Questions about this policy: `<your-contact-email>`.
