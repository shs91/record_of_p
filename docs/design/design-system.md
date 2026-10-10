# P의기록 디자인 시스템

| | |
|---|---|
| 상태 | 승인 — 현재 UI의 기준 |
| 버전 | 1.3 |
| 최종 수정 | 2026-10-10 |
| 구현 | Android `ui/theme/{Color,CategoryColors,Type,Shape,Spacing,Motion,Theme,Previews}.kt`, 아이콘 `res/drawable/ic_cat_*`·`ic_trigger_*`·`ic_banner_*`·`ic_alert_error` · iOS 미착수 |
| 근거 | 디자인 개편안 `docs/superpowers/specs/2026-09-03-design-refresh-clean-minimal.md`, 개편안 2 `docs/superpowers/specs/2026-10-10-design-refresh-heydealer.md`(결정 기록)과 현재 코드 값 |

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

바탕을 해치지 않는 선에서 화면에 성격을 준다. 실제 모양은 개편안 2(방향 B "카테고리 타일", 2026-10-10 승인)에서 정했다. 카드 왼쪽에 카테고리 색 타일을 두어 목록을 훑을 때 장소 종류가 먼저 보이게 한다.

- **숫자 강조**: 거리·개수처럼 사용자가 판단에 쓰는 숫자를 크고 굵게 보여 준다. (§2.2 추가 스타일 — 홈 개수 pill, 주변 보기 거리)
- **브랜드 그래픽**: P-핀 모티프를 빈 상태, 온보딩, 완료 순간에 쓴다. (홈 빈 상태)
- **마이크로 인터랙션**: 완료 체크, 카드 눌림, 항목 등장에 짧은 모션을 준다. (§2.5)
- **카테고리 컬러 포인트**: 카테고리마다 색을 하나씩 정해 칩·아이콘 배경으로 구분한다. (§2.6)

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
| 보조 글자 | onSurfaceVariant | `#636E7C` | `#8B95A1` | `SubInk*` |
| 행동(블루) | primary | `#1B6EF3` | `#5B95F8` | `Blue*` |
| 연블루 면 | primaryContainer | `#EAF1FE` | `#1E2C42` | `BlueContainer*` |
| 블루 글자(연블루·카드·종이 위) | onPrimaryContainer | `#1762D8` | `#5B95F8` | `BlueTextLight` · 다크는 `BlueDark` |
| 회색 pill 칩 | secondaryContainer, surfaceVariant | `#F2F4F6` | `#242B34` | `Chip*` |
| 칩 글자 | onSecondaryContainer, secondary | `#4E5968` | `#B0B8C1` | `ChipInk*` |
| 경고 면(보호 배너) | tertiaryContainer | `#FFF4E0` | `#332916` | `AmberContainer*` |
| 경고 글자 | onTertiaryContainer | `#96660A` | `#F0C070` | `AmberInk*` |
| 오류 | error | `#D62C3A` | `#FF6B6B` | `Error*` |
| 헤어라인 | outlineVariant | `#E5E8EB` | `#232A33` | `Line*` |
| 테두리(입력 필드) | outline | `#878F9B` | `#646E7C` | `Outline*` |
| 성공(완료) | — (`successColor()`) | `#0A8049` | `#2ED07E` | `Success*` |
| 성공 면 위 글자 | — (`onSuccessColor()`) | `#FFFFFF` | `#1B2027` | `OnSuccess*` |
| 스낵바 바탕 | inverseSurface | `#191F28` | `#E9EDF2` | `InverseSurface*` |
| 스낵바 글자 | inverseOnSurface | `#F7F8FA` | `#101418` | `InverseOnSurface*` |
| 스낵바 액션 | inversePrimary | `#9EC1FF` | `#1762D8` | `InversePrimary*` |
| 스낵바 성공 원 | — (`inverseSuccessColor()`) | `#2ED07E` | `#0A8049` | `InverseSuccess*` |

