# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

P의기록(Record of P) — 카테고리/브랜드("아무 편의점이나") 단위로 할 일을 걸어두면 해당 장소 근처를 **도보로** 지날 때 알림을 주는 위치 기반 리마인더. Android(Kotlin) v1.0 선출시 → iOS(Swift) 포팅 예정.

- **설계 문서(단일 진실 원천)**: `docs/superpowers/specs/2026-08-31-record-of-p-design.md` — 코드·커밋은 이 문서의 §번호를 근거로 인용한다.
- **구현 계획(13 태스크, TDD)**: `docs/superpowers/plans/2026-08-31-record-of-p-v1.md` — 구현 완료(`feat/v1`, PR #1). 계획의 Global Constraints는 이후 작업에도 모두 적용된다. 태스크 단위 실행은 `superpowers:subagent-driven-development`(권장) 또는 `superpowers:executing-plans`로 한다.
- **실기기 검증 기록**: `docs/superpowers/notes/2026-09-01-device-verification-round1.md` — 통과 항목, 발견 사항(F0~F5)과 해결 커밋, 남은 검증 목록.
- **디자인 개편안(클린 미니멀)**: `docs/superpowers/specs/2026-09-03-design-refresh-clean-minimal.md` — 현재 UI의 기준.
- **계획 검토 결과**: `docs/superpowers/reviews/2026-09-29-v1-plan-review.md` — 원래 계획의 결함 목록이다. 검토가 구현보다 늦게 나와서 **현재 `feat/v1` 코드에는 반영되지 않았다**. 이 검토를 반영한 별도 구현이 `archive/v1-local` 브랜치에 있다(푸시하지 않음, 참고 구현). 현재 코드에 없는 수정은 이식 계획으로 옮긴다. C4~C7은 출시 전 체크리스트로 보류했다.
- **보강 이식 계획(15 태스크, TDD)**: `docs/superpowers/plans/2026-10-03-v1-hardening-port.md` — 검토와 `archive/v1-local`의 수정 중 원격에 없는 것을 옮긴다(엔진 신뢰성 → 프라이버시·권한 → 알림 정확도 → UI). 코드 주석의 `최종 리뷰 C1`·`I2` 같은 번호는 로컬 최종 리뷰 번호다.

## 명령 (macOS — 항상 `android/`에서 실행)

2026-08-31 계획서의 명령은 Windows 기준(`.\gradlew.bat`, `Select-String`)이다. macOS에서는 `./gradlew`, `grep`으로 바꿔 실행한다.

```bash
cd android
./gradlew testDebugUnitTest                                   # JVM 단위 테스트 전체
./gradlew testDebugUnitTest --tests "*.ReseedPlannerTest"     # 클래스 하나
./gradlew testDebugUnitTest --tests "*.NotificationGateTest.방해금지*"  # 메서드(백틱 한글 이름) 와일드카드
./gradlew lintDebug                                           # CI 게이트 (현재 경고만, 오류 0)
./gradlew testDebugUnitTest lintDebug assembleDebug           # CI와 동일한 전체 검증
./gradlew installDebug                                        # 기기/에뮬레이터 설치
```

- 테스트 결과: `android/app/build/test-results/testDebugUnitTest/*.xml`, lint 리포트: `android/app/build/reports/lint-results-debug.txt`
- `adb`: `~/Library/Android/sdk/platform-tools/adb` (`.claude/settings.json`의 허용 규칙은 PATH상의 `adb`를 기준으로 한다). AVD: `Pixel_Tablet_API_30`(에뮬레이터 위치 주입은 `adb emu geo fix <lng> <lat>` — 경도가 먼저). 실기기 검증 헬퍼는 `tools/device/`(`e2e.sh`, `dbdump.py`)에 있다. Git Bash 기준이므로 macOS에서는 `ADB=~/Library/Android/sdk/platform-tools/adb`로 덮어써서 쓴다.
- `android/local.properties`(gitignore): `sdk.dir`과 `KAKAO_REST_KEY`. 키가 없어도 빌드·테스트는 통과하고 POI 조회만 실패한다(CI도 키 없이 빌드). 이 파일은 읽거나 출력하지 않는다.
- JDK 17, AGP 8.10 / Kotlin 2.1.21 / Gradle 8.14.3, compileSdk·targetSdk 36, minSdk 26. 의존성은 `android/gradle/libs.versions.toml` 버전 카탈로그로만 추가한다.

## 아키텍처 (큰 그림)

단일 `:app` 모듈에서 패키지 경계로 계층을 나눈다. **의존 방향: `ui → data/repo → domain`, `platform → domain + data`.** ui는 platform을 import하지 않는다(MainActivity 같은 조립 지점만 예외). data가 platform 기능이 필요하면 data에 포트(인터페이스)를 두고 platform이 구현한다(예: `FenceApplier`, `ReseedRequester`, `LocationProvider`).

- `domain/engine` — **순수 Kotlin. Android/GMS import 금지.** JVM 테스트와 iOS 포팅의 기반이다. 시간은 `java.time.Clock`/`Instant`/`ZoneId`로 주입하고 `System.currentTimeMillis()`는 쓰지 않는다.
- `EngineParams` — 오발화/미발화 튜닝 상수(반경 120m, DWELL 60s, 센티널 1km, 예산 95, 쿨다운 등)가 **모두** 여기 모인다. 다른 파일에 리터럴로 중복하지 않는다. 필드 테스트 튜닝 이력은 이 파일의 커밋으로 남긴다.

### 두 개의 핵심 파이프라인

1. **재배치(Reseed)** — 어떤 지오펜스를 OS에 등록할지 결정한다.
   원인(`BOOT`/`SENTINEL_EXIT`/`PERIODIC`/`APP_OPEN`/`ITEM_CHANGE`/`RETRY`) → `ReseedWorker`(WorkManager) → 판정(`ReseedGovernor`) → 활성 트리거를 matchKey 단위 조회 요청으로 해석(`TriggerResolver`) → 카카오 POI 조회(`PoiRepository`) → `ReseedPlanner.plan()` → 현재 등록분과 차분(`DiffCalculator`) → `GeofenceController`로 OS 적용 → `geofence_reg`/`reg_trigger` 미러 갱신 → `EngineRunLog` 기록.
   - 앱이 위치를 폴링하는 코드는 금지다. 이동 감지는 현재 위치 중심 반경 1km **EXIT 센티널 펜스**로 한다.
   - POI 조회가 실패하면 **기존 등록을 지우지 않고** 유지한 뒤 백오프 재시도한다(§6.4).
   - 큐: APP_OPEN 이외의 원인은 `reseed_now` 하나를 `REPLACE`로 공유하고, APP_OPEN만 `reseed_opportunistic`(`KEEP`)을 쓴다. 따라서 대기 중이거나 **실행 중인** 재배치가 다른 원인으로 대체될 수 있다. `ReseedService`는 Mutex로 reseed·standDown을 직렬화한다. BOOT·PERIODIC은 미러와 상관없이 계획된 펜스를 전부 다시 등록한다.
2. **이벤트(Notification)** — 지오펜스 전이 → `GeofenceBroadcastReceiver`(goAsync) → 미러에 없는 id(stale)는 폐기 → `reg_trigger`→`trigger_spec`→`reminder` 로드 → `NotificationGate` 필터 체인(상태→스누즈→방해금지→항목 쿨다운→항목·지점 쿨다운→항목당 일 상한→전체 일 상한) → POI 단위로 묶어 알림 1건 발행. `NotificationLog`는 알림이 실제로 표시된 뒤에 `recordShown`으로 기록한다. 차단 사유는 진단 화면용으로 `EngineRunLog`에 남긴다.

### 알아두어야 할 개념

- **matchKey**: 트리거의 안정 키 — `cat:<catalogId>`, `brand:<trim+lowercase 키워드>`, `place:<triggerSpecId>`. 리마인더 여러 개가 같은 카테고리를 쓰면 조회는 1번만 한다(카카오 쿼터 방어). 한 POI 펜스가 여러 matchKey를 대변할 수 있다(N:M, `reg_trigger`).
- **펜스 키 = OS requestId = `geofence_reg.geofenceId`**: `sentinel`, `poi:<kakaoId>`, `place:<id>`. 키가 안정적이어야 차분 적용이 성립한다(uuid 발급 금지).
- **`geofence_reg`는 OS 등록 상태의 미러**다. OS 쪽 등록은 재부팅, 앱/Play 서비스 데이터 삭제, `GEOFENCE_NOT_AVAILABLE` 수신 시 사라지지만 미러는 남는다. 미러를 기준으로 차분하는 코드는 이 불일치를 반드시 고려해야 한다(검토 문서 참고).
- **트리거 카탈로그**(`TriggerCatalog`)는 코드에 내장한다(DB에 두지 않음). 카카오 코드(`CS2` 등)로 해석하거나, 코드가 없는 업종·브랜드는 키워드 검색으로 해석한다. 표시명은 strings.xml에서 `cat_<id>`로 매핑한다.
- **카카오 로컬 API**: `x`=경도, `y`=위도이고 좌표·거리가 **문자열**로 온다. radius ≤ 20,000m, size ≤ 15, `sort=distance`. 재배치 1회당 최대 2페이지.
- 알림 채널은 `nearby`(높음)와 `status`(낮음) 두 개다(`Notifier`).

## 작업 규칙 (계획서 Global Constraints 요약 + 저장소 관례)

- TDD: 순수 로직 클래스를 만들고 JVM 테스트를 쓴 뒤, 플랫폼 접착 코드는 얇게 둔다. 테스트 더블은 모킹 라이브러리 없이 인터페이스를 직접 구현한 페이크로 만든다(DAO 인터페이스도 페이크로 구현).
- 테스트 메서드는 백틱 한글 문장으로 쓴다(`` `스누즈 중이면 차단되고, 해제 시각부터 통과한다` ``).
- 사용자에게 보이는 문자열은 `res/values/strings.xml`(ko)과 `res/values-en/strings.xml`(en)에 **둘 다** 추가한다.
- 라이브러리는 계획에 명시된 것(coroutines-test, mockwebserver, turbine)만 추가한다.
- 위치 데이터를 기기 밖으로 보내는 코드는 금지한다. 외부 통신은 `KakaoLocalApi` 하나뿐이고 분석·광고 SDK는 넣지 않는다(§9).
- 배터리 최적화 예외는 자동으로 요청하지 않는다(Play 정책). 설정 화면으로 안내만 한다.
- 스켈레톤의 `TODO(v1): §…` 마커는 해당 기능을 구현할 때 제거한다.
- 커밋 메시지는 `feat:`/`fix:`/`test:`/`refactor:`/`docs:`/`build:`/`chore:` 접두어 + 한글 요약, 본문에 스펙 §번호를 적는다. 계획 태스크 하나가 끝날 때마다 커밋한다.
- 취소 예외 관례: `catch (e: Exception)` 앞에서 `CancellationException`을 다시 던진다(`catch (c: CancellationException) { throw c }`). 취소를 실패(FAILED)로 기록하지 않기 위해서다.
