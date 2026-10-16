#!/bin/bash
# GX-03 review sheet: builds tools/hokm/CardSheet.kt against the *real* Hokm card data and writes
# the SVG sheets into games/hokm/design/gx03 (or the directory given as $1).
#   setup: pip install jdk4py ; npm pack kotlin-compiler@2.4.21 && tar xzf kotlin-compiler-*.tgz -C $KT
#   usage: KT=... JAVA_HOME=... tools/hokm/cardsheet.sh [out-dir] [extra-faces e.g. KH,QH]
set -e
R="$(cd "$(dirname "$0")/../.." && pwd)"
KT="${KT:-/tmp/kt/package}"
export JAVA_HOME="${JAVA_HOME:-/usr/local/lib/python3.11/dist-packages/jdk4py/java-runtime}"
export PATH="$JAVA_HOME/bin:$PATH"
KC="$KT/bin/kotlinc"; LIB="$KT/lib"; CO="$LIB/kotlinx-coroutines-core-jvm.jar"
OUT="${OUT:-/tmp/hokm-cardsheet}"; rm -rf "$OUT"; mkdir -p "$OUT"
UI_MAIN="$R/games/hokm/ui/src/main/kotlin/ir/dorehami/games/hokm/ui"
echo "== compile sheet tool"
"$KC" -nowarn -cp "$CO" -d "$OUT/sheet.jar" \
  "$R/games/hokm/core/src/main/kotlin/ir/dorehami/games/hokm/Cards.kt" \
  "$UI_MAIN/HokmArtDirection.kt" "$UI_MAIN/HokmTokens.kt" \
  "$UI_MAIN/HokmCardGlyphs.kt" "$UI_MAIN/HokmCardFaces.kt" "$UI_MAIN/HokmCardDrawing.kt" \
  "$R/tools/hokm/CardSheet.kt" 2>&1 | grep -v "^warning" || true
echo "== render"
cd "$R"
java -cp "$OUT/sheet.jar:$LIB/kotlin-stdlib.jar:$CO" ir.dorehami.tools.hokm.CardSheetKt "${1:-}" "${2:-}"
