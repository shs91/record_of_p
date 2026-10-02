# v1 구현 계획 검토 (2026-09-29)

- 대상: `docs/superpowers/plans/2026-08-31-record-of-p-v1.md` + 커밋 `c051dc2` 시점 코드
- 기준선: `./gradlew testDebugUnitTest assembleDebug` 성공 (단위 테스트 19/19 통과), `lintDebug` 오류 0 / 경고 18(의존성 버전 권고 16, ObsoleteSdkInt 등)
- 상태 표기: **[미결]** = 계획에 반영할지 사용자 결정 필요. 반영이 확정되면 해당 태스크 본문을 수정하고 이 표기를 **[반영]**으로 바꾼다. **[보류]** = 출시 전 체크리스트로 미룸. **[수용]** = 편차를 알고 받아들임.
- 2026-09-29 결정: A1, B1~B8, C1~C3 반영(계획서 "검토 반영" 절 참고), C4~C7 보류, C8 수용.

## A. 계획대로 실행하면 막히는 곳

### A1. `GeofenceRegEntity`에 `matchKey` 필드가 없다 — T5·T7 컴파일 실패 [반영 — T5 Step 1]
T5의 `ReseedServiceTest`·`ReseedService.toEntity()`와 T7의 `GeofenceEventHandlerTest`는 `matchKey` 인자를 포함한 10개 인자 생성자를 쓰고, 계획은 이 생성자가 "Entities.kt 정의 그대로"라고 적고 있다. 그러나 실제 `data/db/Entities.kt`에는 `matchKey`가 없다(설계 §5.3에는 있다).
- 수정: T5 Step 1에서 `val matchKey: String?`를 `poiKakaoId` 다음 위치에 추가한다.
- 부수 효과: DB `version = 1`, `exportSchema = false`, 마이그레이션이 없다. 기기에 이미 설치된 빌드가 있으면 Room 무결성 오류로 크래시가 나므로 앱 데이터를 삭제해야 한다(C4 참고).

### A2. 명령이 Windows 기준이다 [반영 — CLAUDE.md, 계획서 명령을 macOS 기준으로 교체]
`.\gradlew.bat`, `Select-String`은 macOS에서 `./gradlew`, `grep`으로 바꾼다(CLAUDE.md에 반영됨).

## B. 테스트는 통과하지만 실제 동작이 틀리는 곳 (우선순위 순)

### B1. [치명] 미러와 OS 등록 상태가 어긋나 재부팅 후 펜스가 재등록되지 않는다 — T5/T6/T7 [반영 — T3·T5·T6·T7]
`ReseedService`는 OS 상태 대신 DB 미러(`geofence_reg`)를 기준으로 차분한다. 공식 문서상 OS 지오펜스는 **재부팅, 앱 데이터 삭제, Play 서비스 데이터 삭제, `GEOFENCE_NOT_AVAILABLE`(위치 끔)** 때 사라지지만, 미러는 그대로 남는다. 그 결과:
- `BOOT` 재배치에서 키·좌표·반경이 그대로인 POI 펜스는 "변화 없음"으로 판정되어 **다시 등록되지 않는다**. 센티널만 GPS 오차로 좌표가 바뀌어 재등록된다.
- 센티널 이탈 뒤에도 계속 계획에 남는 POI는 영영 등록되지 않는다.
- 계획의 E2E 체크리스트 7번("재부팅 → BOOT → APPLIED")은 **펜스가 없는 상태에서도 통과**한다.
- `GeofenceBroadcastReceiver`는 `hasError()`일 때 그냥 return하므로 `GEOFENCE_NOT_AVAILABLE`에서 복구하는 경로가 없다.

수정 제안:
1. `reseed()`에 "OS 상태를 신뢰할 수 없음" 모드를 둔다. `BOOT`와 `GEOFENCE_NOT_AVAILABLE` 복구 시(그리고 선택적으로 `PERIODIC`)에는 `existing = emptyList()`로 보고 계획된 펜스를 전부 add한다. `addGeofences`는 같은 requestId를 교체하므로 멱등이다.
2. 리시버에서 `event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE`이면 전체 재등록 재배치를 예약한다.
3. `ReseedServiceTest`에 "BOOT면 미러에 같은 펜스가 있어도 전부 add" 케이스를 추가한다.

