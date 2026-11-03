#!/usr/bin/env bash
# Install or update Tatami Home on the TV, next to the launcher the TV already has.
#
# Usage: TV_HOST=192.168.1.50 home/install.sh          install or update; Home stays as it is
#        TV_HOST=192.168.1.50 home/install.sh --home   then make Tatami Home the TV's Home and check that it took
#
# Builds nothing: run ./gradlew -p home testReleaseUnitTest assembleRelease first. Talks to the TV with tv.py from a
# cleanyourtv checkout: CLEANYOURTV (an absolute path), default ../cleanyourtv next to this repo, with its
# tools/venv made as its README says. It never disables a launcher: the one the TV had stays as the way back,
# and --home prints the command that gives Home back to it.
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-}" in ""|--home) ;; *) echo "usage: home/install.sh [--home]" >&2; exit 2 ;; esac
[ -n "${TV_HOST:-}" ] || { echo "set TV_HOST to the TV's IP address, e.g. TV_HOST=192.168.1.50 $0" >&2; exit 2; }
CYT="$(cd "${CLEANYOURTV:-../cleanyourtv}/tools" 2>/dev/null && { pwd -W 2>/dev/null || pwd; })" || {
  echo "no cleanyourtv checkout at ${CLEANYOURTV:-../cleanyourtv}: clone https://github.com/Gathin23/cleanyourtv there," \
    "or set CLEANYOURTV to yours." >&2; exit 2; }
PY=$CYT/venv/bin/python
[ -x "$PY" ] || PY=$CYT/venv/Scripts/python.exe
[ -x "$PY" ] || { echo "in the cleanyourtv checkout, run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 2; }
APK=home/app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "no $APK: run ./gradlew -p home testReleaseUnitTest assembleRelease first" >&2; exit 2; }
# Windows Python cannot open /c/... paths, so hand it C:/... where Git Bash has them.
APK="$(cd "$(dirname "$APK")" && { pwd -W 2>/dev/null || pwd; })/app-release.apk"
export MSYS_NO_PATHCONV=1
PKG=io.github.pbanddev.tatamihome
HOME_ACTIVITY=$PKG/com.gathin.tvhome.MainActivity
tv() { "$PY" -I "$CYT/tv.py" "$@"; }

tv --push "$APK" /data/local/tmp/tatami.apk >/dev/null
# Wake first: on some sets an install or a start waits while a screensaver is up.
OUT="$(tv 'input keyevent WAKEUP; pm install -r /data/local/tmp/tatami.apk; rm -f /data/local/tmp/tatami.apk')"
case "$OUT" in *Success*) echo "installed $PKG" ;; *) echo "$OUT" >&2; exit 1 ;; esac
# The Wi-Fi pill names the network only with location access (Android 10 and newer), as in the reference launcher.
tv "pm grant $PKG android.permission.ACCESS_FINE_LOCATION" >/dev/null
tv "cmd package compile -m speed -f $PKG" >/dev/null || true
# Tatami Home 0.2 named its screensaver $PKG/.FloorPlanDream; the class now lives in the plan module.
tv "[ \"\$(settings get secure screensaver_components)\" = $PKG/.FloorPlanDream ] && settings put secure screensaver_components $PKG/io.github.pbanddev.tatami.FloorPlanDream" >/dev/null || true

[ "${1:-}" = --home ] || exit 0
PREVIOUS="$(tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -n 1 | tr -d '\r')"
tv "cmd package set-home-activity --user 0 $HOME_ACTIVITY" >/dev/null
tv 'am start -W -a android.intent.action.MAIN -c android.intent.category.HOME' >/dev/null || true
# set-home-activity can print Success and change nothing (some Google TV builds): only the resumed activity counts.
for _ in 1 2 3 4 5; do
  if tv "dumpsys activity activities | grep -E 'mResumedActivity|mFocusedApp'" | grep -q "$HOME_ACTIVITY"; then
    case "$PREVIOUS" in
      "$PKG"/*|android/*|*ResolverActivity*|"")
        # Already Tatami (a second --home), or Android's chooser when several launchers claim Home.
        echo "Home is Tatami. To give Home back, pick one of these and run tv.py 'cmd package set-home-activity --user 0 <it>':"
        tv 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | grep / | grep -v "$PKG/" ;;
      *) echo "Home is Tatami. To give Home back: tv.py 'cmd package set-home-activity --user 0 $PREVIOUS'" ;;
    esac
    exit 0
  fi
  tv 'sleep 1' >/dev/null
done
echo "Home did not switch to Tatami; it is still $PREVIOUS. Nothing was disabled." >&2
exit 1
