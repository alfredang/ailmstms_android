# AI LMS TMS Android release status

Package: `com.tertiaryinfotech.ailmstms`
Version: 1.0.1 (2)
Company Play account: sales@tertiarycourses.com.sg
Developer: Tertiary Infotech Academy Pte Ltd (6132721113732535566)

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
- Alpha release name and notes prepared for 1.0.1 (2). Bundle upload remains pending.

## Remaining

- Separate IARC terms acceptance awaits user confirmation.
- Chrome file uploads are blocked until the ChatGPT extension has Allow access to file URLs; user confirmation requested before enabling this permission.
- Upload signed version 1.0.1 bundle, icon, feature graphic and screenshots; finish rating and release; send for review and verify Changes in review.

**Not submitted to Google Play yet.**
