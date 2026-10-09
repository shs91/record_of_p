# P의기록 디자인 시스템

| | |
|---|---|
| 상태 | 승인 — 현재 UI의 기준 |
| 버전 | 1.1 |
| 최종 수정 | 2026-10-09 |
| 구현 | Android `ui/theme/{Color,Type,Shape,Theme}.kt` · iOS 미착수 |
| 근거 | 디자인 개편안 `docs/superpowers/specs/2026-09-03-design-refresh-clean-minimal.md`(결정 기록)과 현재 코드 값 |

Android와 iOS 테마는 이 문서의 표를 따른다. 토큰을 바꾸면 이 문서와 코드를 **같은 커밋**에서 바꾸고 끝의 변경 이력에 한 줄 남긴다.

## 1. 방향

### 1.1 레퍼런스

토스, 당근, 헤이딜러. **심플하지만 너무 단순하지는 않게.**

### 1.2 유지할 것 — 토스·당근의 바탕

- 흰 종이 위의 기록. 콘텐츠(할 일)가 주인공이고 크롬은 물러난다.
- 진한 잉크색 타이포가 위계를 만든다. 색으로 위계를 만들지 않는다.
- 블루는 "행동"(버튼, 선택 상태, 거리 배지)에만 아껴 쓴다.
- 그림자 대신 톤 차이로 면을 나눈다. 여백은 넉넉하게 둔다.

### 1.3 더할 것 — 헤이딜러의 성격

바탕을 해치지 않는 선에서 화면에 성격을 준다. 아래 항목은 방향이고, 실제 모양은 다음 디자인 라운드의 목업에서 정한다(§4).

- **숫자 강조**: 거리·개수처럼 사용자가 판단에 쓰는 숫자를 크고 굵게 보여 준다.
- **브랜드 그래픽**: P-핀 모티프를 빈 상태, 온보딩, 완료 순간에 쓴다.
- **마이크로 인터랙션**: 완료 체크, 카드 눌림, 항목 등장에 짧은 모션을 준다.
- **카테고리 컬러 포인트**: 카테고리마다 색을 하나씩 정해 칩·아이콘 배경으로 구분한다.

### 1.4 피할 것

- 장식용 그라디언트, 과한 그림자, 한 화면에 강조색 여러 개
- 다이나믹 컬러. 기록 앱의 종이는 기기 벽지를 따라가지 않는다.

## 2. 토큰

### 2.1 색

코드 이름은 `ui/theme/Color.kt` 기준이다. 표에 없는 Material 슬롯(onPrimary, errorContainer, 다크의 surfaceContainerHigh 등)은 `Theme.kt`에 직접 적힌 값을 쓴다.

| 역할 | Material 슬롯 | 라이트 | 다크 | 코드 이름 |
|---|---|---|---|---|
| 종이(화면 배경) | background | `#F7F8FA` | `#101418` | `Paper*` |
| 카드 | surface | `#FFFFFF` | `#1B2027` | `Card*` |
| 잉크(본문) | onSurface, onBackground | `#191F28` | `#E9EDF2` | `Ink*` |
| 보조 글자 | onSurfaceVariant | `#6B7684` | `#8B95A1` | `SubInk*` |
| 행동(블루) | primary | `#1B6EF3` | `#5B95F8` | `Blue*` |
| 연블루 면 | primaryContainer | `#EAF1FE` | `#1E2C42` | `BlueContainer*` |
| 회색 pill 칩 | secondaryContainer, surfaceVariant | `#F2F4F6` | `#242B34` | `Chip*` |
| 칩 글자 | onSecondaryContainer, secondary | `#4E5968` | `#B0B8C1` | `ChipInk*` |
| 경고 면(보호 배너) | tertiaryContainer | `#FFF4E0` | `#332916` | `AmberContainer*` |
| 경고 글자 | onTertiaryContainer | `#96660A` | `#F0C070` | `AmberInk*` |
| 오류 | error | `#F04452` | `#FF6B6B` | `Error*` |
| 헤어라인 | outlineVariant | `#E5E8EB` | `#232A33` | `Line*` |
| 테두리 | outline | `#C9D0D8` | `#3A424D` | `Outline*` |
| 성공(완료) | — (`successColor()`) | `#12B76A` | `#2ED07E` | `Success*` |

