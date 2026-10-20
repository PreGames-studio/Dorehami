#!/usr/bin/env bash
# Prepare games/dejban/godot/build/dezhban.pck for the Android host module.
#
# The Android app does not compile GDScript. CI/local builds use the pinned
# Godot editor only to import the project, run its headless self-test, and
# export the platform pack consumed by :games:dejban:ui.
set -euo pipefail

GODOT_VERSION="${GODOT_VERSION:-4.3-stable}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../godot" && pwd)"
PACK_RELATIVE_PATH="build/dezhban.pck"
TEMPLATE_VERSION="${GODOT_VERSION/-stable/.stable}"
TEMPLATE_DIR="${HOME}/.local/share/godot/export_templates/${TEMPLATE_VERSION}"

find_godot() {
    if command -v godot >/dev/null 2>&1; then
        command -v godot
        return 0
    fi
    if [ -x "${HOME}/.local/bin/godot" ]; then
        echo "${HOME}/.local/bin/godot"
        return 0
    fi
    return 1
}

install_godot() {
    local install_dir="${RUNNER_TEMP:-/tmp}/dorehami-godot-${GODOT_VERSION}"
    mkdir -p "${install_dir}" "${HOME}/.local/bin"

    if [ ! -x "${install_dir}/godot" ]; then
        echo "Downloading Godot ${GODOT_VERSION}…" >&2
        curl -sSL -o "${install_dir}/godot.zip" \
            "https://github.com/godotengine/godot/releases/download/${GODOT_VERSION}/Godot_v${GODOT_VERSION}_linux.x86_64.zip"
        unzip -q -o "${install_dir}/godot.zip" -d "${install_dir}/editor"
        local editor_bin
        editor_bin="$(find "${install_dir}/editor" -maxdepth 1 -type f -name 'Godot*' | head -n 1)"
        test -n "${editor_bin}"
        chmod +x "${editor_bin}"
        mv "${editor_bin}" "${install_dir}/godot"
    fi

    if [ ! -f "${TEMPLATE_DIR}/android_release.apk" ] && [ ! -f "${TEMPLATE_DIR}/android_source.zip" ]; then
        echo "Downloading Godot ${GODOT_VERSION} export templates…" >&2
        curl -sSL -o "${install_dir}/templates.tpz" \
            "https://github.com/godotengine/godot/releases/download/${GODOT_VERSION}/Godot_v${GODOT_VERSION}_export_templates.tpz"
        rm -rf "${install_dir}/templates"
        unzip -q -o "${install_dir}/templates.tpz" -d "${install_dir}/templates"
        rm -rf "${TEMPLATE_DIR}"
        mkdir -p "${TEMPLATE_DIR}"
        mv "${install_dir}/templates/templates/"* "${TEMPLATE_DIR}/"
    fi

    echo "${install_dir}/godot"
}

GODOT_BIN="$(find_godot || install_godot)"
echo "Using Godot: ${GODOT_BIN}"
"${GODOT_BIN}" --version

echo "Importing Dezhban assets…"
"${GODOT_BIN}" --headless --path "${PROJECT_DIR}" --import

echo "Running Dezhban headless self-test…"
set -o pipefail
"${GODOT_BIN}" --headless --path "${PROJECT_DIR}" \
    --fixed-fps 60 --quit-after 3600 \
    res://tests/headless_test.tscn 2>&1 | tee "${PROJECT_DIR}/build-headless-test.log"

PATTERN='SCRIPT ERROR|Parse Error|Failed to load script|Cannot open file|Nonexistent function|Invalid call|Invalid get index|Invalid set index|Invalid access to property|Cannot call method|Cannot infer the type'
if grep -nE "${PATTERN}" "${PROJECT_DIR}/build-headless-test.log"; then
    echo "Dezhban headless test reported script/runtime errors." >&2
    exit 1
fi

mkdir -p "${PROJECT_DIR}/build"
echo "Exporting ${PACK_RELATIVE_PATH}…"
"${GODOT_BIN}" --headless --path "${PROJECT_DIR}" \
    --export-pack "Android" "${PACK_RELATIVE_PATH}"

test -s "${PROJECT_DIR}/${PACK_RELATIVE_PATH}"
ls -lh "${PROJECT_DIR}/${PACK_RELATIVE_PATH}"
