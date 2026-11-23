# Changelog

## [1.3.1] — 2026-11-23 · versionCode 8

### Added
- Signatures are drawn on the phone each time a learner or trainer signs the Assessment Summary Record.
- Trainers: post class announcements with a photo or file; an Assessment Grading roster with WA / PP submission ticks, a TRAQOM tick, and the assessor signature on submitted files; ACLP, DDDPL and TAEPP badges with toggles, domain expertise and skill tags; My Certificates in Account.
- Learners: certificates to view and download; a Support tab (feedback emailed to the academy, email, tel, WhatsApp, address) in place of Billing.
- Class status (Confirmed, Pending, Cancelled) on every class card; cancelled classes are listed.
- Developer: funding validity period, Active / Expired chip and an alert when funding ends within 3 months; no Calendar tab.

### Changed
- For trainers, Assessment Summary Record comes after Assessment Grading.

## [1.3.0] — 2026-11-23 · versionCode 7

### Added
- Class page in the website's order and names, laid out for the phone: a summary card (course code, run, class type and status, dates, venue with Google Maps, trainer, Join Google Meet), then collapsible sections that start closed.
- Course Details with funding validity (Valid/Expired) and Training, Assessment and Total hours.
- E-Attendance shows the Digital Attendance ID; trainers get the SSG QR Attendance and SSG E-Attendance List links.
- SWDA Enrolment and Course Feedback open their forms directly.
- Trainers: a Publish switch for each assessment, and the Post Course Feedback Summary (averages, stars and comments).
- Assessment Summary Record e-signing: learners sign as candidate, trainers sign as assessor (one by one or all at once), with a finger-drawn signature saved once.
- Owner "View as" switch (Learner, Trainer, Developer, Admin); admin sign-in shows Admin.
- Admin: Current and Upcoming classes, the academy calendar, the course catalogue, and WhatsApp a trainer about a class.
- Developer: the course catalogue.

### Changed
- Class cards show the class address and no longer show the teaching-kit ring.
- Learner-only sections (Course Feedback & TRAQOM Survey, Briefing on Assessment) are hidden from trainers.

## [1.2.0] — 2026-11-21 · versionCode 6

### Added
- Scan a quiz QR code to take class quizzes in the app. The trainer's quiz QR code carries `https://lms-tms.tertiaryinfotech.com/quiz/<courseId>/<quizId>`; with the app installed the link opens the quiz in the app (Android App Link, `autoVerify`), otherwise it opens the web quiz page.
- Quiz screen: one question at a time, pick one option, Next, Submit on the last question, then the score with every question marked and the correct option shown. Retake and Done. A learner who has taken the quiz before sees the last score first, with Retake.
- A quiz link that arrives while signed out is kept: the normal sign-in is shown and the quiz opens right after sign-in (also after a required password change, and after a session that had expired).
- The in-app "Scan class QR" scanner also opens a quiz when the code is a quiz QR code.
- Demo: "Take the sample quiz" on the learner's class, and any quiz link opens the sample quiz. No network calls.

### Changed
- The main activity is `singleTop`, so a link or reminder opened while the app is running goes to the open app instead of starting a second copy.

### Notes
- Uses the shared routes `GET /api/courses/quiz` and `POST /api/courses/submit-quiz` with the existing session token. The score is always the server's.
- The link opens the app without asking only after the server's `/.well-known/assetlinks.json` lists this app's signing certificates (Play App Signing key and upload key; fingerprints in `store/release-status.md`).

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