- 다크 블루는 채도를 낮췄다. 어두운 화면에서 눈부시지 않게 하기 위해서다.
- 경고(앰버)와 오류(빨강)를 구분한다. 보호 상태가 꺼진 것은 경고다.
- 글자는 WCAG 4.5:1, 테두리·아이콘은 3:1을 넘는다. 라이트의 보조 글자·블루 글자·오류·테두리·성공은 이 기준에 맞춰 보정했다(개편안 2 §1.1). 오류는 개편안의 `#DC2E3C`보다 한 단계 어두운 `#D62C3A`로, 종이 위에서도 4.5:1을 넘는다. `ThemeContrastTest`가 쓰이는 면 위의 대비를 확인한다.
- `primary`는 종이 위 글자로 4.32:1이라 기준에 못 미친다. 블루 면 위 흰 글자(4.59:1)와 아이콘에만 쓰고, 종이·카드·연블루 위의 블루 글자(거리 숫자, 에디터 [저장]·[추가])는 `onPrimaryContainer`를 쓴다.
- 근처 알림 강조색(`setColor`)은 `res/values{,-night}/colors.xml`의 `notification_accent`다. primary와 같은 값이고, platform 코드가 `ui/theme`을 쓰지 않도록 리소스로 둔다.

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

Material 슬롯 밖의 스타일은 `AppTextStyles`(`Type.kt`)에 둔다.

| 이름 | 크기/행간(sp) | 굵기 | 자간(em) | 쓰임 |
|---|---|---|---|---|
| `numberLarge` | 22 / 26 | Bold, tabular | -0.02 | 주변 보기 거리 숫자 |
| `emptyTitle` | 20 / 28 | Bold | -0.01 | 빈 상태 제목 |

굵기만 바꿀 때는 슬롯 스타일의 `copy(fontWeight = …)`를 쓴다(개수 pill·완료 글자 Bold, 칩 Medium·SemiBold). 크기를 새로 만들지 않는다.

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

`ui/theme/Spacing.kt`의 단계를 쓴다.

| 토큰 | 값 | 쓰임 |
|---|---|---|
| `xxs` | 4dp | 단계 칩 사이, 제목 위 |
| `xs` | 8dp | 칩 사이, 섹션 안 줄 |
| `s` | 12dp | 입력 사이, 배너 안 |
| `m` | 16dp | 배너·필드 안쪽, 스낵바 좌우 |
| `l` | 20dp | 화면 좌우 여백(`screen`), 주변 보기 그룹 사이 |
| `xl` | 24dp | 화면 아래 여백 |
| `xxl` | 32dp | 빈 상태 좌우 |

단계 밖의 값(카드 사이 10, 카드 안쪽 14, 칩 패딩 9, 행 좌우 18 등)은 그 컴포넌트 안에 둔다. 온보딩·설정·진단 화면은 다음에 다시 그릴 때 토큰으로 옮긴다.

### 2.5 모션

`ui/theme/Motion.kt`의 값을 쓴다. 이징은 Material 표준(`FastOutSlowInEasing`)이다.

| 토큰 | 값 | 쓰임 |
|---|---|---|
| `SHORT_MS` | 150ms | 칩 선택 색 전환 |
| `STANDARD_MS` | 250ms | 홈 카드 등장·제거·재배열(`animateItem`) |

스낵바는 Material `SnackbarHost`의 기본 전환을 쓴다. 스와이프 완료는 `SwipeToDismissBox`(시작→끝 한 방향)다.

### 2.6 카테고리 색

카테고리마다 타일 바탕(tint)과 아이콘·글자(ink) 한 쌍을 둔다. ink는 tint 위에서 라이트 4.5:1, 다크 6:1을 넘는다. 색은 아이콘·이름과 함께 쓴다 — 색만으로 구분하지 않는다. 코드는 `CategoryPalette`(`ui/theme/CategoryColors.kt`)이고, 화면은 `TriggerVisual.tileColors()`(`ui/common`)로 쓴다. 도메인 카탈로그에는 색을 넣지 않는다.

