#!/bin/bash
# GX-04: prints the card-movement timing table as Markdown, straight out of HokmChoreography.
#   setup:  npm pack kotlin-compiler@2.4.21 && tar xzf kotlin-compiler-*.tgz -C $KT ; pip install jdk4py
#   usage:  KT=/path/to/kotlin-compiler/package JAVA_HOME=/path/to/jre bash tools/hokm/choreography.sh
set -e
R="$(cd "$(dirname "$0")/../.." && pwd)"
KT="${KT:-/tmp/kt/package}"
export JAVA_HOME="${JAVA_HOME:-/usr/local/lib/python3.11/dist-packages/jdk4py/java-runtime}"
export PATH="$JAVA_HOME/bin:$PATH"
U="$R/games/hokm/ui/src/main/kotlin/ir/dorehami/games/hokm/ui"
OUT="${OUT:-/tmp/hokm-choreography}"
rm -rf "$OUT"; mkdir -p "$OUT"
"$KT/bin/kotlinc" -nowarn -d "$OUT/choreography.jar" \
  "$U/HokmArtDirection.kt" "$U/HokmTokens.kt" "$U/HokmMotion.kt" "$U/HokmChoreography.kt" \
  "$R/tools/hokm/ChoreographySheet.kt"
# -Dstdout.encoding keeps the Persian labels readable when stdout is not a UTF-8 terminal.
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT/choreography.jar:$KT/lib/kotlin-stdlib.jar" sandbox.ChoreographySheetKt
