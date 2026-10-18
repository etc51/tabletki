#!/usr/bin/env bash
# Снимает скриншоты отладочной сборки в эмуляторе на демо-данных.
set -euo pipefail
PKG=io.github.etc51.tabletki
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell appops set $PKG USE_FULL_SCREEN_INTENT allow || true
adb shell cmd deviceidle whitelist +$PKG || true
adb shell settings put global window_animation_scale 0
adb shell setprop persist.sys.timezone Europe/Moscow || true
mkdir -p screenshots

adb shell am broadcast -n $PKG/ru.tabletki.debug.DemoDataReceiver -a ru.tabletki.debug.SEED
sleep 3

shot() {
  adb shell am start -n $PKG/ru.tabletki.ui.MainActivity --es screen "$1"
  sleep 5
  adb exec-out screencap -p > "screenshots/$2.png"
}
shot today 1-segodnya
shot medicines 2-lekarstva
shot edit:1 3-izmenit
shot journal 4-zhurnal
shot settings 5-nastroyki

shot today 0-warmup
adb shell am broadcast -n $PKG/ru.tabletki.debug.DemoDataReceiver -a ru.tabletki.debug.ALARM
sleep 5
adb exec-out screencap -p > screenshots/6-napominanie.png
rm -f screenshots/0-warmup.png
ls -la screenshots
