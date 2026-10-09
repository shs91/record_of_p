# 디자인 개편안 — "클린 미니멀" (2026-09-03)

| | |
|---|---|
| 상태 | **승인·구현 완료**(2026-09-03) — 목업 검토 후 사용자 승인("이대로 승인"), feat/v1에 7커밋(0956387..d7a3265) · 실기기 스크린샷 확인 대기 |
| 분류 | bounded (기존 화면 시각 개편, 구조·로직 불변) |
| 전제 | 실기기 1차 검증의 F3(인셋·다크 버그)를 이 작업이 흡수. F1·F4·F5는 별도 기능 라운드 |

## 컨셉

흰 종이 위의 기록. 콘텐츠(할 일)가 주인공, 크롬은 물러난다. 진한 잉크색 타이포가 위계를 만들고, 블루는 "행동"에만 아껴 쓴다. (토스·당근 계열의 한국형 클린 미니멀)

## 1. 파운데이션

- **폰트**: Pretendard 정적 4웨이트(Regular 400·Medium 500·SemiBold 600·Bold 700), `res/font/`. APK +약 4MB.
  - 소스(확인됨): `https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/packages/pretendard/dist/public/static/Pretendard-{Regular,Medium,SemiBold,Bold}.otf`
- **컬러** — 다이나믹 컬러 제거, 고정 브랜드 팔레트(Theme.kt 재작성):

| 토큰 | 라이트 | 다크 |
|---|---|---|
| background | `#F7F8FA` | `#101418` |
| surface(카드) | `#FFFFFF` | `#1B2027` |
| onSurface(잉크) | `#191F28` | `#E9EDF2` |
| onSurfaceVariant(보조) | `#6B7684` | `#8B95A1` |
| primary | `#1B6EF3` | `#5B95F8` |
| primaryContainer | `#EAF1FE` | `#1E2C42` |
| 성공(완료 스와이프) | `#12B76A` | 동일 계열 |
| error | `#F04452` | `#FF6B6B` 계열 |

- **형태**: 카드 20dp 라운드, 칩 pill, 여백 20dp 그리드, 그림자 대신 톤 차이.
- **앱 아이콘**: "P가 곧 핀" — 원형 헤드+스템이 P 실루엣을 이루는 지오메트릭 마크로 교체(어댑티브+모노크롬).
- **XML 테마**: DayNight 정합(values-night windowBackground) — 다크 창 배경 버그 근절.

## 2. 화면별

- **온보딩**: 하단 고정 버튼+페이지 도트, 큰 핀 그래픽, 권한 단계에 "왜 필요한가" 카드. 카피 유지.
- **홈**: 큰 타이틀(28sp Bold), 카드 리스트(제목 17 SemiBold + 트리거 회색 pill 칩), 스와이프 완료 초록 배경+체크, 항목 등장/제거 애니메이션, 보호 배너 앰버 톤, FAB → 확장 FAB "＋ 기록".
- **에디터**: TopAppBar(X · "새 기록"/"기록 수정" · 저장) — 상태바 겹침 해결. 섹션 레이블 12sp, FilterChip 선택 시 연블루+블루 텍스트, 장소 결과 카드화, 삭제는 하단 빨간 텍스트버튼.
- **주변 보기**: 그룹 헤더 이모지+SemiBold, 거리 오른쪽 블루 배지, POI 행 카드화.
- **설정**: TopAppBar("설정") + 그룹 카드 3개(보호 상태/알림 정책/문제 해결), 상태 pill("켜짐"/"꺼짐").
- **진단**: 결과별 컬러 도트(APPLIED 초록·BLOCK/FAILED 빨강·STOOD_DOWN 회색), tabular-nums.
- **알림**: 스몰 아이콘 전용 모노크롬 핀으로 교체.

## 3. 범위 밖

화면 구조·내비게이션·엔진·문자열 의미(카피 미세 수정만)·기능 로직. F1·F4·F5는 기능 라운드에서.

## 4. 파일·검증

- `ui/theme/*` 전면 재작성(Color/Type/Shape/Theme), `res/font/*` 신규, 화면 6개 스타일 수정, `themes.xml`(+values-night), 런처·알림 아이콘 벡터, strings 미세 수정.
- 검증: 기존 JVM 테스트 71개 그린 유지 + 기기(A25) 설치 후 전 화면 라이트/다크 스크린샷 육안 확인(`tools/device/e2e.sh` 활용).

## 구현 기록 (2026-09-03)

- 승인 절차: 목업 아티팩트(https://claude.ai/code/artifact/dcf3e7e8-faec-41dc-867b-369ed8f90ee1 — 팔레트·타입·아이콘·화면 10장 라이트/다크) 검토 → "이대로 승인".
- 커밋(feat/v1, 화면 단위 7개): 파운데이션 0956387 → 아이콘 2bdd17f → 홈 f892c8e → 에디터 439a379 → 온보딩 feff845 → 주변 27beef2 → 설정·진단 d7a3265.
- 검증: JVM 테스트 71개 그린 유지(커밋마다), lintDebug 통과, assembleDebug 성공.
- 구현 노트(스펙과의 차이):
  - Pretendard 실측 +6.3MB (스펙 추정 4MB 초과, OTF 4웨이트).
  - 주변 보기 POI 카드에 관련 기록 부제는 넣지 않음(목업엔 있었으나 §3 로직 불변 원칙 우선).
  - 주변·설정·진단에 TopAppBar 뒤로가기 추가(표준 어포던스, 내비 구조 불변).
  - strings 미세 수정: editor_brand_hint → editor_section_brand/editor_brand_example 분리, editor_section_place·editor_title_edit·home_fab·onboard_why_* 추가 (ko/en 정합).

## 남은 절차

1. 실기기(A25) 설치 → 전 화면 라이트/다크 스크린샷 육안 확인 (`tools/device/e2e.sh`)
2. 기능 라운드(F1·F4·F5 + 도보 테스트 4~6 + 재부팅 7 + TalkBack)로 이동
