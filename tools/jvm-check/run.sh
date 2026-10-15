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
# The JVM-testable part of `games/hokm/ui` joins this helper runner (HK-013: baseline scenario +
# accessibility labels + the typed model). The list is explicit on purpose: purity here is not
# decidable by grep — `HokmLocalMatch.kt`/`HokmSessionMatch.kt` import no `androidx.*` yet extend
# the Compose `HokmTable`, and `HokmSounds.kt` references generated `R.raw` ids. Gradle
# (`./gradlew checkAll`) stays the source of truth for the whole module.
UI_MAIN="$R/games/hokm/ui/src/main/kotlin/ir/dorehami/games/hokm/ui"
UI_TEST="$R/games/hokm/ui/src/test/kotlin/ir/dorehami/games/hokm/ui"
PURE_UI=" \
  $R/core/uikit/src/main/kotlin/ir/dorehami/uikit/FaDigits.kt \
  $UI_MAIN/HokmUiModel.kt \
  $UI_MAIN/HokmSeatLayout.kt \
  $UI_MAIN/HokmMatchStats.kt \
  $UI_MAIN/HokmPacing.kt \
  $UI_MAIN/HokmSave.kt \
  $UI_MAIN/HokmA11y.kt \
  $UI_MAIN/HokmMotion.kt \
  $UI_MAIN/HokmBaselineScenario.kt \
  $UI_MAIN/HokmBaselinePlayback.kt \
  $UI_MAIN/HokmArtDirection.kt \
  $UI_MAIN/HokmTokens.kt"
PURE_UI_TESTS=" \
  $UI_TEST/HokmUiMapperTest.kt \
  $UI_TEST/HokmUiStateTest.kt \
  $UI_TEST/HokmSeatLayoutTest.kt \
  $UI_TEST/HokmMatchStatsTest.kt \
  $UI_TEST/HokmPacingTest.kt \
  $UI_TEST/HokmSaveTest.kt \
  $UI_TEST/HokmA11yTest.kt \
  $UI_TEST/HokmMotionTest.kt \
  $UI_TEST/HokmBaselineScenarioTest.kt \
  $UI_TEST/HokmBaselinePlaybackTest.kt \
  $UI_TEST/HokmArtDirectionTest.kt \
  $UI_TEST/HokmTokensTest.kt"
echo "== compile main (pure UI files: $(echo $PURE_UI | wc -w))"
"$KC" -nowarn -cp "$CO" -d "$OUT/main.jar" "$STUBS/kxs" \
  "$R/core/protocol/src/main/kotlin" "$R/core/gamesdk/src/main/kotlin" "$R/games/hokm/core/src/main/kotlin" \
  "$R/transport/api/src/main/kotlin" "$R/core/testkit/src/main/kotlin" "$R/core/session/src/main/kotlin" \
  "$R/server/src/main/kotlin/ir/dorehami/server/rooms" \
  $PURE_UI 2>&1 | grep -v "^warning" || true
echo "== compile tests"
"$KC" -nowarn -Xallow-kotlin-package -Xfriend-paths="$OUT/main.jar" -cp "$OUT/main.jar:$LIB/kotlin-test.jar:$CO" -d "$OUT/test.jar" \
  "$R/core/gamesdk/src/test/kotlin" "$R/games/hokm/core/src/test/kotlin" "$R/core/session/src/test/kotlin" \
  "$R/transport/api/src/test/kotlin" "$R/server/src/test/kotlin/ir/dorehami/server/rooms" \
  $PURE_UI_TESTS "$STUBS/junit" 2>&1 | grep -v "^warning" || true
echo "== run"
java -cp "$OUT/main.jar:$OUT/test.jar:$LIB/kotlin-test.jar:$LIB/kotlin-stdlib.jar:$LIB/kotlin-reflect.jar:$CO" sandbox.RunnerKt "$OUT/test.jar"
