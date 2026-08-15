# Activates the repo-local toolchain — source this, do not execute it.
#   source scripts/env.sh
# Undo by opening a new shell; nothing here is persisted.

_domovoy_root="$(cd "$(dirname "${BASH_SOURCE[0]:-${(%):-%x}}")/.." && pwd)"

export JAVA_HOME="$_domovoy_root/.toolchain/jdk/Contents/Home"
export ANDROID_HOME="$_domovoy_root/.toolchain/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
# ANDROID_USER_HOME only — setting ANDROID_PREFS_ROOT as well makes AGP fail with
# "several environment variables contain different paths", since that one names
# the parent directory rather than the prefs directory itself.
export ANDROID_USER_HOME="$_domovoy_root/.toolchain/android-prefs"
# Keeps the Gradle dependency cache, distributions and daemon inside the repo
# instead of ~/.gradle — this is the part that makes it venv-like.
export GRADLE_USER_HOME="$_domovoy_root/.toolchain/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

unset _domovoy_root
