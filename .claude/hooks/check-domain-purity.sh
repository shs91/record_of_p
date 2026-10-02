#!/usr/bin/env bash
# PostToolUse(Edit|Write) 훅: domain/ 아래 Kotlin 파일이 Android/GMS나 상위 계층을 import하면 차단 사유를 Claude에게 돌려준다.
# 설계 §5.2 — domain/engine은 순수 Kotlin(JVM 테스트·iOS 포팅의 기반), 의존 방향 ui → data → domain.
set -euo pipefail

file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
case "$file" in
  */src/main/java/com/recordofp/app/domain/*.kt) ;;
  *) exit 0 ;;
esac
[ -f "$file" ] || exit 0

violations=$(grep -nE '^import (android\.|androidx\.|com\.google\.android\.|com\.recordofp\.app\.(data|platform|ui|di)\.|com\.recordofp\.app\.(R|BuildConfig)$)' "$file" || true)
if [ -n "$violations" ]; then
  {
    echo "domain/ 순수성 위반 (설계 §5.2): $file"
    echo "$violations"
    echo "domain은 Android/GMS와 data·platform·ui·di 패키지를 import할 수 없다. 포트(인터페이스)를 domain이나 data에 두고 구현을 바깥 계층으로 옮길 것."
  } >&2
  exit 2
fi
