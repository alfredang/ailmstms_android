# AI LMS TMS Android

This is a native Kotlin / Jetpack Compose app for existing learners and trainers only.
The verified production origin is `https://lms-tms.tertiaryinfotech.com`.

Use the imported Android skills in `.agents/skills/` (Codex) or `.claude/skills/`
(Claude). Source copies and checksums are preserved in `.skills/` and
`skills-import.json`. Do not expose administrative, finance or provider functions.

Never commit signing keys, `keystore.properties`, Firebase server credentials,
`.env` files, or `app/google-services.json`. Release keys are backed up outside
this repository. Firebase mobile configuration is intentionally separate from
server sender credentials.

The `backend/` directory is a separate, ignored AI-LMS-TMS repository checkout.
Coordinate backend changes with the iOS sibling at `../../iOS/ailmstmsapp` before
pushing or deploying; both apps share production. Read that repository's
instructions and verify the tenant before any deployment.

Verify meaningful changes with model tests, Android lint, and the emulator flow
in `AppFlowTest`. Use `CHANGELOG.md` for Play release notes. Do not claim Play
submission until the console shows the release in review. See
`store/release-status.md` for the latest evidence and outstanding gates.
