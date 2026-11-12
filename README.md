# Daily Verse

Android app that puts a new Bible verse (King James Version) on your lock screen every morning.

## Features
- 135 well-known verses, a new one each morning at the time you choose (default 06:00)
- Live preview, Previous / Next, "Set on lock screen now"
- 6 background styles, or Auto (changes daily)
- Option to also set the home screen
- Share the verse as text
- Survives restarts; catches up if the phone was off at the scheduled time

## Get the APK
Every push to GitHub builds the app automatically (see `.github/workflows/build.yml`).
When the build finishes, open the repo's **Releases → latest** on your phone and download `DailyVerse.apk`.

Or build locally: open the folder in Android Studio and choose *Build → Build APK(s)*,
or run `./gradlew assembleRelease` (output: `app/build/outputs/apk/release/app-release.apk`).

## Installing on the phone
1. Download `DailyVerse.apk` and tap it.
2. Allow "Install unknown apps" for your browser/file manager when asked.
3. Open Daily Verse and tap **Set on lock screen now**.

Tip: on Xiaomi / Huawei / some Samsung phones, set battery usage for Daily Verse to
"No restrictions" so the morning change isn't blocked. If a morning is missed, the app
catches up next time it opens or the phone restarts.
