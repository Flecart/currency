#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
build=true
wait_for_phone=false
serial=""
while (($#)); do
    case "$1" in
        --no-build) build=false; shift ;;
        --wait) wait_for_phone=true; shift ;;
        --serial)
            [[ $# -ge 2 && -n "$2" ]] || { echo 'Missing value for --serial.' >&2; exit 2; }
            serial="$2"; shift 2 ;;
        -h|--help)
            cat <<'HELP'
Usage: ./install-debug.sh [--wait] [--no-build] [--serial SERIAL]
Build, install/update, and open Currency on one USB-connected Android phone.
Enable USB debugging and accept the phone's debugging prompt first.
  --wait       Wait for the phone to connect and authorize USB debugging.
  --no-build   Install the existing app/build/outputs/apk/debug/app-debug.apk.
  --serial ID  Select a specific device when several are connected.
App data is preserved. No uninstall, reset, or STT configuration changes.
HELP
            exit 0 ;;
        *) echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
    esac
done

sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_dir" && -f "$project_dir/local.properties" ]]; then
    sdk_dir="$(sed -n 's/^sdk\.dir=//p' "$project_dir/local.properties" | head -n 1)"
fi
sdk_dir="${sdk_dir:-$HOME/Android/Sdk}"
if [[ -x "$sdk_dir/platform-tools/adb" ]]; then
    adb_bin="$sdk_dir/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
    adb_bin="$(command -v adb)"
else
    echo 'adb was not found. Set ANDROID_HOME to your Android SDK.' >&2; exit 1
fi
target=(-d)
[[ -z "$serial" ]] || target=(-s "$serial")
if $wait_for_phone; then
    echo 'Waiting for your phone. Accept the USB debugging prompt on the phone if shown.'
    "$adb_bin" "${target[@]}" wait-for-device
fi
if ! "$adb_bin" "${target[@]}" get-state >/dev/null 2>&1; then
    "$adb_bin" devices -l
    echo 'Connect and authorize one USB phone, use --wait, or select it with --serial SERIAL.' >&2
    exit 1
fi

if $build; then
    # Require a full JDK 21; JAVA_HOME may otherwise point to a JRE or newer JDK.
    candidates=("${JAVA_HOME:-}" "$HOME"/.asdf/installs/java/* /usr/lib/jvm/*)
    java_dir=""
    for candidate in "${candidates[@]}"; do
        if [[ -x "$candidate/bin/javac" ]] && "$candidate/bin/javac" -version 2>&1 | grep -qE '^javac 21([.]|$)'; then
            java_dir="$candidate"; break
        fi
    done
    if [[ -z "$java_dir" ]]; then
        echo 'A full JDK 21 is required. Set JAVA_HOME to its installation directory.' >&2; exit 1
    fi
    export JAVA_HOME="$java_dir"
    export ANDROID_HOME="$sdk_dir"
    echo 'Building Currency. Gradle configuration can take several minutes on this machine.'
    echo 'To install an already-built APK next time, use: ./install-debug.sh --no-build'
    (cd "$project_dir" && ./gradlew --no-daemon --console=plain :app:assembleDebug)
fi

apk="$project_dir/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk" ]] || { echo 'No debug APK found. Run again without --no-build.' >&2; exit 1; }
"$adb_bin" "${target[@]}" install -r "$apk"
"$adb_bin" "${target[@]}" shell am start -W -n io.github.currency.companion/.MainActivity
echo 'Currency installed and opened. Existing app data was preserved.'
