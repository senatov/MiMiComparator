#!/bin/zsh
set -euo pipefail

# build_mimicomparator_app.zsh
# Build a macOS .app bundle for a Gradle-based Java / JavaFX project using jpackage.
# Defaults are intentionally conservative and can be overridden via environment variables.
# Example:
#   APP_NAME=MiMiComparator \
#   MAIN_CLASS=com.mimi.comparator.Main \
#   ./build_mimicomparator_app.zsh

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
DEFAULT_PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$DEFAULT_PROJECT_DIR}"
cd "$PROJECT_DIR"

APP_NAME="${APP_NAME:-MiMiComparator}"
MAIN_CLASS="${MAIN_CLASS:-com.mimi.comparator.Main}"
APP_VERSION="${APP_VERSION:-1.0.0}"
VENDOR="${VENDOR:-Iakov Senatov}"
DESCRIPTION="${DESCRIPTION:-MiMiComparator}"
COPYRIGHT_TEXT="${COPYRIGHT_TEXT:-Copyright © 2026 Iakov Senatov. All rights reserved.}"
DEST_DIR="${DEST_DIR:-$PROJECT_DIR/dist/macos}"
TEMP_DIR="${TEMP_DIR:-$PROJECT_DIR/build/jpackage-tmp}"
JAVA_OPTIONS="${JAVA_OPTIONS:---enable-native-access=ALL-UNNAMED}"
VERBOSE="${VERBOSE:-1}"
SIGN_APP="${SIGN_APP:-0}"
BUNDLE_ID="${BUNDLE_ID:-com.senatov.mimicomparator}"

function info() {
    print -P "%F{33}[INFO]%f $*"
}

function warn() {
    print -P "%F{214}[WARN]%f $*"
}

function fail() {
    print -P "%F{196}[ERROR]%f $*" >&2
    exit 1
}

function require_cmd() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

function first_existing_file() {
    local candidate

    for candidate in "$@"; do
        if [[ -f "$candidate" ]]; then
            print -- "$candidate"
            return 0
        fi
    done

    return 1
}

function detect_gradle_context() {
    local root_build_file
    local app_build_file
    local settings_file

    root_build_file="$(first_existing_file "$PROJECT_DIR/build.gradle" "$PROJECT_DIR/build.gradle.kts" || true)"
    app_build_file="$(first_existing_file "$PROJECT_DIR/app/build.gradle" "$PROJECT_DIR/app/build.gradle.kts" || true)"
    settings_file="$(first_existing_file "$PROJECT_DIR/settings.gradle" "$PROJECT_DIR/settings.gradle.kts" || true)"

    if [[ -n "$root_build_file" ]]; then
        EFFECTIVE_BUILD_FILE="$root_build_file"
        EFFECTIVE_MODULE_DIR="$PROJECT_DIR"
        DEFAULT_INPUT_DIR="$PROJECT_DIR/build/libs"
        DEFAULT_ICON_FILE="$(first_existing_file "$PROJECT_DIR/src/main/resources/icon.icns" "$PROJECT_DIR/app/src/main/resources/icon.icns" || true)"
        DEFAULT_BUILD_CMD="./gradlew clean jar"
        return 0
    fi

    if [[ -n "$app_build_file" ]]; then
        EFFECTIVE_BUILD_FILE="$app_build_file"
        EFFECTIVE_MODULE_DIR="$PROJECT_DIR/app"
        DEFAULT_INPUT_DIR="$PROJECT_DIR/app/build/libs"
        DEFAULT_ICON_FILE="$(first_existing_file "$PROJECT_DIR/app/src/main/resources/icon.icns" "$PROJECT_DIR/src/main/resources/icon.icns" || true)"

        if [[ -n "$settings_file" ]]; then
            DEFAULT_BUILD_CMD="./gradlew clean :app:jar"
        else
            DEFAULT_BUILD_CMD="./gradlew -p app clean jar"
        fi
        return 0
    fi

    return 1
}

function detect_jar() {
    local jar
    jar="$(find "$INPUT_DIR" -maxdepth 1 -type f -name '*.jar' \
        ! -name '*-sources.jar' \
        ! -name '*-javadoc.jar' \
        ! -name '*-plain.jar' \
        ! -name 'original-*.jar' \
        | sort | tail -n 1)"

    [[ -n "$jar" ]] || fail "No application JAR found in $INPUT_DIR"
    print -- "$jar"
}


function git_version_fallback() {
    if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        git describe --tags --always 2>/dev/null || true
    fi
}

