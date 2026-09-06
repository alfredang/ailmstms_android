# AI LMS TMS Android release status

Package: `com.tertiaryinfotech.ailmstms`
Version: 1.0.0 (1)
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
- Play app creation form filled in the correct company account.
- Final signed AAB/APK built; jarsigner and apksigner verification passed.
- Lint: zero errors (20 warnings); 64-bit native libraries have 16 KB load alignment.
- Combined backend SHA `8d2ab4e17a1263ecc2ba276b8253237e8c777886` deployed. Android unauthenticated dashboard returns 401, malformed login 400, and six public mobile pages return 200.
- Production scheduler and Android Firebase credential presence confirmed by deployment readback.
- Final emulator test passed: login validation, demo learner home/courseware/calendar, trainer role, and sign-out. Four 1080 x 1920 screenshots visually approved.
- Actual OTP email receipt and end-to-end real-device FCM delivery have not been exercised; backend integration and FCM validation checks passed.

## Remaining


- User approval of Google Developer Program Policies and US export declarations.
- Create listing, upload bundle/screenshots, complete declarations, select All Testers and country, submit for review, and verify Changes in review.

**Not submitted to Google Play yet.**
