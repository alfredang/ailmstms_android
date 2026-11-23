# TIA TMS Android release status


## Production 1.3.1 (8) — 23 November 2026

- Committed through the Android Publisher API with the shared service account (edit 09934470016067432438): bundle versionCode 8 (SHA-256 `977c1d8989cbae45…`), production release "1.3.1 (8)" status completed, en-US release notes from `distribution/whatsnew/`. Validated before commit.
- Read-back after commit: production = 1.3.1 (8) completed; alpha unchanged at 1.0.3 (4). Google review of the update is pending. Evidence: `store/qa-1.3.1-publish.txt`.
- Ownership on the corporate account was taken from this file's earlier record (developer 6132721113732535566); the console was not signed in during this session.

## Production 1.3.0 (7) — 23 November 2026

- Committed through the Android Publisher API with the shared service account (edit 04231702214405640760): bundle versionCode 7 (SHA-256 `f49b8a9a37a31eac…`), production release "1.3.0 (7)" status completed, en-US release notes from `distribution/whatsnew/`. Validated before commit.
- Read-back after commit: production = 1.3.0 (7) completed; alpha unchanged at 1.0.3 (4). Google review of the update is pending. Evidence: `store/qa-1.3.0-publish.txt`.
- Ownership on the corporate account was taken from this file's earlier record (developer 6132721113732535566); the console was not signed in during this session.

## Production 1.2.0 (6) — 21 November 2026

- Adds class quizzes opened from the trainer's quiz QR code (Android App Link for `https://lms-tms.tertiaryinfotech.com/quiz/<courseId>/<quizId>`); see `CHANGELOG.md`.
- Committed through the Android Publisher API with the shared service account (edit 17962943955624144722): bundle versionCode 6 (SHA-256 `ca6dc9da…825f2f49`), production release "1.2.0 (6)" status completed, en-US release notes from `distribution/whatsnew/`. The edit was validated in a dry run first (edit 16726226703100140310, discarded).
- Read-back after commit: production = 1.2.0 (6) completed; alpha unchanged at 1.0.3 (4). Google review of the update is pending; public availability is not yet confirmed. Evidence: `store/qa-1.2.0-publish.txt`.
- Ownership on the corporate account was taken from this file's earlier record (developer 6132721113732535566); the console was not opened in this session.
- Verified before release: 67 unit tests (0 failures), lint 0 errors (21 warnings), `AppFlowTest` OK on an Android 16 emulator (TCLMS_Phone, demo mode) including the quiz link while signed out, the quiz, the result and Retake. The link was also opened with `adb shell am start -a android.intent.action.VIEW -d …/quiz/<uuid>/<id>`: cold start signed out (sign-in shown, quiz opened after entering the demo) and with the app already open (delivered to the running activity).
- Not verified: the quiz against production with a real learner account (no test account in this repository; the request and response handling is covered by `QuizTest` against a mock server, and the unauthenticated route answers 401), and automatic link verification. `https://lms-tms.tertiaryinfotech.com/.well-known/assetlinks.json` returned 404 `{"success":false,"error":"Not configured"}` at release time, and the emulator reported domain state 1024 (not verified). Until the server lists the certificates below the link opens the browser (or a chooser), not the app.
- Signing certificates (SHA-256) for `assetlinks.json`:
  - Play App Signing key (what Play-installed copies are signed with; Publisher API `generatedApks` for versionCodes 5 and 6): `F3:2D:07:F9:0D:F2:F7:B9:62:0E:02:B7:6E:1A:3F:E3:47:7E:A6:16:97:4E:8E:A3:AF:F9:BD:56:DD:10:D7:E1`
  - Upload key (`keystore/upload.jks`, alias `ailmstms`; locally installed release builds): `B3:3F:68:00:9F:DF:1A:39:49:F4:D6:98:C6:50:F5:91:EF:B0:E9:19:EA:5E:24:EA:1A:FA:85:01:91:F7:8D:36`
- Data safety form still not updated (website-only): the 1.1.0 items in `listing.md` plus quiz answers and scores.

## Production 1.1.0 (5) — 19 November 2026

- Committed through the Android Publisher API with the shared service account (edit 17972305667341637043): bundle versionCode 5, production release "1.1.0 (5)" status completed, listing title TIA TMS, new icon and 8 phone screenshots.
- Read-back after commit: production = 1.1.0 (5); listing title = TIA TMS. Google review of the update is pending; public availability is not yet confirmed.
- Ownership on the corporate account was taken from this file's earlier record (developer 6132721113732535566); the console was not opened in this session.
- Data safety form not updated for the new data types listed in `listing.md` (website-only).

## 1.1.0 (5) — built 19 November 2026, NOT submitted

- Renamed to TIA TMS with the blue theme; class detail, in-app PDF courseware, attendance, assessments, feedback, profile, password sign-in and local class alerts added (see `CHANGELOG.md`).
- Signed bundle built locally: `app/build/outputs/bundle/release/app-release.aab`.
- Verified: unit tests, lint (0 errors) and `AppFlowTest` on an Android 16 emulator in demo mode; screenshots refreshed in `screenshots/files/`.
- Not verified against production: the `/api/mobile/*` routes this version uses were not deployed when it was built. Deploy the backend first, test with a real learner and trainer account, then submit.
- Before submitting: update the Play listing name/description from `listing.md` and review the Data safety form (new data types listed there).