### B2. [치명] Android 12+에서 위치 권한 요청이 무시된다 — T10 [반영 — T10·T11]
T10 온보딩은 `RequestPermission()`으로 `ACCESS_FINE_LOCATION` **하나만** 요청한다. targetSdk 36에서는 FINE을 COARSE 없이 요청하면 시스템이 요청을 무시하고 `ACCESS_FINE_LOCATION must be requested with ACCESS_COARSE_LOCATION` 로그만 남긴다(공식 문서).
- 수정: `RequestMultiplePermissions()`로 FINE과 COARSE를 함께 요청한다. 사용자가 "대략적 위치"만 허용하면 COARSE만 부여되어 지오펜스가 불가능하므로, 보호 상태 대시보드가 이를 "정확한 위치 꺼짐"으로 보여줘야 한다(`PermissionSnapshot.fineLocation`이 FINE 기준이라 판정 자체는 맞다).

### B3. [높음] 백그라운드 위치 권한이 없으면 재시도가 무한히 반복된다 — T6 [반영 — T5·T6]
API 29+ 타깃에서 지오펜싱에는 `ACCESS_BACKGROUND_LOCATION`이 필수다(공식 문서). 그런데 `ReseedWorker`는 FINE 권한만 확인한다. 열화 모드(§4.2 — '사용 중'만 허용)에서는 등록 실패 → `FAILED` → `Result.retry()`가 최대 5시간 간격으로 끝없이 반복되고, FAILED 로그가 계속 쌓인다.
- 또 설계 §6.4는 "권한 회수 시 등록 전부 해제"를 요구하지만 계획의 워커는 `NO_PERMISSION` 로그만 남기고 미러를 비우지 않는다. 이 때문에 나중에 권한을 다시 부여하면 B1이 발생한다.
- 수정: Q+에서는 백그라운드 권한도 확인한다. 권한이 없으면 OS 등록 해제 + 미러 비우기 + `NO_BACKGROUND_PERMISSION` 로그 후 `success`로 끝낸다(재시도하지 않음).

### B4. [높음] 하나의 유니크 큐에 REPLACE를 쓰면 ITEM_CHANGE가 사라진다 — T6/T8/T9 [반영 — T5·T6·T8·T9]
모든 원인이 `reseed_now` 큐 하나를 `REPLACE`로 공유한다. ITEM_CHANGE는 30초 지연으로 들어가는데, 그 사이에 APP_OPEN이나 SENTINEL_EXIT가 들어오면 대기 중이던 ITEM_CHANGE를 **취소**한다. 새로 들어온 원인은 거버너의 디바운스·거리 조건에 걸려 `SKIPPED`가 될 수 있다.
- 재현: 항목 저장 직후 화면 회전(또는 다크 모드·언어 변경). T9는 `MainActivity.onCreate`에서 APP_OPEN을 거는데, `onCreate`는 구성 변경 때도 호출된다. → 새 항목의 펜스가 등록되지 않는다.
- REPLACE는 **실행 중인** 재배치도 취소한다. OS에는 적용됐는데 미러는 갱신되지 않은 상태로 끊기면 B1과 같은 불일치가 생긴다.
- 수정: 유니크 작업 이름을 원인별로 분리한다(ITEM_CHANGE만 REPLACE로 코얼레싱하고 나머지는 KEEP). APP_OPEN은 `savedInstanceState == null`일 때나 `ProcessLifecycleOwner`의 ON_START에서만 건다.

### B5. [중간] 재배치가 동시에 실행될 수 있다 — T5 [반영 — T5]
PERIODIC(`reseed_health_check`)과 one-shot(`reseed_now`)은 이름이 다른 유니크 작업이라 동시에 돌 수 있다. 두 작업이 같은 미러를 기준으로 차분하면 중복 add/remove가 일어나고 미러가 틀어진다.
- 수정: `@Singleton ReseedService` 안에서 `Mutex().withLock { … }`으로 직렬화한다.

### B6. [중간] 차분이 좌표·반경만 비교해 DWELL 튜닝이 반영되지 않는다 — T4 [반영 — T4·T5]
`DiffCalculator.matches()`는 `center`와 `radiusM`만 비교한다. `EngineParams.LOITERING_DELAY_MS`(§10.2의 1차 튜닝 파라미터)나 전이 종류를 바꿔도 기존 펜스는 교체되지 않으므로, 필드 테스트 회차별 결과가 섞인다.
- 수정: `geofence_reg`에 `transition`과 `loiteringDelayMs`를 저장하고 비교 대상에 포함한다.

