#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/tests/out"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/MacPolicy.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/AllowListSyncPlan.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/DeviceInventory.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/DeviceControlStatus.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/DevicePresenceTracker.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/RouterUrl.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/AuthAttemptPolicy.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/GuardRecoveryPolicy.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/AuthErrorLabel.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/ParentalControlProbe.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/TrafficFieldNames.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/LiveTrafficStatus.java" \
  "$ROOT/tests/MacPolicyTest.java" \
  "$ROOT/tests/AllowListSyncPlanTest.java" \
  "$ROOT/tests/DeviceInventoryTest.java" \
  "$ROOT/tests/DeviceControlStatusTest.java" \
  "$ROOT/tests/DevicePresenceTrackerTest.java" \
  "$ROOT/tests/RouterUrlTest.java" \
  "$ROOT/tests/AuthAttemptPolicyTest.java" \
  "$ROOT/tests/GuardRecoveryPolicyTest.java" \
  "$ROOT/tests/AuthErrorLabelTest.java" \
  "$ROOT/tests/ParentalControlProbeTest.java" \
  "$ROOT/tests/TrafficFieldNamesTest.java" \
  "$ROOT/tests/LiveTrafficStatusTest.java"
java -cp "$OUT" MacPolicyTest
java -cp "$OUT" AllowListSyncPlanTest
java -cp "$OUT" DeviceInventoryTest
java -cp "$OUT" DeviceControlStatusTest
java -cp "$OUT" DevicePresenceTrackerTest
java -cp "$OUT" RouterUrlTest

java -cp "$OUT" AuthAttemptPolicyTest
java -cp "$OUT" GuardRecoveryPolicyTest
java -cp "$OUT" AuthErrorLabelTest
java -cp "$OUT" ParentalControlProbeTest
java -cp "$OUT" TrafficFieldNamesTest
java -cp "$OUT" LiveTrafficStatusTest
sh "$ROOT/tests/check_ui_structure.sh"
