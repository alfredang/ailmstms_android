# Changelog

## [1.1.0] — 2026-11-19 · versionCode 5

Built against the shared learner/trainer API (`/api/mobile/*`). Not submitted to Google Play yet.

### Changed
- Renamed to **TIA TMS** (launcher label and all headings). Package, signing key and Firebase project are unchanged.
- New blue theme: brand blue `#0A3D8F` with a navy `#051C47` header; a lighter blue in dark mode. The academy's own colour is used after sign-in only when it passes a text-contrast check.
- Source split into `data/`, `notify/` and `ui/` packages.

### Added
- Light, dark or system theme, saved to the user's profile and applied at launch.
- Sign in with a password as well as an email code, with a required password change when the academy demands one. Change password from Account.
- Class detail screen: dates, venue, status, join-online link, sessions with attendance, courseware, assessments, feedback and TRAQOM.
- Courseware opens inside the app when it is a PDF (Google Drive, Slides and Docs links are converted; up to 60 MB), with zoom, a page indicator and Open in browser. Anything else opens in the browser.
- Tapping a session on Home or the calendar opens its class.
- Learner: scan the class QR code to check in (Google code scanner, no camera permission), SSG e-attendance link, submit assessment files with upload progress, remove unsigned files, native course feedback form.
- Trainer: per-session check-in QR code, manual Present/Absent attendance with an absence reason, SSG attendance QR link, learner results (Competent / Not Yet Competent) with confirmation.
- Edit profile: name, phone, company and employment status (learner), LinkedIn (trainer).
- Local class alerts 24 hours before each session and at 08:00 Singapore time on the day, alongside the existing push reminders. Cancelled on sign-out or when reminders are off.
- The demo covers every new screen with fictional data and makes no network calls.

### Fixed
- Sessions whose date the server sends as `20261116` are no longer dropped from the calendar.

## [1.0.3] — 2026-09-07 · versionCode 4

- Replaced the icon with the final user-supplied company T logo and TIA LMS title.
- Submitted version 1.0.3 (4) to Production review for Singapore on 7 September 2026; supersedes the earlier Alpha submission.

## [1.0.2] — 2026-09-07 · versionCode 3

- Updated the launcher icon to the supplied blue TIA LMS company artwork.
- Renamed the app and its visible headings to TIA LMS.
- Submitted version 1.0.2 (3) and updated Play listing to Alpha review on 7 September 2026.

## [1.0.1] — 2026-09-07 · versionCode 2

### Added
- Demo reminder preview lets reviewers test notification permissions and opening the calendar without OTP or private learner data.

### Play submission
- Submitted to Google Play review on 7 September 2026: Alpha closed testing, All Testers, Singapore; versionCode 2. Console shows Changes in review; automated quick checks running.

## [1.0.0] — 2026-09-07 · versionCode 1

### Added
- Email OTP sign-in for existing learner and trainer accounts.
- Personal courseware and a Singapore-time class calendar.
- Optional reminders three days and one day before each class.
- Learner/trainer role switching, privacy links and feedback.
- A public demo with fictional courses for exploring the app.

### Play submission
- Initial review submission pending completion of live validation and Play Console setup.