The sections below record earlier releases under the previous name, TIA LMS.

Package: `com.tertiaryinfotech.ailmstms`
Latest on the production track: 1.2.0 (6)
Company Play account: sales@tertiarycourses.com.sg
Developer: Tertiary Infotech Academy Pte Ltd (6132721113732535566)

## Production submission — 7 September 2026

- User requested production directly under the company account.
- Production release 1.0.3 (4) created from the existing verified bundle; Singapore availability selected.
- Withdrew the previous review and excluded the Alpha release using Save for later. Closed-testing release is not part of this submission.
- Submitted five changes: production full rollout, Singapore availability, app name, description and supplied icon. Console confirmed five changes sent for review and Production under Changes in review.
- Evidence: `qa/1.0.3/production-review.yml` and `qa/1.0.3/production-track.yml`. Awaiting Google approval; not yet publicly available in production.

## Final company icon — 7 September 2026

- Used the exact final attachment: `branding/tia-company-approved.png`, resized only for the launcher and Play listing.
- Version 1.0.3 (4): signed bundle built, four unit tests and AppFlowTest pass, lint zero errors (20 warnings).
- Play review restarted with the final icon and version 1.0.3 (4). Console confirms Changes in review and 2 changes sent for review. Evidence: `qa/1.0.3/play-review.yml`.
- App remains named TIA LMS with the same package and company account.

## Branding update — 7 September 2026

- Replaced the launcher and Play icon with the exact user-supplied blue TIA LMS artwork (resized to 512 px).
- Renamed Android app headings, launcher label and Play listing to TIA LMS. Package ID unchanged.
- Signed version 1.0.2 (3) built; four unit tests, lint and AppFlowTest passed.
- Google Play confirmed four changes sent for review: Alpha 1.0.2 (3), app name, description and icon. Evidence: `qa/1.0.2/play-review.yml`.
- Previous 1.0.1 (2) is available to selected testers; this update awaits Google review.

## Verified

- All Android skills imported from Codex, shared user and Claude roots.
- Native Kotlin / Compose source, OTP login, learner/trainer roles, courseware, calendar, and FCM client implemented.
- Four Android model/security unit tests pass.
- Backend integration tests pass: role scope, learner material privacy, OTP replay, withdrawal, device lifecycle, Singapore dates and reminder deduplication.
- Local API checks: unauthenticated dashboard 401; malformed login 400.
- Firebase project/app and server-only FCM sender configured. FCM validation-only request returned HTTP 200; no real push was sent by that check.
- Android signing key generated and backed up privately outside the repository.
- User approved Google Program Policies and US export declarations; accepted and app created in the company account. Play app ID: 4976046230893494126.
- Final signed AAB/APK built; jarsigner and apksigner verification passed.
- Lint: zero errors (19 warnings); 64-bit native libraries have 16 KB load alignment.
- Combined backend SHA `8d2ab4e17a1263ecc2ba276b8253237e8c777886` deployed. Android unauthenticated dashboard returns 401, malformed login 400, and six public mobile pages return 200.
- Production scheduler and Android Firebase credential presence confirmed by deployment readback.
- Final emulator test passed: login validation, demo learner home/courseware/calendar, trainer role, and sign-out. Six 1080 x 1920 screenshots captured. Demo notification test verifies notification creation.
- Actual OTP email receipt and end-to-end real-device FCM delivery have not been exercised; backend integration and FCM validation checks passed.

## Play Console preparation

- Privacy, ads, app access, government, financial, health, advertising ID, adult target audience and data safety saved.
- Education category, support contact and store listing text saved. Reviewer demo instructions include sample notification access.
- All Testers email list created with sales@tertiarycourses.com.sg and selected for Alpha closed testing; Singapore targeted (2 of 4 track setup tasks complete).
- Alpha release name and notes prepared for 1.0.1 (2). Bundle version 2 (1.0.1) uploaded through native Playwright MCP and saved to Alpha after validation.

## Submitted for review — 7 September 2026

- Native Playwright MCP uploaded version 2 (1.0.1), icon, feature graphic and five phone screenshots.
- IARC terms accepted following the user's instruction to handle the entire submission. Rating questionnaire saved: online educational content; IARC 3+, ESRB Everyone.
- Alpha release validated with no errors and two non-blocking warnings (deobfuscation and native debug symbols).
- Sent all 14 changes for review. Console confirmed “14 changes sent for review” and “Changes in review”, including Alpha 1.0.1 (2) / Start full rollout.
- Google automated quick checks are running. This is a closed-testing submission for Singapore with All Testers; it is not production approval or public availability.
- Evidence: `store/qa/play-submission.txt` and `store/qa/play-changes-in-review.png`.

**Submitted to Google Play for review. Google approval is pending.**