| 카테고리 | 라이트 tint | 라이트 ink | 다크 tint | 다크 ink |
|---|---|---|---|---|
| 편의점 | `#E5F6EC` | `#137444` | `#163024` | `#5CCB8C` |
| 대형마트 | `#FFF0E2` | `#B4520A` | `#3A2614` | `#FF9F57` |
| 약국 | `#FCEAF3` | `#B42A72` | `#3A1A2D` | `#F27DBB` |
| 은행 | `#ECEEFC` | `#3A49B8` | `#1F2444` | `#9AA5F7` |
| 우체국 | `#FDECE7` | `#B83A18` | `#3B1F17` | `#FF8C69` |
| 주유소·충전소 | `#E2F4F4` | `#0C7276` | `#123335` | `#52C7CC` |
| 세탁소 | `#F1EAFD` | `#6A3CBC` | `#2A1F42` | `#B99AF7` |
| 카페 | `#F4EDE6` | `#835532` | `#2F251D` | `#D9A67E` |
| 병원 | `#E3F2F9` | `#0B6A8F` | `#12303D` | `#5CC0E6` |
| 지하철역 | `#EDF5DE` | `#4F7212` | `#243016` | `#B0D66A` |
| 브랜드(프리셋·직접 입력)·카탈로그에 없는 id | secondaryContainer | onSecondaryContainer | 같음 | 같음 |
| 특정 지점 | primaryContainer | onPrimaryContainer | 같음 | 같음 |

### 2.7 아이콘

카테고리·트리거 아이콘은 Material Symbols Rounded(Apache 2.0, 0.47.0 고정)를 VectorDrawable로 가져와 쓴다. `tools/design/material_symbols.py`의 `ICONS`에 한 줄 넣고 저장소 루트에서 `python3 -I tools/design/material_symbols.py`를 다시 실행한다. drawable을 손으로 고치지 않고, 아이콘 라이브러리를 더하지 않는다. P-핀 마크(`ic_pin_mark`, 알림용 `ic_stat_pin`)는 직접 그린 브랜드 그래픽이다.

| 쓰임 | drawable | Material Symbols |
|---|---|---|
| 편의점 · 대형마트 · 약국 · 은행 · 우체국 | `ic_cat_convenience` · `ic_cat_mart` · `ic_cat_pharmacy` · `ic_cat_bank` · `ic_cat_post` | storefront · shopping_cart · medication · account_balance · local_post_office |
| 주유소 · 세탁소 · 카페 · 병원 · 지하철역 | `ic_cat_fuel` · `ic_cat_laundry` · `ic_cat_cafe` · `ic_cat_hospital` · `ic_cat_subway` | local_gas_station · checkroom · local_cafe · local_hospital · subway |
| 브랜드 프리셋 · 브랜드 직접 입력 · 특정 지점 | `ic_trigger_brand` · `ic_trigger_search` · `ic_trigger_place` | shopping_bag · search · location_on |
| 보호 배너: 알림 꺼짐 · 정확한 위치 · 기기 위치 꺼짐 ('항상 허용'은 `ic_trigger_place`) | `ic_banner_notifications_off` · `ic_banner_precise` · `ic_banner_location_off` | notifications_off · my_location · location_off |
| 저장 실패 | `ic_alert_error` | error |

화면은 `TriggerVisual.iconRes()`로 고른다. 카탈로그의 이모지(`TriggerCatalog.emoji`)는 화면에서 쓰지 않는다.

## 3. 컴포넌트 (현재 구현)

화면마다 `private`으로 두고 쓰는 것이 많다. 두 화면 이상에서 쓰게 되면 `ui/common`으로 옮긴다.

