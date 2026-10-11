#!/bin/bash
# Compiles and runs the pure-JVM modules' tests with a bare kotlinc (no Gradle/Maven needed).
# Used by agents in sandboxes without Maven access. Gradle (`./gradlew checkAll`) remains the source of truth.
#   setup:  pip install jdk4py ; npm pack kotlin-compiler@2.4.21 && tar xzf kotlin-compiler-*.tgz -C $KT
#   usage:  KT=/path/to/kotlin-compiler/package JAVA_HOME=/path/to/jre tools/jvm-check/run.sh
set -e
R="$(cd "$(dirname "$0")/../.." && pwd)"
KT="${KT:-/tmp/kt/package}"
export JAVA_HOME="${JAVA_HOME:-/usr/local/lib/python3.11/dist-packages/jdk4py/java-runtime}"
export PATH="$JAVA_HOME/bin:$PATH"
KC="$KT/bin/kotlinc"; LIB="$KT/lib"; CO="$LIB/kotlinx-coroutines-core-jvm.jar"
OUT="${OUT:-/tmp/jvm-check-out}"; rm -rf "$OUT"; mkdir -p "$OUT"
STUBS="$R/tools/jvm-check/stubs"
echo "== compile main"
"$KC" -nowarn -cp "$CO" -d "$OUT/main.jar" "$STUBS/kxs" \
  "$R/core/protocol/src/main/kotlin" "$R/core/gamesdk/src/main/kotlin" "$R/games/hokm/core/src/main/kotlin" \
  "$R/transport/api/src/main/kotlin" "$R/core/testkit/src/main/kotlin" "$R/core/session/src/main/kotlin" \
  "$R/server/src/main/kotlin/ir/dorehami/server/rooms" 2>&1 | grep -v "^warning" || true
echo "== compile tests"
"$KC" -nowarn -Xallow-kotlin-package -cp "$OUT/main.jar:$LIB/kotlin-test.jar:$CO" -d "$OUT/test.jar" \
  "$R/core/gamesdk/src/test/kotlin" "$R/games/hokm/core/src/test/kotlin" "$R/core/session/src/test/kotlin" \
  "$R/transport/api/src/test/kotlin" "$R/server/src/test/kotlin/ir/dorehami/server/rooms" "$STUBS/junit" 2>&1 | grep -v "^warning" || true
echo "== run"
java -cp "$OUT/main.jar:$OUT/test.jar:$LIB/kotlin-test.jar:$LIB/kotlin-stdlib.jar:$LIB/kotlin-reflect.jar:$CO" sandbox.RunnerKt "$OUT/test.jar"
