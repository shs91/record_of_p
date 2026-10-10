#!/usr/bin/env python3
"""Material Symbols Rounded SVG를 Android VectorDrawable로 가져온다 (디자인 개편안 2 §4).

다시 실행하면 같은 파일을 다시 만든다. 아이콘을 더하려면 ICONS에 한 줄 넣는다.
사용: python3 -I tools/design/material_symbols.py   (저장소 루트에서)
"""
import re
import sys
import urllib.request
from pathlib import Path

# 고정 버전 — 바꾸면 모든 아이콘 모양이 함께 바뀐다
VERSION = "0.47.0"
URL = "https://cdn.jsdelivr.net/npm/@material-symbols/svg-400@{v}/rounded/{name}.svg"
OUT = Path("android/app/src/main/res/drawable")

# drawable 이름 → Material Symbols 이름
ICONS = {
    "ic_cat_convenience": "storefront",
    "ic_cat_mart": "shopping_cart",
    "ic_cat_pharmacy": "medication",
    "ic_cat_bank": "account_balance",
    "ic_cat_post": "local_post_office",
    "ic_cat_fuel": "local_gas_station",
    "ic_cat_laundry": "checkroom",
    "ic_cat_cafe": "local_cafe",
    "ic_cat_hospital": "local_hospital",
    "ic_cat_subway": "subway",
    "ic_trigger_brand": "shopping_bag",
    "ic_trigger_search": "search",
    "ic_trigger_place": "location_on",
    "ic_alert_error": "error",
}

# VectorPath: 원본 경로를 그대로 옮긴다(정밀도를 줄이면 모양이 바뀐다). 24dp 아이콘이라 성능 영향이 작다
TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "{name}" (Apache License 2.0, Google). tools/design/material_symbols.py가 만든다 — 손으로 고치지 않는다 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="#FF000000"
            android:pathData="{d}"
            tools:ignore="VectorPath" />
    </group>
</vector>
"""


def main() -> int:
    if not OUT.is_dir():
        print(f"{OUT}가 없다 — 저장소 루트에서 실행한다", file=sys.stderr)
        return 1
    for drawable, name in ICONS.items():
        with urllib.request.urlopen(URL.format(v=VERSION, name=name)) as res:
            svg = res.read().decode("utf-8")
        if 'viewBox="0 -960 960 960"' not in svg:
            print(f"{name}: 예상과 다른 viewBox", file=sys.stderr)
            return 1
        paths = re.findall(r'<path d="([^"]+)"', svg)
        if len(paths) != 1:
            print(f"{name}: path가 {len(paths)}개", file=sys.stderr)
            return 1
        (OUT / f"{drawable}.xml").write_text(TEMPLATE.format(name=name, d=paths[0]), encoding="utf-8")
        print(f"{drawable}.xml ← {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