| 컴포넌트 | 위치 | 쓰임 |
|---|---|---|
| 트리거 시각 `TriggerVisual` · 트리거 타일 `TriggerTile` | `ui/common` | 트리거 → 타일 색·아이콘·이름. 타일은 홈 카드 48dp, 빈 상태 44dp, 주변 보기 그룹 머리 32dp |
| 기록 카드 `ReminderRow` | home(`ReminderCard.kt`) | 대표 트리거 타일(카테고리 → 특정 지점 → 브랜드) + 제목(두 줄) + 트리거 이름 줄(한 줄). 시작→끝 스와이프 완료(성공 면·원 체크), TalkBack 사용자 지정 동작 "완료" |
| 개수 pill | home | 큰 타이틀 옆, 잉크 바탕·종이 글자 15 Bold tabular. 0개면 숨김, TalkBack은 "N개" |
| 빈 상태 `HomeEmptyState` | home | 연블루 원 안 P-핀 + 카테고리 타일 셋(편의점·약국·카페), 제목 20 Bold + 본문 두 줄 |
| 보호 배너 `ProtectionBanner` | home | 앰버 면 20dp, 36dp 원 아이콘, 제목·본문, '항상 허용' 단계 칩(마지막만 강조, TalkBack은 쉼표로 끊어 읽음), [설정 열기] pill. 기록이 있으면 목록 첫 항목으로 함께 스크롤되고, 0개면 빈 상태와 한 스크롤 영역에 놓인다 |
| 실행 취소 스낵바 `UndoSnackbar` | home | inverse 면 16dp, 성공 체크 원, [실행 취소](inversePrimary) |
| 확장 FAB "＋ 기록" | home | 새 기록. 실행 취소 스낵바는 FAB 위에 뜬다(Material 3 Scaffold 기본) |
| 거리 배지 `DistanceBadge` | `ui/common` | 에디터 장소 결과 — 연블루 pill + onPrimaryContainer 숫자, 999m 넘으면 km 한 자리 |
| 주변 보기 그룹 `NearbyGroupSection` · 지점 행 `NearbyPoiRow` | nearby | 32dp 타일 + 이름 + "N곳", 그룹마다 카드 하나에 헤어라인 행(64dp). 거리 숫자 22 Bold(1km 넘으면 보조색) + 단위 |
| TopAppBar · 뒤로 `BackButton` | editor, settings, nearby, diagnostics · `ui/common` | 상태바 인셋을 처리하는 상단 바. 에디터는 ✕ · 제목 · 저장(onPrimaryContainer), 나머지는 뒤로(TalkBack "뒤로") · 제목 |
| 에디터 `EditorSection` · `EditorField` · `CategoryChip` · `RemovableChip` · `AddBrandButton` · `SearchPlaceButton` · `SaveFailedNotice` · `PlaceResultCard` | editor(`EditorComponents.kt`) | 섹션 제목 15 SemiBold, 카드 바탕 입력(1dp 테두리·포커스 2dp 블루, 56dp), 카테고리 칩(선택 = tint + ink 테두리 + 체크), 브랜드 [추가]·안내, 고른 브랜드(회색)·지점(연블루) 칩(TalkBack은 '<이름> 삭제' 버튼으로 읽음), 저장 실패 오류 면(실패하면 맨 위로 스크롤) |
| 섹션 레이블 `SectionLabel` | settings | labelMedium. 에디터는 섹션 제목으로 바뀌어 설정에만 남았다 |
| 그룹 카드 `GroupCard` · 상태 pill `StatusPill` | settings | 보호 상태, 알림 정책, 문제 해결 묶음. pill은 "켜짐"/"꺼짐" |
| 진단 행 `DiagnosticsRow` | diagnostics | 결과별 8dp 컬러 도트: APPLIED·PASS 성공색, BLOCK·FAILED·ERROR 오류색, 나머지(STOOD_DOWN·STALE 등) 테두리 회색 |
| 핀 그래픽 `PinMarkGraphic` · 왜 카드 `WhyCard` · 페이지 도트 `PageDots` · 하단 버튼 `PrimaryButton` | onboarding | 단계별 권한 안내 |

## 4. 작업 절차

