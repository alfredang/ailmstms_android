# Instrumented test environment

Android 16.1 Google Play ARM64 emulator, API 36.1.
Espresso 3.7.0 fixes the removed reflective InputManager.getInstance access on this image.
The previous transitive version failed during runner initialization before executing app assertions.
Reference: https://developer.android.com/jetpack/androidx/releases/test

Instrumentation dependencies only; production runtime is unchanged by this adjustment.

Final instrumentation run: PASS (one end-to-end demo navigation test).
Four approved screenshots at 1080 x 1920: login, courseware, calendar, trainer account.
The home capture caught the prior display frame and was excluded from store assets.
Temporary emulator display and storage threshold settings were restored; emulator shut down.