### B7. [중간] 발화(PASS)가 진단 로그에 남지 않는다 — T7/T13 [반영 — T7]
T7은 차단된 경우만 `EngineRunLog`에 기록한다. 진단 화면(T13)은 `EngineRunLog`만 보여주므로 "알림이 실제로 발행됐는지"를 앱 안에서 확인할 수 없다. 필드 테스트 프로토콜(§10.2 — 발화/미발화/오발화 집계)과 §4.4를 충족하지 못한다.
- 수정: PASS(알림 발행)와 stale 이벤트 폐기도 `FENCE_EVENT`로 기록한다.

### B8. [중간] `allowBackup="true"` — 프라이버시 원칙과 충돌하고 B1도 유발한다 [반영 — T5 Step 6]
Auto Backup은 Room DB(항목, PLACE 좌표, 펜스 미러)와 DataStore를 클라우드에 백업한다. 이는 "위치·항목 데이터는 기기 밖으로 나가지 않는다"(§9)와 충돌한다. 새 기기에 복원하면 미러만 복원되어 B1이 발생한다.
- 수정: `allowBackup="false"`(v1 권장). 백업을 유지하려면 `dataExtractionRules`/`fullBackupContent`로 DB와 DataStore를 제외해야 한다.

## C. 낮은 우선순위 / 정리 항목

- **[반영 — T1·T7] C1. PLACE 트리거에 "같은 항목·같은 지점 24h" 쿨다운이 적용되지 않는다** (§4.5와 불일치): `PlaceRequest`가 `placeKakaoId`를 버리고, 플래너도 PLACE 펜스에 `poiId`를 넣지 않는다. 그래서 `poiKakaoId = null`이 되어 항목 쿨다운(4h)만 걸린다. 집 앞 지점처럼 매일 드나드는 PLACE는 4시간마다 다시 울린다.
- **[반영 — T7] C2. 알림 권한이 꺼져 있어도 `NotificationLog`가 기록된다**: 핸들러가 로그를 먼저 쓰고, `NearbyNotifier.show()`는 알림 비활성 시 조용히 return한다. 발행되지 않은 알림이 하루 상한을 소진한다.
- **[반영 — T6·T13] C3. `EngineRunLog` 정리가 진단 화면을 열 때만 실행된다**(T13 VM init): 차단 로그가 이벤트마다 쌓이므로 PERIODIC 워커에서도 정리해야 한다.
- **[보류] C4. 스키마 관리**: `exportSchema = false`에 마이그레이션이 없다. 출시 전에 `exportSchema = true` + 스키마 디렉터리 + 마이그레이션 정책을 정해야 한다.
- **[보류] C5. `ReminderDao.upsert`가 `REPLACE`다**: 기존 행을 삭제 후 삽입하므로 FK CASCADE로 `trigger_spec`이 지워진다. 직후에 재삽입하므로 결과는 같지만, 트랜잭션이 아니라서 중간에 실패하면 트리거가 유실된다. `@Upsert` 또는 `@Update`와 DAO `@Transaction`으로 묶는 편이 안전하다.
- **[보류] C6. 플래너 예산 낭비**: 서로 다른 트리거가 같은 POI를 고르면 예산이 두 번 차감되지만 펜스는 1개로 병합된다. 실제 등록 수가 예산(95)보다 적어질 수 있다.
- **[보류] C7. 카카오 REST 키가 `BuildConfig`에 평문으로 들어간다**: APK에서 추출해 쿼터를 도용할 수 있다. 서버 없는 구조에서 수용한 리스크지만 출시 체크리스트에 명시할 것.
- **[수용] C8. RETRY 백오프**: WorkManager 지수 백오프(15m→30m→1h→…→최대 5h)를 쓰므로 스펙(15m→1h→6h)과 다르다. 계획이 의도적으로 수용한 편차다.

## D. 반영 위치 요약

| 항목 | 반영 태스크 |
|---|---|
| A1, B1, B5 | T5 (엔티티·서비스·테스트), T7 (리시버의 NOT_AVAILABLE 처리) |
| B6 | T4 (+ T5 엔티티) |
| B3, B4 | T6 (워커·큐 정책), T8 (Requester), T9 (APP_OPEN 훅) |
| B7, C2 | T7 |
| B2 | T10 |
| B8 | T1 전 또는 T13 (매니페스트 1줄) |
| C1 | T1 (PlaceRequest에 kakaoId) + 플래너 |
| C3 | T13 또는 T6 |