# Normalize an app version string to X.Y.Z and guarantee first component >= 1 for macOS jpackage.
function normalize_app_version() {
    local raw_version="$1"
    local normalized=""
    local -a parts
    local first_part

    if [[ "$raw_version" =~ '^[0-9]+(\.[0-9]+){0,2}$' ]]; then
        normalized="$raw_version"
    elif [[ "$raw_version" =~ '^v?([0-9]+)(\.[0-9]+){0,2}([.-].*)?$' ]]; then
        normalized="${match[1]}${match[2]}"
    elif [[ "$raw_version" =~ '^[0-9a-fA-F]{7,}$' ]]; then
        normalized="1.0.0"
    else
        normalized="$(print -- "$raw_version" | grep -Eo '[0-9]+' | head -n 3 | paste -sd '.' -)"
    fi

    if [[ -z "$normalized" ]]; then
        print -- "1.0.0"
        return 0
    fi

    parts=(${(s:.:)normalized})
    first_part="${parts[1]:-0}"

    if (( first_part <= 0 )); then
        parts[1]=1
    fi

    print -- "${(j:.:)parts}"
}


if [[ "$APP_VERSION" == "1.0.0" ]]; then
    GIT_VERSION="$(git_version_fallback)"
    if [[ -n "$GIT_VERSION" ]]; then
        APP_VERSION="$GIT_VERSION"
    fi
fi

RAW_APP_VERSION="$APP_VERSION"
APP_VERSION="$(normalize_app_version "$APP_VERSION")"

require_cmd jpackage
require_cmd java
require_cmd "$SHELL"

[[ -f "$PROJECT_DIR/gradlew" ]] || fail "gradlew not found in $PROJECT_DIR"

detect_gradle_context || fail "No Gradle build file found in $PROJECT_DIR or $PROJECT_DIR/app"

ICON_FILE="${ICON_FILE:-$DEFAULT_ICON_FILE}"
INPUT_DIR="${INPUT_DIR:-$DEFAULT_INPUT_DIR}"
BUILD_CMD="${BUILD_CMD:-$DEFAULT_BUILD_CMD}"

mkdir -p "$DEST_DIR"
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR"

info "Project dir: $PROJECT_DIR"
info "Script dir: $SCRIPT_DIR"
info "Default project dir: $DEFAULT_PROJECT_DIR"
info "Effective module dir: $EFFECTIVE_MODULE_DIR"
info "Effective build file: $EFFECTIVE_BUILD_FILE"
info "App name: $APP_NAME"
info "Main class: $MAIN_CLASS"
info "Raw app version: $RAW_APP_VERSION"
info "Normalized app version: $APP_VERSION"
info "Input dir: $INPUT_DIR"
info "Icon file: ${ICON_FILE:-<none>}"
info "Build command: $BUILD_CMD"

info "Running Gradle build..."
eval "$BUILD_CMD"

[[ -d "$INPUT_DIR" ]] || fail "Input directory not found after build: $INPUT_DIR"
APP_JAR="$(detect_jar)"
APP_JAR_NAME="$(basename "$APP_JAR")"

info "Using JAR: $APP_JAR_NAME"

JPACKAGE_ARGS=(
    --type app-image
    --name "$APP_NAME"
    --dest "$DEST_DIR"
    --input "$INPUT_DIR"
    --main-jar "$APP_JAR_NAME"
    --main-class "$MAIN_CLASS"
    --app-version "$APP_VERSION"
    --vendor "$VENDOR"
    --description "$DESCRIPTION"
    --copyright "$COPYRIGHT_TEXT"
    --temp "$TEMP_DIR"
    --mac-package-name "$APP_NAME"
    --mac-package-identifier "$BUNDLE_ID"
)

if [[ -n "${ICON_FILE:-}" && -f "$ICON_FILE" ]]; then
    JPACKAGE_ARGS+=(--icon "$ICON_FILE")
elif [[ -n "${ICON_FILE:-}" ]]; then
    warn "Icon file not found, continuing without custom icon: $ICON_FILE"
else
    warn "Icon file not configured, continuing without custom icon"
fi

if [[ -n "$JAVA_OPTIONS" ]]; then
    JPACKAGE_ARGS+=(--java-options "$JAVA_OPTIONS")
fi

if [[ "$SIGN_APP" == "1" ]]; then
    JPACKAGE_ARGS+=(--mac-sign)
fi

if [[ "$VERBOSE" == "1" ]]; then
    JPACKAGE_ARGS+=(--verbose)
fi

info "Packaging .app with jpackage..."
jpackage "${JPACKAGE_ARGS[@]}"

APP_BUNDLE="$DEST_DIR/$APP_NAME.app"

[[ -d "$APP_BUNDLE" ]] || fail "Expected app bundle was not created: $APP_BUNDLE"

info "Build finished successfully"
info "App bundle: $APP_BUNDLE"
info "Open folder with: open '$DEST_DIR'"
