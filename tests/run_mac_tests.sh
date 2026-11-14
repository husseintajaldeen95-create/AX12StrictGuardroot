#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/tests/out"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/MacPolicy.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/RouterUrl.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/AuthAttemptPolicy.java" \
  "$ROOT/app/src/main/java/com/ax12/strictguard/AuthErrorLabel.java" \
  "$ROOT/tests/MacPolicyTest.java" \
  "$ROOT/tests/RouterUrlTest.java" \
  "$ROOT/tests/AuthAttemptPolicyTest.java" \
  "$ROOT/tests/AuthErrorLabelTest.java"
java -cp "$OUT" MacPolicyTest
java -cp "$OUT" RouterUrlTest

java -cp "$OUT" AuthAttemptPolicyTest

java -cp "$OUT" AuthErrorLabelTest
