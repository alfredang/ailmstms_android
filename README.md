# TIA TMS — native Android

<p><a href="https://play.google.com/store/apps/details?id=com.tertiaryinfotech.ailmstms"><img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80"></a></p>

Kotlin and Jetpack Compose companion for Tertiary Infotech Academy learners and trainers.

[![Download from Google Play Store](https://img.shields.io/badge/Download_from-Google_Play_Store-414141?logo=googleplay&logoColor=white)](https://play.google.com/store/apps/details?id=com.tertiaryinfotech.ailmstms)

- Sign in with an email code or a password (existing accounts only), with encrypted local session storage and a forced password change when the academy requires one.
- Class detail for every course: dates, venue, status, join-online link, sessions, courseware, assessments, feedback and TRAQOM.
- Courseware opens inside the app when it is a PDF (Google Drive, Slides and Docs links included), with zoom and a page indicator; anything else opens in the browser.
- Monthly class calendar in Singapore time; tapping a session opens its class.
- Learners: scan the class QR code to check in, open SSG e-attendance, submit assessment files, send course feedback.
- Trainers: show a session's check-in QR code, mark attendance by hand, record Competent / Not Yet Competent.
- Profile editing and a light, dark or system theme saved to the profile.
- Push reminders three days and one day before each class, plus local alerts 24 hours before and at 08:00 on the day.
- Learner/trainer switching where both roles are assigned; no administrative or finance UI.
- Clearly labelled fictional demo covering every screen, with no network calls.

## App preview

<img src="screenshot.png" alt="TIA TMS class screen in the demo" width="270">

## Build

Requires JDK 17+, Android SDK 36 and the Gradle wrapper.

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
ANDROID_SERIAL=emulator-5554 scripts/run-ui-tests.sh   # AppFlowTest + screenshots into store/screenshots/files
./gradlew :app:bundleRelease
```

Use `local.properties` for the SDK path. Release signing uses the ignored
`keystore.properties` and `keystore/upload.jks`; the upload key is backed up outside
this project. Add the Firebase project's `google-services.json` to `app/` before
building a distribution bundle. Server credentials must never be placed in the app.

## Source layout

`app/src/main/java/com/tertiaryinfotech/ailmstms/`

| Path | Contents |
|---|---|
| `MainActivity.kt`, `LmsViewModel.kt`, `Push.kt` | Activity, app state and navigation, application class and FCM service |
| `data/` | Models, `Repository` (HTTP API), `DemoApi` (fictional data), session store, link rules, PDF download, brand colour check |
| `notify/` | Local class alerts: pure planning (`AlertPlanner`) and WorkManager scheduling (`ClassAlerts`) |
| `ui/` | Theme and one file per screen: sign-in, tabs, class, PDF viewer, attendance, assessments, feedback, profile |

## Backend

API origin: https://lms-tms.tertiaryinfotech.com

Sign-in by email code, the dashboard and device registration use the `/api/android/*` routes.
Everything added in 1.1.0 (password sign-in, profile, class detail, attendance, assessments,
feedback) uses the shared `/api/mobile/*` routes, documented in `docs/mobile-app-api.md` of the
backend repository; the same session token works on both. Assessment files are uploaded to the
academy's Google Drive through `/api/upload/google-drive`. **Release 1.1.0 only after those
routes are deployed**: without them the app still signs in and lists classes, but class detail
falls back to the basic dashboard copy.

The server owns push reminder scheduling and rechecks current assignments, enrollments and
account/session status. A `401` from any route signs the user out.

Courseware is downloaded without the session token (the hosts are third parties) into the
app's cache, only over https, only up to 60 MB and only when the file starts with `%PDF`.
Calendar insertion opens the user's calendar editor without calendar-read permission. The QR
scanner is Google Play services' code scanner, so the app has no camera permission; the file
picker is the system document picker, so it has no storage permission. Local alerts use
WorkManager (no exact-alarm permission) and can arrive a few minutes late.

## Skills imported

All four Android skills from the Codex user, shared user and Claude user roots are
preserved in `.skills/{codex,user,claude}/`. `skills-import.json` records their hashes.
Includes design, Play submission, automatic release and Feedback/About guidance.

## Release

Package: `com.tertiaryinfotech.ailmstms` · version 1.1.0 (5) · min SDK 26 · target SDK 36.
Store copy and assets are in `store/`; release notes are in `CHANGELOG.md` and
`distribution/whatsnew/`. See `store/release-status.md` for verified submission state.

Developed by Tertiary Infotech Academy Pte. Ltd.
