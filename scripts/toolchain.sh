#!/usr/bin/env bash
# Downloads the JDK and Android SDK into .toolchain/ — nothing is installed system-wide.
# Idempotent: re-running skips whatever is already in place. Delete .toolchain/ to start over.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN="$ROOT/.toolchain"
JDK_DIR="$TOOLCHAIN/jdk"
SDK_DIR="$TOOLCHAIN/android-sdk"

JDK_VERSION=21
PLATFORM="platforms;android-37.1"   # compileSdk: newer than targetSdk on purpose, AndroidX requires it
BUILD_TOOLS="build-tools;37.0.0"

case "$(uname -m)" in
    arm64) ARCH=aarch64 ;;
    x86_64) ARCH=x64 ;;
    *) echo "unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac

# Keeps sdkmanager's caches and prefs out of ~/.android. ANDROID_USER_HOME only:
# also setting ANDROID_PREFS_ROOT makes AGP fail with "several environment
# variables contain different paths", as that one names the parent directory.
export ANDROID_USER_HOME="$TOOLCHAIN/android-prefs"

mkdir -p "$TOOLCHAIN" "$ANDROID_USER_HOME"

# --- JDK -------------------------------------------------------------------
# Fetched by hand rather than via Gradle's toolchain provisioning: the wrapper
# is a shell script that needs a JVM to start, so it cannot bootstrap its own.
if [ -x "$JDK_DIR/Contents/Home/bin/java" ]; then
    echo "jdk: already present"
else
    echo "jdk: downloading Temurin $JDK_VERSION ($ARCH)"
    URL="https://api.adoptium.net/v3/binary/latest/${JDK_VERSION}/ga/mac/${ARCH}/jdk/hotspot/normal/eclipse"
    mkdir -p "$JDK_DIR"
    curl -fsSL "$URL" | tar -xz -C "$JDK_DIR" --strip-components=1
fi

export JAVA_HOME="$JDK_DIR/Contents/Home"
"$JAVA_HOME/bin/java" -version

# --- Android command-line tools --------------------------------------------
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$SDKMANAGER" ]; then
    echo "cmdline-tools: already present"
else
    echo "cmdline-tools: resolving current build from the SDK repository manifest"
    # The zip filename carries a build number that changes; read it off Google's
    # own manifest instead of pinning a number that goes stale.
    # sort -V, not sort: the build number is compared numerically, otherwise
    # "9862592" sorts above "11076708" and we pin a two-year-old build.
    ZIP=$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml \
        | grep -o 'commandlinetools-mac-[0-9]*_latest\.zip' | sort -uV | tail -1)
    if [ -z "$ZIP" ]; then
        echo "could not resolve the cmdline-tools download from the manifest" >&2
        exit 1
    fi
    echo "cmdline-tools: downloading $ZIP"
    TMP="$TOOLCHAIN/.download"
    rm -rf "$TMP" && mkdir -p "$TMP"
    trap 'rm -rf "$TMP"' EXIT
    curl -fsSL -o "$TMP/tools.zip" "https://dl.google.com/android/repository/$ZIP"
    unzip -q "$TMP/tools.zip" -d "$TMP"
    mkdir -p "$SDK_DIR/cmdline-tools"
    mv "$TMP/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
fi

# --- SDK packages ----------------------------------------------------------
echo "sdk: accepting licences"
# pipefail off for this one: `yes` is killed by SIGPIPE once sdkmanager stops
# reading, and its 141 would otherwise abort the script. sdkmanager's own exit
# status is still the pipeline's, so a real licence failure is still caught.
set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses > /dev/null
set -o pipefail

echo "sdk: installing platform-tools, $PLATFORM, $BUILD_TOOLS"
"$SDKMANAGER" --sdk_root="$SDK_DIR" "platform-tools" "$PLATFORM" "$BUILD_TOOLS"

# --- local.properties ------------------------------------------------------
# AGP reads sdk.dir from here; gitignored, so the path stays off the record.
if ! grep -qs '^sdk.dir=' "$ROOT/local.properties" 2>/dev/null; then
    echo "sdk.dir=$SDK_DIR" >> "$ROOT/local.properties"
    echo "local.properties: sdk.dir written"
fi

echo
echo "toolchain ready. activate with:  source scripts/env.sh"
