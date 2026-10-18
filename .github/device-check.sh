#!/usr/bin/env bash
# Проверка на эмуляторе: экраны, добавление лекарства, настоящий будильник, отметка «Принял», падения.
# Аргумент: имя набора (для папки со скриншотами).
set -uo pipefail
NAME="$1"
PKG=io.github.etc51.tabletki
OUT="device-check/$NAME"
mkdir -p "$OUT"
FAIL=0
log() { echo "== $*"; }
fail() { echo "!! ПРОВАЛ: $*"; echo "ПРОВАЛ: $*" >> "$OUT/result.txt"; FAIL=1; }
ok() { echo "ok: $*"; echo "ok: $*" >> "$OUT/result.txt"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

# Найти на экране элемент с текстом и нажать на его центр.
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys
want = sys.argv[1]
xml = open('/tmp/ui.xml', encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)
    if t and t.group(1) == want:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -z "$xy" ]; then return 1; fi
  adb shell input tap $xy
}
# Системные окна «приложение не отвечает» (бывают у лаунчера эмулятора) закрываем кнопкой «Wait».
dismiss_anr() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  if adb shell cat /sdcard/ui.xml | grep -q "isn't responding"; then
    echo "info: системное окно ANR, закрываю"
    tap_text "Wait" || adb shell input keyevent 4
    sleep 2
  fi
}
screen_has() {
  dismiss_anr
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml | grep -q "text=\"$1\""
}
start() { adb shell am start -n $PKG/ru.tabletki.ui.MainActivity --es screen "$1" >/dev/null; sleep 4; dismiss_anr; }
send() { adb shell am broadcast -n $PKG/ru.tabletki.debug.DemoDataReceiver -a "ru.tabletki.debug.$1" >/dev/null; sleep 2; }

adb logcat -c
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell appops set $PKG USE_FULL_SCREEN_INTENT allow 2>/dev/null || true
adb shell cmd deviceidle whitelist +$PKG 2>/dev/null || true
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0

log "Пустое приложение"
start today
shot 01-pusto
screen_has "Добавьте первое лекарство" && ok "пустой экран" || fail "нет подсказки на пустом экране"

log "Добавление лекарства через интерфейс"
if tap_text "Добавить лекарство"; then
  sleep 3
  shot 02-novoe
  # Поле «Название» — первое поле ввода; печатаем латиницей, adb не вводит кириллицу.
  tap_text "Название" && adb shell input text "Aspirin" && sleep 1
  adb shell input keyevent 111  # убрать клавиатуру
  sleep 1
  read -r W H < <(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr 'x' ' ')
  for i in 1 2 3 4 5 6; do tap_text "Сохранить" && break; adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 300; sleep 1; done
  sleep 3
  start medicines
  shot 03-spisok
  screen_has "Aspirin" && ok "лекарство сохранилось" || fail "лекарство не появилось в списке"
else
  fail "нет кнопки «Добавить лекарство»"
fi

log "Демо-данные и все экраны"
send SEED
for s in today medicines journal settings; do start $s; shot "10-$s"; done
start edit:1; shot 14-izmenit
start today
screen_has "Пора принять!" && ok "статус «Пора принять» на экране «Сегодня»" || fail "нет статуса «Пора принять»"

log "Экран напоминания"
send ALARM
sleep 3
shot 20-napominanie
screen_has "Отложить на 10 минут" && ok "экран напоминания открылся" || fail "экран напоминания не открылся"
if tap_text "Принял"; then
  sleep 3
  start today
  screen_has "Пора принять!" && fail "после «Принял» приём всё ещё ждёт" || ok "отметка «Принял» сработала"
  shot 21-posle-prinyal
else
  fail "нет кнопки «Принял» на экране напоминания"
fi

log "Настоящий будильник при выключенном экране"
send CLEAR
send SOON
adb shell input keyevent 223 2>/dev/null || adb shell input keyevent 26   # выключить экран
for i in $(seq 1 30); do
  sleep 5
  if adb shell dumpsys notification --noredact | grep -q "Пора принять"; then break; fi
done
sleep 3
adb shell input keyevent 224 2>/dev/null || true   # включить экран
sleep 2
shot 30-budilnik
if adb shell dumpsys notification --noredact | grep -q "Пора принять"; then
  ok "уведомление пришло вовремя"
else
  fail "уведомление о приёме не пришло за 2,5 минуты"
fi
adb shell dumpsys activity activities | grep -q "AlarmActivity" && ok "экран-будильник поверх блокировки" || echo "info: экран-будильник не открылся (на части версий только уведомление)"
adb shell wm dismiss-keyguard 2>/dev/null || true
sleep 2
if tap_text "Принял"; then
  sleep 3
  adb shell dumpsys notification --noredact | grep -q "Пора принять" && fail "уведомление не исчезло после «Принял»" || ok "после «Принял» уведомление исчезло"
fi
shot 31-posle

log "Падения"
adb logcat -d > "$OUT/logcat.txt"
if grep -A20 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG\|ru.tabletki"; then
  fail "приложение падало, см. logcat.txt"
  grep -A30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
else
  ok "падений нет"
fi

cat "$OUT/result.txt"
exit $FAIL
