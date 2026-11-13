#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/tests/out"
javac -encoding UTF-8 -d "$ROOT/tests/out" "$ROOT/app/src/main/java/com/ax12/strictguard/MacPolicy.java" "$ROOT/tests/MacPolicyTest.java"
java -cp "$ROOT/tests/out" MacPolicyTest
