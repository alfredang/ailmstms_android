#!/bin/zsh
set -eu
# Run after other simulator/build jobs finish; a full cold boot is more reliable for QA.
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
ANDROID_SDK_TASK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
"$ANDROID_SDK_TASK_DIR/platform-tools/adb" wait-for-device
for attempt in {1..120}; do
  if [[ "$("$ANDROID_SDK_TASK_DIR/platform-tools/adb" shell getprop sys.boot_completed | tr -d '\r')" == "1" ]]; then
    ./gradlew :app:connectedDebugAndroidTest --max-workers=2
    exit $?
  fi
  sleep 2
done
print -u2 'Emulator did not finish booting. No UI test pass is claimed.'
exit 1
