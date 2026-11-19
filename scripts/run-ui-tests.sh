#!/bin/zsh
set -eu
# Runs AppFlowTest on one emulator and copies its screenshots into store/screenshots/files.
# Usage: ANDROID_SERIAL=emulator-5570 scripts/run-ui-tests.sh
# The display is set to 1080x1920 for Play-sized screenshots and restored afterwards.
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PACKAGE=com.tertiaryinfotech.ailmstms
"$ADB" wait-for-device
for attempt in {1..120}; do
  if [[ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" == "1" ]]; then
    "$ADB" shell wm size 1080x1920
    trap '"$ADB" shell wm size reset' EXIT
    ./gradlew :app:installDebug :app:installDebugAndroidTest --max-workers=2 -q
    "$ADB" shell rm -rf "/sdcard/Android/data/$PACKAGE/files"
    result=$("$ADB" shell am instrument -w "$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner")
    print -r -- "$result"
    mkdir -p store/screenshots/files
    for name in $("$ADB" shell ls "/sdcard/Android/data/$PACKAGE/files" | tr -d '\r'); do
      "$ADB" pull "/sdcard/Android/data/$PACKAGE/files/$name" "store/screenshots/files/$name" > /dev/null
    done
    [[ "$result" == *"OK (1 test)"* ]]
    exit $?
  fi
  sleep 2
done
print -u2 'Emulator did not finish booting. No UI test pass is claimed.'
exit 1
