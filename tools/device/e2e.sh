#!/bin/bash
# 실기기 검증 헬퍼 (Git Bash 전용, 1인 프로젝트라 장비 경로 고정 — 필요 시 ADB env로 덮어쓰기)
# 사용: source tools/device/e2e.sh  →  ss <이름> / tap <x> <y> / back / perms
ADB="${ADB:-/c/Users/PC/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
OUT="${OUT:-$(dirname "${BASH_SOURCE[0]}")/out}"
mkdir -p "$OUT"
OUT_W=$(cygpath -m "$OUT" 2>/dev/null || echo "$OUT")
export MSYS_NO_PATHCONV=1  # /sdcard 경로가 Windows 경로로 변환되는 것 방지

# ss <name>: 기기 스크린샷을 out/<name>.png 로 저장
ss() { "$ADB" shell screencap -p /sdcard/rop.png && "$ADB" pull /sdcard/rop.png "$OUT_W/$1.png" >/dev/null && echo "saved $1.png"; }
# tap <x> <y>: 원본 해상도(A25: 1080x2340) 좌표로 탭 후 0.8초 대기(기기측 sleep — 로컬 sleep 아님)
tap() { "$ADB" shell input tap "$1" "$2" && "$ADB" shell sleep 0.8; }
back() { "$ADB" shell input keyevent KEYCODE_BACK && "$ADB" shell sleep 0.8; }
# perms: 앱 런타임 권한 요약
perms() { "$ADB" shell dumpsys package com.recordofp.app | grep -E "POST_NOTIFICATIONS|ACCESS_FINE_LOCATION|ACCESS_BACKGROUND_LOCATION|ACCESS_COARSE_LOCATION" | grep -E "granted=" | sed -E 's/^ +//' | sort -u; }