- 다크 블루는 채도를 낮췄다. 어두운 화면에서 눈부시지 않게 하기 위해서다.
- 경고(앰버)와 오류(빨강)를 구분한다. 보호 상태가 꺼진 것은 경고다.

### 2.2 타입

글꼴은 Pretendard 정적 4웨이트(400·500·600·700, `res/font/`)다. 자간 기본값은 -0.005em이다.

| Material 슬롯 | 크기/행간(sp) | 굵기 | 자간(em) | 쓰임 |
|---|---|---|---|---|
| headlineMedium | 28 / 36 | Bold | -0.02 | 홈 큰 타이틀 |
| headlineSmall | 24 / 32 | Bold | -0.02 | 온보딩 타이틀 |
| titleLarge | 18 / 24 | SemiBold | -0.01 | TopAppBar 타이틀 |
| titleMedium | 17 / 24 | SemiBold | -0.01 | 카드(기록) 제목 |
| titleSmall | 15 / 21 | SemiBold | -0.005 | 행 제목, 강조 본문 |
| bodyLarge | 15 / 22 | Regular | -0.005 | 기본 본문 |
| bodyMedium | 14 / 20 | Regular | -0.005 | 본문(좁은 곳) |
| bodySmall | 13 / 18 | Regular | -0.005 | 보조 캡션 |
| labelLarge | 15 / 20 | SemiBold | -0.005 | 버튼 |
| labelMedium | 12 / 16 | SemiBold | 0.02 | 섹션 레이블 |
| labelSmall | 12 / 16 | Medium | 0 | 칩, 배지 |

숫자를 세로로 맞춰야 하는 곳(거리 배지, 진단 시각)은 tabular-nums(`ui/common`의 `tabularNums()`)를 쓴다.

### 2.3 형태

| 토큰 | 값 | 쓰임 |
|---|---|---|
| shapes.extraSmall | 8dp | — |
| shapes.small | 12dp | 입력 필드, 작은 면 |
| shapes.medium | 20dp | 카드(Material3 Card 기본) |
| shapes.large | 24dp | 시트, 다이얼로그 |
| shapes.extraLarge | 28dp | — |
| `PillShape` | 50% | 칩, 배지, 상태 pill |

### 2.4 간격

화면 좌우 여백과 카드 그리드의 기준은 20dp다. 아직 토큰이 아니라 화면 코드에 dp 리터럴로 있다. 현재 많이 쓰는 값은 8·20·16·12·24·10dp 순이다. 다음 UI 작업에서 `Spacing` 토큰으로 옮기고, 그때 값의 단계를 이 절에 확정한다.

### 2.5 모션

토큰은 아직 없다. 현재 쓰는 모션은 두 가지다.
- 홈 목록의 항목 등장·제거·재배열: `Modifier.animateItem()`
- 스와이프 완료: `SwipeToDismissBox`. 성공 색 배경에 체크를 표시한다.

§1.3의 마이크로 인터랙션을 넣을 때 지속 시간과 이징을 이 절에 토큰으로 정한다.

## 3. 컴포넌트 (현재 구현)

화면마다 `private`으로 두고 쓰는 것이 많다. 두 화면 이상에서 쓰게 되면 `ui/common`으로 옮긴다.

