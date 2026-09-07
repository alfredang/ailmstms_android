# TIA LMS Android release status

Package: `com.tertiaryinfotech.ailmstms`
Version: 1.0.3 (4)
Company Play account: sales@tertiarycourses.com.sg
Developer: Tertiary Infotech Academy Pte Ltd (6132721113732535566)

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