- **새 화면, 눈에 띄는 시각 변경**: HTML 목업(라이트·다크)을 만들고 → 사용자가 승인하면 → 구현하고 → 실기기에서 라이트·다크 스크린샷으로 확인한다(`tools/device/e2e.sh`).
- **기존 컴포넌트 안에서의 작은 수정**(문구, 간격 조정, 상태 추가): 목업 없이 바로 한다.
- 목업과 승인 기록은 개편안 문서처럼 `docs/superpowers/specs/YYYY-MM-DD-<주제>.md`에 남긴다. 확정된 토큰·컴포넌트는 이 문서에 반영한다.

## 5. 코드 규칙

- 색·글꼴은 `MaterialTheme.colorScheme`·`MaterialTheme.typography`와 이 문서의 헬퍼로만 쓴다: `successColor()`·`onSuccessColor()`·`inverseSuccessColor()`, `TriggerVisual.tileColors()`(카테고리 색), `AppTextStyles`, `PillShape`, `tabularNums()`. 화면 코드에 `Color(0x…)`나 `.sp` 리터럴을 쓰지 않는다.
- 간격은 §2.4의 `Spacing` 단계에 있는 값을 토큰으로 쓴다. 단계 밖의 값은 컴포넌트 안에 둔다.
- 새로 만들거나 다시 그리는 화면은 본체를 상태와 동작만 받는 `XxxContent`로 떼고, `@LightDarkPreviews`(라이트·다크 두 벌)와 큰 글꼴(`fontScale = 2f`) 미리보기를 둔다. 미리보기 안은 `RecordOfPTheme { }`를 기본값으로 감싼다.
- 고정 높이 대신 `heightIn(min = …)`을 쓴다(큰 글꼴). 색은 아이콘·이름과 함께 쓴다 — 색만으로 구분하지 않는다.
- 사용자에게 보이는 문자열은 ko·en 둘 다 넣는다(CLAUDE.md 작업 규칙).

## 변경 이력

| 버전 | 날짜 | 바뀐 § | 무엇·왜 | 근거 |
|---|---|---|---|---|
| 1.3 | 2026-10-10 | §2.1, §2.7, §3 | 개편안 2 컴포넌트 반영 — 카테고리 타일 기록 카드·개수 pill·빈 상태, 보호 배너, 실행 취소 스낵바, 에디터 컴포넌트, 주변 보기 그룹·행, 트리거 타일·뒤로 버튼 공통화, 알림 강조색, 배너·오류 아이콘. 최종 리뷰 반영: 배너·빈 상태 스크롤, 스낵바는 FAB 위, 칩·단계 접근성 의미 | 개편안 2 §2, 보강 계획 Task 13·14·17~20, 묶음 D 최종 리뷰 |
| 1.2 | 2026-10-10 | §1.3, §2.1, §2.2, §2.4, §2.5, §2.6, §2.7, §5 | 개편안 2 토큰 반영 — 라이트 대비 보정(보조 글자·블루 글자·오류·테두리·성공), 완료·스낵바 색, 카테고리 10색, 글자 추가 스타일, 간격·모션 토큰, Material Symbols 아이콘, 미리보기·간격 규칙. 오류색은 종이 위 4.5:1을 위해 개편안의 `#DC2E3C` 대신 `#D62C3A` | 개편안 2 §1·§4(2026-10-10 승인), 보강 계획 Task 16 |
| 1.1 | 2026-10-09 | §3 | 진단 행 도트에 PASS(발화, 성공색)·ERROR(리시버 오류, 오류색)를 더하고 더는 기록되지 않는 NO_PERMISSION을 뺐다 | 보강 계획 T9·T10, 묶음 C 최종 리뷰 |
| 1.0 | 2026-10-09 | 전체 | 최초 작성. 개편안(2026-09-03)의 결정과 현재 코드 값을 옮기고, 레퍼런스(토스·당근·헤이딜러)와 방향을 더했다. | 프로젝트 규칙 확정(2026-10-09) |