| 컴포넌트 | 위치 | 쓰임 |
|---|---|---|
| 기록 카드 `ReminderRow` | home | 제목 + 트리거 칩, 스와이프로 완료 |
| 트리거 칩 `TriggerChips` | home | 회색 pill(secondaryContainer) |
| 보호 배너 | home | 경고 면(tertiaryContainer). 보호가 꺼졌을 때 |
| 확장 FAB "＋ 기록" | home | 새 기록 |
| 거리 배지 `DistanceBadge` | `ui/common` | 연블루 pill + 블루 숫자, 999m 넘으면 km 한 자리 |
| 주변 POI 카드 `NearbyPoiCard` | nearby | 그룹 헤더(이모지 + SemiBold) 아래 카드 |
| TopAppBar | editor, settings, nearby, diagnostics | 상태바 인셋을 처리하는 상단 바. 에디터는 ✕ · 제목 · 저장, 나머지는 뒤로 · 제목 |
| 섹션 레이블 `SectionLabel` | editor, settings | labelMedium. **두 화면에 각각 있다 — 공통화 후보** |
| 입력 필드 `EditorField` · 제거 가능 칩 `RemovableChip` · 장소 결과 카드 `PlaceResultCard` | editor | 기록 편집 |
| 그룹 카드 `GroupCard` · 상태 pill `StatusPill` | settings | 보호 상태, 알림 정책, 문제 해결 묶음. pill은 "켜짐"/"꺼짐" |
| 진단 행 `DiagnosticsRow` | diagnostics | 결과별 8dp 컬러 도트: APPLIED·PASS 성공색, BLOCK·FAILED·ERROR 오류색, 나머지(STOOD_DOWN·STALE 등) 테두리 회색 |
| 핀 그래픽 `PinMarkGraphic` · 왜 카드 `WhyCard` · 페이지 도트 `PageDots` · 하단 버튼 `PrimaryButton` | onboarding | 단계별 권한 안내 |

## 4. 작업 절차

- **새 화면, 눈에 띄는 시각 변경**: HTML 목업(라이트·다크)을 만들고 → 사용자가 승인하면 → 구현하고 → 실기기에서 라이트·다크 스크린샷으로 확인한다(`tools/device/e2e.sh`).
- **기존 컴포넌트 안에서의 작은 수정**(문구, 간격 조정, 상태 추가): 목업 없이 바로 한다.
- 목업과 승인 기록은 개편안 문서처럼 `docs/superpowers/specs/YYYY-MM-DD-<주제>.md`에 남긴다. 확정된 토큰·컴포넌트는 이 문서에 반영한다.

## 5. 코드 규칙

- 색·글꼴은 `MaterialTheme.colorScheme`·`MaterialTheme.typography`와 이 문서의 헬퍼(`successColor()`, `PillShape`, `tabularNums()`)로만 쓴다. 화면 코드에 `Color(0x…)`나 `.sp` 리터럴을 쓰지 않는다. 현재 화면 코드는 이 규칙을 지키고 있다.
- 간격은 §2.4의 `Spacing` 토큰을 도입한 뒤로는 토큰만 쓴다.
- 새로 만들거나 고치는 화면에는 `@Preview`를 라이트·다크 두 벌 둔다. 이 규칙은 다음 UI 작업에서 도입한다. 현재 `@Preview`는 0개다.
- 사용자에게 보이는 문자열은 ko·en 둘 다 넣는다(CLAUDE.md 작업 규칙).

## 변경 이력

| 버전 | 날짜 | 바뀐 § | 무엇·왜 | 근거 |
|---|---|---|---|---|
| 1.1 | 2026-10-09 | §3 | 진단 행 도트에 PASS(발화, 성공색)·ERROR(리시버 오류, 오류색)를 더하고 더는 기록되지 않는 NO_PERMISSION을 뺐다 | 보강 계획 T9·T10, 묶음 C 최종 리뷰 |
| 1.0 | 2026-10-09 | 전체 | 최초 작성. 개편안(2026-09-03)의 결정과 현재 코드 값을 옮기고, 레퍼런스(토스·당근·헤이딜러)와 방향을 더했다. | 프로젝트 규칙 확정(2026-10-09) |
