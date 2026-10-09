# v1 보강 이식 Implementation Plan

| | |
|---|---|
| 상태 | 초안 — 사용자 검토 전 |
| 최종 수정 | 2026-10-09 |

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 원격 기준 `feat/v1`(PR #1, 2026-10-09 `main` 병합)에, 2026-09-29 계획 검토와 로컬 참고 구현(`archive/v1-local`)에서 나온 수정 중 원격에 없는 것을 TDD로 옮긴다.

**Architecture:** 원격 구조는 그대로 둔다. 큐 2개(`reseed_now` REPLACE / `reseed_opportunistic` KEEP), 표시 후 기록(`recordShown`), `ProtectionReseedTrigger`, 클린 미니멀 UI가 여기에 해당한다. 엔진 신뢰성은 세 가지로 보강한다. DataStore의 **펜스 소실 표시(`fences_lost`)**, OS 호출부터 미러 기록까지를 묶는 **NonCancellable 선기록**, 이 앱의 OS 펜스를 PendingIntent 단위로 전부 바꾸는 **`FenceApplier.replaceAll`**이다. 알림 쪽은 이벤트 안에서 같은 항목을 한 번만 내보내고, 알림 id를 펜스 기준으로 바꾸고, PLACE 펜스에 카카오 지점 id를 싣는다. UI는 디자인 개편본 위에 손으로 다시 구현한다. 원격 UI가 로컬과 달라 cherry-pick이 되지 않기 때문이다.

**Tech Stack:** Kotlin 2.1.21, AGP 8.10, Jetpack Compose(BOM 2025.05.01), Hilt, Room 2.7.1, WorkManager 2.10.1, DataStore Preferences, Play Services Location, JUnit4 + kotlinx-coroutines-test + turbine.

**Spec:**
- 설계 문서(권위): `docs/superpowers/specs/2026-08-31-record-of-p-design.md`
- 계획 검토: `docs/superpowers/reviews/2026-09-29-v1-plan-review.md` (코드 주석의 `검토 B1` 등)
- 대조표(무엇을 옮길지의 근거): `.superpowers/sdd/2026-08-31-record-of-p-v1/2026-10-03-local-vs-remote.md` (gitignore 대상, 로컬에만 있음). 코드 주석의 `최종 리뷰 C1`·`I2` 등은 로컬 최종 리뷰 번호다.
- 참고 구현: 브랜치 `archive/v1-local` — `git show archive/v1-local:<경로>`로 읽는다. **cherry-pick 하지 않는다.** 원격 코드와 구조가 달라서, 이 계획의 코드가 원격에 맞게 고쳐 둔 버전이다.

## Global Constraints

- 명령은 항상 `android/`에서 실행한다: `./gradlew testDebugUnitTest`, 전체 검증은 `./gradlew testDebugUnitTest lintDebug assembleDebug`.
- 기준선(이 계획 시작 시점, 커밋 `3d7fced`): 단위 테스트 80개 통과, `lintDebug` 오류 0·경고 22, `assembleDebug` 성공. 태스크가 끝날 때마다 테스트 전부 통과, lint 오류 0을 유지한다. 새 경고가 생기면 보고한다.
- `domain/` 아래에는 Android/GMS import와 data·platform·ui·di 패키지 import를 금지한다(설계 §5.2). PostToolUse 훅(`.claude/hooks/check-domain-purity.sh`)이 막는다. 시간은 `java.time.Clock`으로 주입한다.
- 의존 방향: `ui → data → domain`, `platform → domain + data`. ui는 platform을 import하지 않는다(MainActivity 같은 조립 지점만 예외).
- 튜닝 상수는 `EngineParams`에서만 가져온다. 리터럴을 다른 곳에 다시 쓰지 않는다(설계 §10.2).
- 사용자에게 보이는 문자열은 `res/values/strings.xml`(ko)과 `res/values-en/strings.xml`(en)에 **둘 다** 넣는다. 아포스트로피는 `\'`로 이스케이프한다(설계 §8). 영어는 자연스러운 문장으로 쓴다.
- 새 라이브러리를 추가하지 않는다. 이 계획은 의존성 변경이 없다.
- **Room 스키마(엔티티 칼럼)를 바꾸지 않는다.** 실기기에 설치본이 있고 마이그레이션이 없다(검토 C4 보류). 그래서 검토 B6(미러에 전이 저장)은 옮기지 않는다.
- 위치 데이터를 기기 밖으로 보내는 코드를 금지한다. 외부 통신은 `KakaoLocalApi` 하나뿐이다(설계 §9).
- 배터리 최적화 예외는 자동으로 요청하지 않는다(설계 §4.2). 설정 목록 화면으로 안내만 한다.
- 테스트 더블은 모킹 라이브러리 없이 인터페이스를 직접 구현한 페이크로 만든다. 테스트 이름은 백틱 한글 문장으로 쓴다.
- 취소 예외 관례: `catch (e: Exception)` 앞에 `catch (c: CancellationException) { throw c }`를 둔다.
- `ReminderRepository` 인터페이스를 바꾸면 테스트 페이크 4곳을 모두 고친다: `ReseedServiceTest.FakeReminders`, `HomeViewModelTest.FakeRepo`, `EditorViewModelTest.FakeRepo`, `NearbyViewModelTest.FakeRepo`.
- 커밋은 태스크마다 한 번 한다. 형식은 `fix:`/`feat:`/`refactor:`/`test:` 접두어 + 한글 요약이고, 본문에 설계 §번호와 검토·최종 리뷰 번호를 적는다. 본문 끝에는 실행 환경이 지정한 `Co-Authored-By` 줄을 붙인다. 기존 커밋을 고치거나 다시 쓰지 않는다.
- 브랜치는 묶음마다 하나다: A `feat/hardening-engine`, B `feat/hardening-privacy`, C `feat/hardening-alerts`, D `feat/hardening-ui`. 앞 묶음이 병합된 `main`에서 딴다. 묶음이 끝나면 그 묶음 전체를 리뷰한 뒤 PR 하나로 올린다. 푸시·PR 생성·병합은 사용자가 정한다(CLAUDE.md Git 규칙).

## Review Focus

설계가 암시하지만 원래 테스트가 다루지 않았던 조건 중, 사용자에게 가장 먼저 문제가 될 다섯 가지다. 각 줄의 테스트를 담당 태스크에 넣었다.

1. **부팅 직후 네트워크가 없을 때** — 카카오 조회가 실패해도 센티널·PLACE·기존 POI 펜스가 OS에 다시 있어야 한다. → Task 2 `BOOT에서 POI 조회가 실패해도 미러대로 OS를 되살린다`
2. **대기 중이던 BOOT 작업이 ITEM_CHANGE로 대체될 때**(`reseed_now`가 REPLACE) — 그래도 다음 재배치는 전체 재등록이어야 한다. → Task 1 `markFencesLost 뒤에는 차분 원인도 전체 재등록한다`
3. **실행 중인 재배치가 다른 원인의 REPLACE로 취소될 때** — OS 반영과 미러 기록이 함께 끝나야 한다. → Task 1 `OS 적용 중에 작업이 취소돼도 미러 기록까지 마친다`
4. **같은 가게를 PLACE로도, 카테고리로도 걸어 둔 항목** — 그 가게에 들어가면 알림이 한 번만 떠야 한다(실기기 09-03 14:57:09 사례). → Task 7 `한 이벤트에서 같은 항목이 두 펜스로 통과해도 한 묶음에만 들어간다`
5. **'항상 허용'을 끄고, 같은 자리에서 6시간 안에 다시 켰을 때** — 돌아오자마자 펜스가 다시 등록돼야 한다. → Task 2 `standDown은 재배치 스탬프를 지워 권한이 돌아오면 APP_OPEN이 바로 재배치한다`

## 대조표 항목 ↔ 태스크

| 묶음 | 태스크 | 대조표 ID |
|---|---|---|
| A 엔진 신뢰성 | 1 | B1(소실 표시), T5(선기록·NonCancellable) |
| | 2 | I2(미러 복구), B3+(스탬프 삭제·고아 정리) |
| | 3 | B1(리시버 FENCE_LOST), C1-b(워커 분기 추출), C3+(정리 가드), M2 |
| | 4 | C1-d(센티널 INITIAL_TRIGGER_EXIT), 최종C1(시계 역행·PERIODIC 면제) |
| B 프라이버시·권한 | 5 | B8, B2 |
| | 6 | I4a, I4b, M8 |
| C 알림 정확도 | 7 | 신규(이벤트 내 중복), T7-1(펜스 기준 알림 id) |
| | 8 | 검토 C1(PLACE kakaoId) |
| | 9 | B7(PASS·stale 로그), 검토 C2 마무리 |
| | 10 | I1, N2 |
| | 11 | T7-2(다건 알림) |
| D UI | 12 | I6 |
| | 13 | I3 |
| | 14 | I5, I7, N1 대책 |
| | 15 | C2-b, C2-c, C2-f, M1 |

## File Structure

```
android/app/src/main/
├─ AndroidManifest.xml                         [T5 백업 차단·추출 규칙, T15 adjustResize]
├─ res/xml/data_extraction_rules.xml           [T5 생성] DB·DataStore를 백업·기기 이전에서 제외
├─ res/values{,-en}/strings.xml                [T5·T6·T12·T13·T14·T15 문자열 추가]
└─ java/com/recordofp/app/
   ├─ domain/
   │  ├─ engine/ReseedGovernor.kt              [T3 FENCE_LOST, T4 시계 역행·PERIODIC 면제]
   │  ├─ engine/FencePlan.kt                   [T2 종류별 전이 규칙, T8 placeKakaoId, T10 SENTINEL_FENCE_KEY]
   │  ├─ engine/ReseedPlanner.kt               [T2 전이 규칙 사용, T8 PLACE poiId, T10 센티널 키 상수]
   │  ├─ engine/TriggerResolver.kt             [T8 PlaceRequest.kakaoId]
   │  └─ model/NotificationChannels.kt         [T6 생성] 채널 id (ui·platform 공용, 순수)
   ├─ data/
   │  ├─ engine/ReseedService.kt               [T1 소실 표시·선기록·replaceAll, T2 미러 복구·standDown, T8 kakaoId]
   │  ├─ engine/EngineStateStore.kt            [T1 fences_lost, T2 스탬프 삭제]
   │  ├─ engine/GeofenceEventHandler.kt        [T7 fenceId·중복 제거, T9 PASS·stale·표시 실패 기록]
   │  ├─ notify/NearbyAlerts.kt                [T6 생성] 근처 알림이 보일 수 있는가 (ui·platform 공용)
   │  ├─ repo/ReminderRepository.kt            [T14 reactivate]
   │  └─ repo/SettingsStore.kt                 [T15 기본값을 EngineParams에서]
   ├─ platform/
   │  ├─ ReceiverErrorLog.kt                   [T10 생성] 리시버 예외 → EngineRunLog
   │  ├─ geofence/GeofenceController.kt        [T1 replaceAll, T4 센티널 별도 요청]
   │  ├─ geofence/GeofenceBroadcastReceiver.kt [T3 NOT_AVAILABLE, T9 기록 호출, T10 FenceEventFlow로 위임]
   │  ├─ geofence/FenceEventFlow.kt            [T10 생성] 이벤트 처리 순서·예외 격리 (JVM 테스트)
   │  ├─ notify/Notifier.kt                    [T6 채널 상수 이전]
   │  ├─ notify/NearbyNotifier.kt              [T6 채널 판정, T7 펜스 기준 id, T11 InboxStyle·액션]
   │  ├─ notify/AlertActions.kt                [T11 생성] 묶음별 액션 결정 (JVM 테스트)
   │  ├─ notify/NotificationActionReceiver.kt  [T10 오류 기록, T11 LongArray]
   │  ├─ work/ReseedWorkFlow.kt                [T3 생성] 워커 분기 판단 (JVM 테스트)
   │  └─ work/ReseedWorker.kt                  [T3 분기 위임·UPDATE]
   └─ ui/
      ├─ AppNavHost.kt                         [T15 popFrom]
      ├─ common/BackButton.kt                  [T15 생성]
      ├─ permissions/PermissionStatus.kt       [T6 채널·기기 위치·설정 열기, T13 topIssue]
      ├─ home/HomeScreen.kt                    [T13 보호 배너, T14 실행 취소·TalkBack]
      ├─ home/HomeViewModel.kt                 [T14 reactivate]
      ├─ editor/EditorViewModel.kt             [T12 저장 가드]
      ├─ editor/EditorScreen.kt                [T12 브랜드 입력·오류 문구, T15 imePadding]
      ├─ onboarding/OnboardingScreen.kt        [T5 FINE+COARSE]
      └─ settings/{SettingsScreen,DiagnosticsScreen}.kt, nearby/NearbyScreen.kt [T6·T9·T10·T15]
```

---

## 묶음 A — 엔진 신뢰성

### Task 1: 펜스 소실 표시와 선기록 — 전체 재등록을 원인이 아니라 상태로

원격은 BOOT·PERIODIC일 때만 전량 재등록한다. 그런데 BOOT 원인은 WorkManager 입력값에만 있어서, 대기 중인 BOOT가 `reseed_now`의 REPLACE로 ITEM_CHANGE에 대체되면 그 의도가 사라진다. 또 OS 반영과 미러 기록 사이에서 작업이 취소되면 둘이 어긋난다. 이 태스크는 DataStore에 "OS를 믿을 수 없음" 표시를 남기고, OS 호출부터 미러 기록까지를 NonCancellable로 묶는다. 표시는 OS 호출 **전에** 남기고(선기록) 미러 기록이 성공한 뒤에만 지운다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/data/engine/ReseedService.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/data/engine/EngineStateStore.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceController.kt`
- Test: `android/app/src/test/java/com/recordofp/app/data/engine/ReseedServiceTest.kt`

**Interfaces:**
- Consumes: 원격 `ReseedService`, `FenceApplier.apply(diff)`, `GeofenceRegDao.applyReseed(...)`
- Produces:
  - `interface FenceApplier { suspend fun apply(diff: FenceDiff); suspend fun replaceAll(fences: List<PlannedFence>) }` — `replaceAll`은 이 앱 PendingIntent의 OS 펜스를 전부 지우고 `fences`를 등록한다. 빈 목록이면 해제만 한다.
  - `interface ReseedStateStore`에 `suspend fun fencesLost(): Boolean`, `suspend fun setFencesLost(lost: Boolean)` 추가
  - `suspend fun ReseedService.markFencesLost()` — Task 3의 워커가 호출한다
  - 재배치 규칙: `osUntrusted = fencesLost || cause == BOOT`, `fullResync = osUntrusted || cause == PERIODIC`. 전체 재등록이면 `applier.replaceAll(planned)`를 호출하고 미러를 통째로 바꾼다. 아니면 기존처럼 차분만 적용한다.

- [ ] **Step 1: 테스트 페이크를 넓힌다**

`ReseedServiceTest.kt`에서 아래 세 가지를 바꾼다. import에 `com.recordofp.app.domain.engine.PlannedFence`, `com.recordofp.app.domain.engine.ReseedStamp`, `org.junit.Assert.assertFalse`, `org.junit.Assert.assertNull`을 더한다.

`FakeRegDao`에 실패 스위치를 단다(`applyReseed` 맨 앞에서 던진다).

```kotlin
private class FakeRegDao : GeofenceRegDao {
    val regs = mutableMapOf<String, GeofenceRegEntity>()
    val links = mutableListOf<RegTriggerEntity>()
    /** true면 미러 기록이 실패한다 (디스크 오류 재현) */
    var failApplyReseed = false
    override suspend fun all() = regs.values.toList()
    override suspend fun byId(geofenceId: String) = regs[geofenceId]
    override suspend fun triggerIdsFor(geofenceId: String) =
        links.filter { it.geofenceId == geofenceId }.map { it.triggerId }
    override suspend fun insertRegs(regs: List<GeofenceRegEntity>) =
        regs.forEach { this.regs[it.geofenceId] = it }
    override suspend fun insertRegTriggers(links: List<RegTriggerEntity>) { this.links += links }
    override suspend fun deleteRegs(ids: List<String>) = ids.forEach { regs.remove(it) }
    override suspend fun deleteRegTriggers(ids: List<String>) {
        links.removeAll { it.geofenceId in ids }
    }
    override suspend fun applyReseed(
        removeIds: List<String>, addRegs: List<GeofenceRegEntity>,
        linkFenceIds: List<String>, links: List<RegTriggerEntity>,
    ) { // Room @Transaction 기본 구현과 동일 순서
        if (failApplyReseed) throw IllegalStateException("disk I/O")
        deleteRegTriggers(removeIds + linkFenceIds); deleteRegs(removeIds)
        insertRegs(addRegs); insertRegTriggers(links)
    }
}
```

`FakeApplier`는 `replaceAll` 호출을 따로 기록하고, 실패와 지연을 흉내 낼 수 있게 한다.

```kotlin
private class FakeApplier : FenceApplier {
    val applied = mutableListOf<FenceDiff>()
    val replaced = mutableListOf<List<PlannedFence>>()
    /** true면 OS 호출이 실패한다 */
    var fail = false
    /** 0보다 크면 OS 호출이 이만큼 suspend — 실행 중 취소 재현용 */
    var delayMs = 0L
    override suspend fun apply(diff: FenceDiff) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        if (fail) throw IllegalStateException("GEOFENCE_NOT_AVAILABLE")
        applied += diff
    }
    override suspend fun replaceAll(fences: List<PlannedFence>) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        if (fail) throw IllegalStateException("GEOFENCE_NOT_AVAILABLE")
        replaced += fences
    }
}
```

파일 맨 아래 `FakeStateStore`를 통째로 바꾼다.

```kotlin
class FakeStateStore : ReseedStateStore {
    var stamp: ReseedStamp? = null
    var lost = false
    override suspend fun lastReseed() = stamp
    override suspend fun recordReseed(stamp: ReseedStamp) { this.stamp = stamp }
    override suspend fun fencesLost() = lost
    override suspend fun setFencesLost(lost: Boolean) { this.lost = lost }
}
```

- [ ] **Step 2: 기존 테스트의 기대를 전체 재등록에 맞춘다**

BOOT는 이제 `replaceAll`을 쓴다.

- `해피 패스 - 펜스 적용, 미러·링크 갱신, 스탬프 기록`: `assertEquals(1, applier.applied.size)` → `assertEquals(1, applier.replaced.size)`
- `BOOT은 미러와 계획이 동일해도 전량 재등록한다`: `applier.applied.clear()` → `applier.replaced.clear()`, 마지막 줄 `assertEquals(4, applier.applied.single().add.size)` → `assertEquals(4, applier.replaced.single().size)`
- `BOOT 재배치 중 APP_OPEN이 끼어들어도 이중 적용되지 않는다`: `assertEquals(1, applier.applied.size)` → `assertEquals(1, applier.replaced.size + applier.applied.size)`

- [ ] **Step 3: 새 실패 테스트를 쓴다**

`ReseedServiceTest` 클래스 안에 추가한다.

```kotlin
    @Test
    fun `펜스 소실 표시가 있으면 디바운스와 무관하게 전체 재등록하고 고아 펜스까지 정리한다`() = runTest {
        val regDao = FakeRegDao().apply {
            regs["poi:old"] = GeofenceRegEntity("poi:old", "POI", 37.6, 127.0, 120f, "CU", "old", "cat:convenience", "b0", 0)
        }
        val applier = FakeApplier()
        val state = FakeStateStore().apply {
            stamp = ReseedStamp(1_000_000_000_000 - 60_000, here) // 1분 전 — 평소라면 디바운스
            lost = true
        }
        val service = build(
            FakeReminders(listOf(convenience)), FakePoi(byQuery = mapOf("CS2" to listOf(poi("1", 37.501)))),
            regDao = regDao, applier = applier, stateStore = state,
        )

        assertEquals(ReseedResult.APPLIED, service.reseed(ReseedCause.SENTINEL_EXIT, here))

        assertEquals(setOf("sentinel", "poi:1"), applier.replaced.single().map { it.key }.toSet())
        assertTrue(applier.applied.isEmpty())
        assertEquals(setOf("sentinel", "poi:1"), regDao.regs.keys) // 미러도 통째로 바뀐다
        assertFalse(state.lost) // OS와 미러가 다시 일치한다
    }

    @Test
    fun `markFencesLost 뒤에는 차분 원인도 전체 재등록한다 - 대기 중이던 BOOT가 큐에서 대체돼도 의도가 남는다`() = runTest {
        // reseed_now 큐는 REPLACE다 — 대기 중인 BOOT가 ITEM_CHANGE로 바뀌어도 재부팅으로 사라진 OS 펜스는 되살아나야 한다
        val applier = FakeApplier(); val state = FakeStateStore()
        val service = build(
            FakeReminders(listOf(convenience)), FakePoi(byQuery = mapOf("CS2" to listOf(poi("1", 37.501)))),
            applier = applier, stateStore = state,
        )

        service.markFencesLost()
        assertEquals(ReseedResult.APPLIED, service.reseed(ReseedCause.ITEM_CHANGE, here))

        assertEquals(1, applier.replaced.size)
        assertTrue(applier.applied.isEmpty())
        assertFalse(state.lost)
    }

    @Test
    fun `OS 적용이 실패하면 소실 표시가 남고 FAILED를 반환한다`() = runTest {
        val regDao = FakeRegDao(); val state = FakeStateStore()
        val applier = FakeApplier().apply { fail = true }
        val service = build(
            FakeReminders(listOf(convenience)), FakePoi(byQuery = mapOf("CS2" to listOf(poi("1", 37.501)))),
            regDao = regDao, applier = applier, stateStore = state,
        )

        assertEquals(ReseedResult.FAILED, service.reseed(ReseedCause.ITEM_CHANGE, here))

        assertTrue(state.lost)            // 다음 재배치가 전체 재등록한다
        assertTrue(regDao.regs.isEmpty()) // 미러는 건드리지 않았다
        assertNull(state.stamp)
    }

    @Test
    fun `미러 기록이 실패해도 소실 표시가 남아 다음 재배치가 전체 재등록한다`() = runTest {
        val regDao = FakeRegDao().apply { failApplyReseed = true }
        val state = FakeStateStore(); val applier = FakeApplier()
        val service = build(
            FakeReminders(listOf(convenience)), FakePoi(byQuery = mapOf("CS2" to listOf(poi("1", 37.501)))),
            regDao = regDao, applier = applier, stateStore = state,
        )

        assertEquals(ReseedResult.FAILED, service.reseed(ReseedCause.ITEM_CHANGE, here))

        assertEquals(1, applier.applied.size) // OS에는 반영됐지만
        assertTrue(state.lost)                // 미러와의 일치를 보장할 수 없다
    }

    @Test
    fun `OS 적용 중에 작업이 취소돼도 미러 기록까지 마친다`() = runTest {
        // reseed_now 큐의 REPLACE는 실행 중인 재배치도 취소한다. OS에는 반영됐는데 미러가 옛 상태로 남으면
        // 다음 차분이 틀어진다 (검토 B4)
        val regDao = FakeRegDao(); val state = FakeStateStore()
        val applier = FakeApplier().apply { delayMs = 100 }
        val service = build(
            FakeReminders(listOf(convenience, place)),
            FakePoi(byQuery = mapOf("CS2" to listOf(poi("1", 37.501), poi("2", 37.503)))),
            regDao = regDao, applier = applier, stateStore = state,
        )

        val job = launch { service.reseed(ReseedCause.BOOT, here) }
        testScheduler.advanceTimeBy(50) // OS 호출이 끝나기 전
        job.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals(1, applier.replaced.size) // OS 호출은 끝까지 갔고
        assertEquals(4, regDao.regs.size)      // 미러도 그에 맞게 기록됐다
        assertFalse(state.lost)
    }
```

- [ ] **Step 4: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.ReseedServiceTest"`
Expected: 컴파일 실패 — `replaceAll`, `fencesLost`, `setFencesLost`, `markFencesLost`가 없다.

- [ ] **Step 5: 포트와 저장소를 구현한다**

`ReseedService.kt` 위쪽의 두 인터페이스를 바꾼다.

```kotlin
/** OS 지오펜스 반영 지점 — GMS 의존을 이 인터페이스 뒤로 격리한다 */
interface FenceApplier {
    /** 차분 적용: removeIds를 지우고 add를 등록한다 */
    suspend fun apply(diff: FenceDiff)

    /** 이 앱의 OS 펜스를 전부(미러에 없는 고아 포함) 지우고 fences를 등록한다. 빈 목록이면 해제만 한다 (검토 B1) */
    suspend fun replaceAll(fences: List<PlannedFence>)
}

/** EngineStateStore가 구현 — 테스트에서 페이크 주입용 */
interface ReseedStateStore {
    suspend fun lastReseed(): ReseedStamp?
    suspend fun recordReseed(stamp: ReseedStamp)

    /** OS 펜스가 사라졌거나 어디까지 반영됐는지 몰라 미러를 믿을 수 없는 상태인가 (검토 B1) */
    suspend fun fencesLost(): Boolean
    suspend fun setFencesLost(lost: Boolean)
}
```

`EngineStateStore.kt`에 구현을 더한다(import `androidx.datastore.preferences.core.booleanPreferencesKey`).

```kotlin
    private val fencesLostKey = booleanPreferencesKey("fences_lost")

    override suspend fun fencesLost(): Boolean = dataStore.data.first()[fencesLostKey] ?: false

    override suspend fun setFencesLost(lost: Boolean) {
        dataStore.edit { it[fencesLostKey] = lost }
    }
```

- [ ] **Step 6: ReseedService에 소실 표시·선기록·전체 재등록을 넣는다**

import에 `kotlinx.coroutines.NonCancellable`, `kotlinx.coroutines.withContext`를 더한다. `reseed(...)` 바로 위에 추가한다.

```kotlin
    /**
     * OS 펜스가 사라졌다는 신호(BOOT·FENCE_LOST)를 기록한다. 워커가 권한·위치 확인보다 먼저 부른다 —
     * 그 작업이 재시도로 밀리거나 큐에서 다른 원인으로 대체돼도, 다음에 성공하는 재배치가 전체 재등록한다 (검토 B1)
     */
    suspend fun markFencesLost() = mutex.withLock { stateStore.setFencesLost(true) }
```

`reseedLocked`를 아래로 바꾼다. 트리거 해석과 POI 조회(`triggers` ~ `planned = planner.plan(...)`) 부분은 원격 코드를 그대로 둔다.

```kotlin
    private suspend fun reseedLocked(cause: ReseedCause, current: GeoPoint): ReseedResult {
        val now = clock.millis()
        val fencesLost = stateStore.fencesLost()
        // 펜스 소실 표시가 있으면 디바운스와 무관하게 바로 전체 재등록한다 (검토 B1)
        if (!fencesLost && !governor.shouldReseed(cause, now, stateStore.lastReseed(), current)) {
            // APP_OPEN 디바운스 스킵은 매 앱 진입마다 일어나는 정상 소음 — 로그 생략 (스팸 방지)
            if (cause == ReseedCause.APP_OPEN) return ReseedResult.SKIPPED_DEBOUNCE
            return log(cause, ReseedResult.SKIPPED_DEBOUNCE, 0, now, null)
        }
        // OS 펜스를 믿을 수 없다: 재부팅·앱 업데이트(BOOT, §6.1) 또는 소실 표시(검토 B1)
        val osUntrusted = fencesLost || cause == ReseedCause.BOOT
        // PERIODIC은 OS 등록을 조회할 수 없으니 주기적으로 전부 다시 등록하는 것이 §6.2 "등록 상태 검증"이다
        val fullResync = osUntrusted || cause == ReseedCause.PERIODIC

        val triggers = reminderRepository.activeTriggers()
        val triggerIdsByMatchKey: Map<String, List<Long>> =
            triggers.groupBy({ it.matchKey }, { it.id })
        val requests = resolver.resolve(triggers)

        val planned: List<PlannedFence>
        if (requests.isEmpty()) {
            planned = emptyList() // 볼 것이 없으면 센티널도 걷는다
        } else {
            val candidates = try {
                requests.map { req ->
                    when (req) {
                        is PlaceRequest -> TriggerCandidates(
                            matchKey = req.matchKey, isPlace = true,
                            placePoint = req.point, placeName = req.name,
                        )
                        is QueryRequest -> TriggerCandidates(
                            matchKey = req.matchKey,
                            candidates = poiRepository.search(
                                req.resolution, req.query, current,
                                maxResults = EngineParams.QUERY_MAX_RESULTS,
                            ),
                        )
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                // §6.4: 기존 등록 유지, 아무것도 바꾸지 않는다. 재시도는 호출부(Worker) 몫.
                return log(cause, ReseedResult.FAILED, 0, now, e.message)
            }
            planned = planner.plan(current, candidates)
        }

        val existing = regDao.all()
        val diff = if (fullResync) {
            // 이 앱의 OS 펜스를 전부 지우고(replaceAll) 계획을 전부 등록한다 — 미러도 통째로 바꾼다
            FenceDiff(removeIds = existing.map { it.geofenceId }, add = planned)
        } else {
            differ.diff(existing.map { ExistingFence(it.geofenceId, GeoPoint(it.lat, it.lng), it.radiusM) }, planned)
        }
        val touchesOs = fullResync || !diff.isEmpty

        // OS에 손대기 시작하면 미러 갱신까지 마친다 — reseed_now 큐의 REPLACE가 실행 중인 작업을 취소해도
        // OS와 미러가 어긋나지 않게 한다 (검토 B4)
        return withContext(NonCancellable) {
            // 선기록(write-ahead): OS 호출 전에 소실 표시를 남긴다. OS 호출·미러 쓰기 중 실패하거나 프로세스가 죽으면
            // 둘의 일치를 보장할 수 없으므로 다음 재배치가 원인과 무관하게 전체 재등록한다 (검토 B1)
            if (touchesOs) stateStore.setFencesLost(true)
            try {
                when {
                    fullResync -> applier.replaceAll(planned)
                    touchesOs -> applier.apply(diff)
                }
                val batchId = UUID.randomUUID().toString()
                val links = planned.flatMap { fence ->
                    fence.matchKeys.flatMap { key ->
                        triggerIdsByMatchKey[key].orEmpty().map { RegTriggerEntity(fence.key, it) }
                    }
                }
                regDao.applyReseed(
                    removeIds = diff.removeIds,
                    addRegs = diff.add.map { it.toEntity(batchId, now) },
                    linkFenceIds = planned.map { it.key },
                    links = links,
                )
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                // 소실 표시는 이미 남아 있다 — 기록만 하고 끝낸다. 재시도는 호출부(Worker) 몫
                return@withContext log(cause, ReseedResult.FAILED, 0, now, e.message)
            }
            stateStore.recordReseed(ReseedStamp(now, current))
            if (touchesOs) stateStore.setFencesLost(false) // OS와 미러가 다시 일치한다
            val result = if (planned.isEmpty()) ReseedResult.CLEARED_NO_TRIGGERS else ReseedResult.APPLIED
            log(cause, result, planned.size, now, if (fullResync) "full-resync" else null)
        }
    }
```

원격의 `val toApply = when (cause) { BOOT, PERIODIC -> ... }` 블록과 그 뒤의 `applier.apply(toApply)` 블록은 위 코드로 대체되므로 지운다. `standDown`은 이 태스크에서 건드리지 않는다.

- [ ] **Step 7: GeofenceController에 replaceAll을 구현한다**

`apply`와 `replaceAll`이 같은 `add`를 쓰게 한다.

```kotlin
    override suspend fun apply(diff: FenceDiff) {
        if (diff.removeIds.isNotEmpty()) client.removeGeofences(diff.removeIds).await()
        add(diff.add)
    }

    override suspend fun replaceAll(fences: List<PlannedFence>) {
        // 이 PendingIntent로 등록된 펜스 전부 — 미러에 없는 고아 펜스까지 지운다 (검토 B1)
        client.removeGeofences(geofencePendingIntent()).await()
        add(fences)
    }

    @SuppressLint("MissingPermission") // 호출부(Worker)가 권한 확인 후 진입 (§4.3)
    private suspend fun add(fences: List<PlannedFence>) {
        if (fences.isEmpty()) return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // 재배치 순간 이미 영역 안이어도 즉발 금지 — 자연 전이만 (§6.5 스팸 방지)
            .addGeofences(fences.map { it.toGeofence() })
            .build()
        client.addGeofences(request, geofencePendingIntent()).await()
    }
```

원래 `apply` 위에 있던 `@SuppressLint("MissingPermission")`는 `add`로 옮겼으니 `apply`에서 지운다.

- [ ] **Step 8: 테스트 통과를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.ReseedServiceTest"`
Expected: PASS(기존 9개 + 새 5개)

- [ ] **Step 9: 전체 검증 후 커밋한다**

Run: `./gradlew testDebugUnitTest lintDebug`
Expected: 테스트 전부 통과, lint 오류 0

```bash
git add android/app/src/main/java/com/recordofp/app/data/engine/ReseedService.kt \
  android/app/src/main/java/com/recordofp/app/data/engine/EngineStateStore.kt \
  android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceController.kt \
  android/app/src/test/java/com/recordofp/app/data/engine/ReseedServiceTest.kt
git commit -m "fix: 펜스 소실 표시와 선기록으로 전체 재등록 보장" -m "BOOT 원인이 큐 REPLACE로 사라져도, OS 적용 중 취소돼도 OS와 미러가 어긋나지 않는다.
FenceApplier.replaceAll로 고아 펜스까지 정리한다. (§6.1, §6.2, §6.4, 검토 B1·B4)"
```

---

### Task 2: 조회 실패 시 미러로 OS 복구, 권한 정리 보강

부팅 직후에는 네트워크가 없는 경우가 많다. 이때 카카오 조회가 실패하면 원격은 FAILED만 남기고 OS에는 펜스가 하나도 없는 상태로 백오프한다(§6.4 "구 데이터가 무등록보다 낫다" 위반). 이 태스크는 OS를 믿을 수 없는 상태(`osUntrusted`)에서 조회가 실패하면 미러대로 OS를 되살린다. 원격 미러에는 전이 칼럼이 없으므로, 전이는 펜스 종류에서 다시 계산한다. 또 권한을 회수해 정리(`standDown`)할 때 재배치 스탬프를 지워서, 권한을 다시 켜면 F1의 APP_OPEN이 디바운스에 걸리지 않게 한다. 정리 자체도 `replaceAll(emptyList())`로 바꿔 고아 펜스까지 지운다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/domain/engine/FencePlan.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/domain/engine/ReseedPlanner.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/data/engine/ReseedService.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/data/engine/EngineStateStore.kt`
- Test: `android/app/src/test/java/com/recordofp/app/data/engine/ReseedServiceTest.kt`

**Interfaces:**
- Consumes: Task 1의 `FenceApplier.replaceAll`, `ReseedStateStore.fencesLost/setFencesLost`, `reseedLocked` 안의 `osUntrusted`
- Produces:
  - `fun FenceKind.transition(): FenceTransition`, `fun FenceKind.loiteringDelayMs(): Int?` (domain, FencePlan.kt)
  - `ReseedStateStore.clearReseedStamp()`
  - `suspend fun ReseedService.standDown(cause: ReseedCause, note: String? = null): ReseedResult` — Task 3의 워커가 없는 권한 이름을 `note`로 넘긴다

- [ ] **Step 1: 페이크와 기존 테스트를 고친다**

`FakeStateStore`에 추가한다.

```kotlin
    override suspend fun clearReseedStamp() { stamp = null }
```

기존 `standDown은 OS와 미러 등록을 전부 걷어낸다` 테스트를 바꾼다(원격은 `apply`로 미러 id만 지웠다).

```kotlin
    @Test
    fun `standDown은 OS와 미러 등록을 전부 걷어낸다`() = runTest {
        val regDao = FakeRegDao().apply {
            regs["sentinel"] = GeofenceRegEntity("sentinel", "SENTINEL", 37.5, 127.0, 1000f, null, null, null, "b0", 0)
            regs["poi:1"] = GeofenceRegEntity("poi:1", "POI", 37.501, 127.0, 120f, "CU", "1", "cat:convenience", "b0", 0)
        }
        val applier = FakeApplier(); val state = FakeStateStore()
        val service = build(FakeReminders(listOf(convenience)), FakePoi(), regDao = regDao, applier = applier, stateStore = state)

        val result = service.standDown(ReseedCause.PERIODIC)

        assertEquals(ReseedResult.STOOD_DOWN, result)
        assertTrue(applier.replaced.single().isEmpty()) // PendingIntent 단위 해제 — 미러에 없는 고아 펜스까지
        assertTrue(applier.applied.isEmpty())
        assertTrue(regDao.regs.isEmpty())
        assertFalse(state.lost)
    }
```

기존 `POI 조회 실패 시 기존 등록을 유지하고 FAILED를 반환한다`의 `assertTrue(applier.applied.isEmpty())` 아래에 `assertTrue(applier.replaced.isEmpty()) // 소실 상태가 아니면 OS를 건드리지 않는다`를 더한다.

- [ ] **Step 2: 새 실패 테스트를 쓴다**

import에 `com.recordofp.app.domain.engine.EngineParams`, `com.recordofp.app.domain.engine.FenceTransition`을 더한다.

```kotlin
    @Test
    fun `소실 상태에서 POI 조회가 실패하면 미러대로 OS를 되살리고 FAILED로 재시도를 남긴다`() = runTest {
        val regDao = FakeRegDao().apply {
            regs["sentinel"] = GeofenceRegEntity("sentinel", "SENTINEL", 37.5, 127.0, 1000f, null, null, "", "b0", 0)
            regs["poi:1"] = GeofenceRegEntity("poi:1", "POI", 37.501, 127.0, 120f, "CU 1", "1", "cat:convenience", "b0", 0)
            regs["place:20"] = GeofenceRegEntity("place:20", "PLACE", 37.51, 127.0, 150f, "회사 우체국", null, "place:20", "b0", 0)
        }
        val applier = FakeApplier(); val runLog = FakeRunLog()
        val state = FakeStateStore().apply { lost = true }
        val service = build(
            FakeReminders(listOf(convenience, place)), FakePoi(throwOn = "CS2"),
            regDao = regDao, runLog = runLog, applier = applier, stateStore = state,
        )

        assertEquals(ReseedResult.FAILED, service.reseed(ReseedCause.ITEM_CHANGE, here))

        val restored = applier.replaced.single().associateBy { it.key }
        assertEquals(setOf("sentinel", "poi:1", "place:20"), restored.keys)
        // 미러에는 전이 칼럼이 없다 — 종류별 규칙(설계 §6.3.5)으로 되살린다
        assertEquals(FenceTransition.EXIT, restored.getValue("sentinel").transition)
        assertEquals(FenceTransition.DWELL, restored.getValue("poi:1").transition)
        assertEquals(EngineParams.LOITERING_DELAY_MS, restored.getValue("poi:1").loiteringDelayMs)
        assertEquals(FenceTransition.ENTER, restored.getValue("place:20").transition)
        assertEquals(setOf("cat:convenience"), restored.getValue("poi:1").matchKeys)
        assertTrue(restored.getValue("sentinel").matchKeys.isEmpty()) // 센티널 matchKey는 ""로 저장돼 있다
        assertFalse(state.lost)           // OS가 다시 미러와 같다
        assertEquals(3, regDao.regs.size) // 미러는 그대로
        assertTrue(runLog.entries.last().note!!.startsWith("restored-from-mirror"))
    }

    @Test
    fun `BOOT에서 POI 조회가 실패해도 미러대로 OS를 되살린다 - 부팅 직후 네트워크가 없는 경우`() = runTest {
        val regDao = FakeRegDao().apply {
            regs["poi:1"] = GeofenceRegEntity("poi:1", "POI", 37.501, 127.0, 120f, "CU 1", "1", "cat:convenience", "b0", 0)
        }
        val applier = FakeApplier(); val state = FakeStateStore()
        val service = build(
            FakeReminders(listOf(convenience)), FakePoi(throwOn = "CS2"),
            regDao = regDao, applier = applier, stateStore = state,
        )

        assertEquals(ReseedResult.FAILED, service.reseed(ReseedCause.BOOT, here))

        assertEquals(listOf("poi:1"), applier.replaced.single().map { it.key })
        assertFalse(state.lost)
    }

    @Test
    fun `미러 복구마저 실패하면 소실 표시를 남긴다`() = runTest {
        val regDao = FakeRegDao().apply {
            regs["poi:1"] = GeofenceRegEntity("poi:1", "POI", 37.501, 127.0, 120f, "CU 1", "1", "cat:convenience", "b0", 0)
        }
        val applier = FakeApplier().apply { fail = true }
        val state = FakeStateStore().apply { lost = true }
        val service = build(
            FakeReminders(listOf(convenience)), FakePoi(throwOn = "CS2"),
            regDao = regDao, applier = applier, stateStore = state,
        )

        assertEquals(ReseedResult.FAILED, service.reseed(ReseedCause.ITEM_CHANGE, here))
        assertTrue(state.lost)
    }

    @Test
    fun `standDown은 재배치 스탬프를 지워 권한이 돌아오면 APP_OPEN이 바로 재배치한다`() = runTest {
        // F1: 권한을 다시 켜고 돌아오면 ProtectionReseedTrigger가 APP_OPEN을 건다. 스탬프가 남아 있으면
        // 같은 자리·6시간 안에서는 디바운스에 걸려 PERIODIC까지 펜스가 없다 (검토 B3)
        val state = FakeStateStore().apply { stamp = ReseedStamp(1_000_000_000_000 - 60_000, here) }
        val service = build(
            FakeReminders(listOf(convenience)), FakePoi(byQuery = mapOf("CS2" to listOf(poi("1", 37.501)))),
            stateStore = state,
        )

        service.standDown(ReseedCause.PERIODIC, note = "ACCESS_BACKGROUND_LOCATION")

        assertEquals(ReseedResult.APPLIED, service.reseed(ReseedCause.APP_OPEN, here))
    }

    @Test
    fun `standDown은 사유를 진단 메모로 남긴다`() = runTest {
        val runLog = FakeRunLog()
        val service = build(FakeReminders(listOf(convenience)), FakePoi(), runLog = runLog)

        service.standDown(ReseedCause.PERIODIC, note = "ACCESS_BACKGROUND_LOCATION")

        val row = runLog.entries.last()
        assertEquals("STOOD_DOWN", row.result)
        assertEquals("ACCESS_BACKGROUND_LOCATION", row.note)
    }
```

- [ ] **Step 3: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.ReseedServiceTest"`
Expected: 컴파일 실패 — `clearReseedStamp`, `standDown(..., note)`가 없다.

- [ ] **Step 4: 종류별 전이 규칙을 domain 한곳에 둔다**

`FencePlan.kt` 끝에 추가한다.

```kotlin
/** 펜스 종류별 전이 (설계 §6.3.5). 미러에는 전이 칼럼이 없어 OS를 되살릴 때도 이 규칙을 쓴다 */
fun FenceKind.transition(): FenceTransition = when (this) {
    FenceKind.SENTINEL -> FenceTransition.EXIT
    FenceKind.POI -> FenceTransition.DWELL
    FenceKind.PLACE -> FenceTransition.ENTER
}

/** DWELL(POI)만 체류 시간을 갖는다 */
fun FenceKind.loiteringDelayMs(): Int? = if (this == FenceKind.POI) EngineParams.LOITERING_DELAY_MS else null
```

`ReseedPlanner.kt`의 세 군데가 이 규칙을 쓰게 한다.
- 센티널: `transition = FenceTransition.EXIT,` → `transition = FenceKind.SENTINEL.transition(),`
- PLACE: `transition = FenceTransition.ENTER,` → `transition = FenceKind.PLACE.transition(),`
- POI: `transition = FenceTransition.DWELL,` / `loiteringDelayMs = EngineParams.LOITERING_DELAY_MS,` → `transition = FenceKind.POI.transition(),` / `loiteringDelayMs = FenceKind.POI.loiteringDelayMs(),`

- [ ] **Step 5: 스탬프 삭제를 구현한다**

`ReseedStateStore`에 `suspend fun clearReseedStamp()`를 더한다. `EngineStateStore`에 구현한다.

```kotlin
    override suspend fun clearReseedStamp() {
        dataStore.edit {
            it.remove(atKey)
            it.remove(latKey)
            it.remove(lngKey)
        }
    }
```

- [ ] **Step 6: 미러 복구를 넣는다**

`reseedLocked`에서 POI 조회의 `catch (e: Exception)` 블록을 바꾼다.

```kotlin
            } catch (e: Exception) {
                // §6.4: 기존 등록 유지, 아무것도 바꾸지 않는다. 재시도는 호출부(Worker) 몫.
                // 단 OS 펜스가 사라진 상태(소실 표시·BOOT)라면 "유지할 기존 등록"이 OS에 없다 — 미러대로 되살린다 (최종 리뷰 I2)
                return if (osUntrusted) restoreFromMirror(cause, now, e) else log(cause, ReseedResult.FAILED, 0, now, e.message)
            }
```

`log(...)` 함수 위에 추가한다(import `com.recordofp.app.domain.engine.FenceKind`, `com.recordofp.app.domain.engine.transition`, `com.recordofp.app.domain.engine.loiteringDelayMs`).

```kotlin
    /**
     * OS를 믿을 수 없는 상태에서 POI 조회가 실패했을 때 OS를 미러대로 되돌린다 (§6.4 "구 데이터가 무등록보다 낫다", 최종 리뷰 I2).
     * 부팅 직후 네트워크가 없으면, 이것이 없을 때 OS에 펜스가 하나도 없다(PLACE·센티널 포함).
     * 미러·링크·스탬프는 그대로 두고, 조회는 다시 해야 하므로 결과는 FAILED다.
     */
    private suspend fun restoreFromMirror(cause: ReseedCause, now: Long, lookupError: Exception): ReseedResult =
        withContext(NonCancellable) {
            try {
                val fences = regDao.all().map { it.toPlannedFence() }
                if (fences.isEmpty()) return@withContext log(cause, ReseedResult.FAILED, 0, now, lookupError.message)
                stateStore.setFencesLost(true) // 선기록 — 복구가 끝나야 지운다 (검토 B1)
                applier.replaceAll(fences)
                stateStore.setFencesLost(false) // OS가 다시 미러와 같다
                log(cause, ReseedResult.FAILED, fences.size, now, "restored-from-mirror: ${lookupError.message}")
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                log(cause, ReseedResult.FAILED, 0, now, "restore-failed: ${e.message}")
            }
        }
```

파일 끝 `toEntity` 위에 추가한다.

```kotlin
    private fun GeofenceRegEntity.toPlannedFence(): PlannedFence {
        val fenceKind = FenceKind.valueOf(kind)
        return PlannedFence(
            key = geofenceId, kind = fenceKind, center = GeoPoint(lat, lng), radiusM = radiusM,
            transition = fenceKind.transition(), loiteringDelayMs = fenceKind.loiteringDelayMs(),
            matchKeys = matchKey?.split(",")?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
            poiName = poiName, poiId = poiKakaoId,
        )
    }
```

- [ ] **Step 7: standDown을 바꾼다**

`standDown`과 `standDownLocked`를 아래로 교체한다.

```kotlin
    /**
     * 위치 권한이 없어 지오펜스를 유지할 수 없을 때 (§6.4 권한 회수, 검토 B3).
     * OS의 이 앱 펜스 전부(미러에 없는 고아 포함)와 미러를 비우고 재배치 스탬프를 지운다 —
     * 권한이 돌아오면 다음 재배치(F1의 APP_OPEN 포함)가 디바운스 없이 바로 돈다.
     * @param note 진단 화면에 남길 사유 (예: 없는 권한 이름)
     */
    suspend fun standDown(cause: ReseedCause, note: String? = null): ReseedResult =
        mutex.withLock { standDownLocked(cause, note) } // F5: 재배치와 교차하면 고아 등록이 남는다

    private suspend fun standDownLocked(cause: ReseedCause, note: String?): ReseedResult = withContext(NonCancellable) {
        val now = clock.millis()
        stateStore.clearReseedStamp()
        val existing = regDao.all()
        if (existing.isEmpty() && !stateStore.fencesLost()) {
            return@withContext log(cause, ReseedResult.STOOD_DOWN, 0, now, note ?: "no registrations")
        }
        stateStore.setFencesLost(true) // 선기록 (검토 B1)
        try {
            applier.replaceAll(emptyList())
            regDao.applyReseed(existing.map { it.geofenceId }, emptyList(), emptyList(), emptyList())
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            return@withContext log(cause, ReseedResult.FAILED, 0, now, e.message)
        }
        stateStore.setFencesLost(false)
        log(cause, ReseedResult.STOOD_DOWN, 0, now, note)
    }
```

- [ ] **Step 8: 테스트 통과를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.ReseedServiceTest" --tests "*.ReseedPlannerTest"`
Expected: PASS

- [ ] **Step 9: 전체 검증 후 커밋한다**

Run: `./gradlew testDebugUnitTest lintDebug`
Expected: 테스트 전부 통과, lint 오류 0

```bash
git add android/app/src/main/java/com/recordofp/app/domain/engine/FencePlan.kt \
  android/app/src/main/java/com/recordofp/app/domain/engine/ReseedPlanner.kt \
  android/app/src/main/java/com/recordofp/app/data/engine/ReseedService.kt \
  android/app/src/main/java/com/recordofp/app/data/engine/EngineStateStore.kt \
  android/app/src/test/java/com/recordofp/app/data/engine/ReseedServiceTest.kt
git commit -m "fix: 조회 실패 시 미러로 OS 복구와 권한 정리 시 스탬프 삭제" -m "부팅 직후 오프라인에서도 펜스가 0개가 되지 않는다. 권한을 다시 켜면 바로 재배치된다.
전이는 펜스 종류 규칙(FenceKind.transition)으로 되살린다. (§6.3.5, §6.4, 검토 B3, 최종 리뷰 I2)"
```

---

### Task 3: 워커 분기 추출과 FENCE_LOST 배선

위치가 꺼지면 OS가 이 앱의 펜스를 전부 지우고 `GEOFENCE_NOT_AVAILABLE`을 보낸다. 원격 리시버는 이 오류를 무시하므로 PERIODIC(최대 6시간)까지 알림이 없다. 이 태스크는 그 오류를 `FENCE_LOST` 원인으로 재배치에 연결한다. 워커는 BOOT·FENCE_LOST 첫 시도에서 권한·위치 확인보다 먼저 펜스 소실을 표시한다. 워커의 분기 판단은 Android 타입이 없는 순수 함수로 빼서 JVM에서 테스트한다. 주기 작업 정책도 `UPDATE`로 바꾼다(KEEP이면 주기를 튜닝해도 반영되지 않는다).

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/domain/engine/ReseedGovernor.kt`
- Create: `android/app/src/main/java/com/recordofp/app/platform/work/ReseedWorkFlow.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/work/ReseedWorker.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceBroadcastReceiver.kt`
- Modify: `CLAUDE.md`
- Test: `android/app/src/test/java/com/recordofp/app/platform/work/ReseedWorkFlowTest.kt` (생성), `android/app/src/test/java/com/recordofp/app/domain/engine/ReseedGovernorTest.kt`

**Interfaces:**
- Consumes: Task 1 `ReseedService.markFencesLost()`, Task 2 `ReseedService.standDown(cause, note)`
- Produces:
  - `enum class ReseedCause { BOOT, FENCE_LOST, SENTINEL_EXIT, PERIODIC, APP_OPEN, ITEM_CHANGE, RETRY }`
  - `internal interface ReseedWorkSteps`, `internal enum class ReseedWorkResult { SUCCESS, RETRY }`, `internal suspend fun runReseedWork(cause: ReseedCause, runAttemptCount: Int, steps: ReseedWorkSteps): ReseedWorkResult`

- [ ] **Step 1: 거버너 테스트를 고친다**

`ReseedGovernorTest`의 `BOOT와 ITEM_CHANGE는 디바운스를 무시한다`를 바꾼다.

```kotlin
    @Test
    fun `BOOT·FENCE_LOST·ITEM_CHANGE는 디바운스를 무시한다`() {
        val fresh = stamp(ageMs = 1 * min) // 1분 전 — 10분 미만
        listOf(ReseedCause.BOOT, ReseedCause.FENCE_LOST, ReseedCause.ITEM_CHANGE).forEach { c ->
            assertTrue("$c", governor.shouldReseed(c, now, fresh, origin))
        }
    }
```

- [ ] **Step 2: 워커 분기 테스트를 쓴다**

`ReseedWorkFlowTest.kt`를 만든다.

```kotlin
package com.recordofp.app.platform.work

import com.recordofp.app.data.engine.ReseedResult
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.domain.model.GeoPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 호출 순서를 기록하는 워커 부수효과 페이크 */
private class FakeSteps(
    var missing: List<String> = emptyList(),
    var location: GeoPoint? = GeoPoint(37.5, 127.0),
    var result: ReseedResult = ReseedResult.APPLIED,
    var pruneFails: Boolean = false,
) : ReseedWorkSteps {
    val calls = mutableListOf<String>()
    val standDowns = mutableListOf<List<String>>()
    val sentinelRetries = mutableListOf<Long>()

    override suspend fun markFencesLost() { calls += "mark" }
    override suspend fun pruneLogs() {
        calls += "prune"
        if (pruneFails) throw IllegalStateException("db locked")
    }
    override fun missingPermissions(): List<String> { calls += "perm"; return missing }
    override suspend fun standDown(cause: ReseedCause, missing: List<String>) {
        calls += "standDown"
        standDowns += missing
    }
    override suspend fun currentLocation(): GeoPoint? { calls += "location"; return location }
    override suspend fun logNoLocation(cause: ReseedCause) { calls += "noLocation" }
    override suspend fun reseed(cause: ReseedCause, here: GeoPoint): ReseedResult { calls += "reseed"; return result }
    override fun scheduleSentinelRetry(delayMs: Long) {
        calls += "sentinelRetry"
        sentinelRetries += delayMs
    }
}

class ReseedWorkFlowTest {

    private suspend fun run(cause: ReseedCause, steps: FakeSteps, attempt: Int = 0) =
        runReseedWork(cause, attempt, steps)

    @Test
    fun `BOOT·FENCE_LOST 첫 시도는 무엇보다 먼저 펜스 소실을 표시한다`() = runTest {
        listOf(ReseedCause.BOOT, ReseedCause.FENCE_LOST).forEach { cause ->
            val steps = FakeSteps()
            assertEquals(ReseedWorkResult.SUCCESS, run(cause, steps))
            assertEquals("$cause", listOf("mark", "perm", "location", "reseed"), steps.calls)
        }
    }

    @Test
    fun `재시도 차례나 다른 원인은 펜스 소실을 표시하지 않는다`() = runTest {
        val retry = FakeSteps()
        run(ReseedCause.BOOT, retry, attempt = 1)
        val sentinel = FakeSteps()
        run(ReseedCause.SENTINEL_EXIT, sentinel)
        assertTrue("mark" !in retry.calls && "mark" !in sentinel.calls)
    }

    @Test
    fun `PERIODIC은 로그를 정리하고, 정리가 실패해도 재배치를 계속한다`() = runTest {
        val steps = FakeSteps(pruneFails = true)
        assertEquals(ReseedWorkResult.SUCCESS, run(ReseedCause.PERIODIC, steps))
        assertEquals(listOf("prune", "perm", "location", "reseed"), steps.calls)
    }

    @Test
    fun `위치 권한이 없으면 사유와 함께 등록을 걷고 재시도하지 않는다`() = runTest {
        val steps = FakeSteps(missing = listOf("android.permission.ACCESS_BACKGROUND_LOCATION"))
        assertEquals(ReseedWorkResult.SUCCESS, run(ReseedCause.ITEM_CHANGE, steps))
        assertEquals(listOf("perm", "standDown"), steps.calls)
        assertEquals(listOf(listOf("android.permission.ACCESS_BACKGROUND_LOCATION")), steps.standDowns)
    }

    @Test
    fun `위치를 못 얻으면 기록하고 재시도한다`() = runTest {
        val steps = FakeSteps(location = null)
        assertEquals(ReseedWorkResult.RETRY, run(ReseedCause.SENTINEL_EXIT, steps))
        assertEquals(listOf("perm", "location", "noLocation"), steps.calls)
    }

    @Test
    fun `재배치가 실패하면 재시도한다`() = runTest {
        assertEquals(ReseedWorkResult.RETRY, run(ReseedCause.ITEM_CHANGE, FakeSteps(result = ReseedResult.FAILED)))
    }

    @Test
    fun `디바운스된 센티널 이탈은 최소 간격 뒤로 다시 예약하고 성공으로 끝낸다`() = runTest {
        // EXIT는 재신호가 없다 — 버리면 사용자는 유일한 센티널 밖에 남는다 (§6.2)
        val steps = FakeSteps(result = ReseedResult.SKIPPED_DEBOUNCE)
        assertEquals(ReseedWorkResult.SUCCESS, run(ReseedCause.SENTINEL_EXIT, steps))
        assertEquals(listOf(EngineParams.RESEED_MIN_INTERVAL_MS), steps.sentinelRetries)
    }

    @Test
    fun `다른 원인의 디바운스는 다시 예약하지 않는다`() = runTest {
        val steps = FakeSteps(result = ReseedResult.SKIPPED_DEBOUNCE)
        run(ReseedCause.APP_OPEN, steps)
        assertTrue(steps.sentinelRetries.isEmpty())
    }

    @Test
    fun `적용·트리거 없음·스탠드다운은 성공으로 끝낸다`() = runTest {
        listOf(ReseedResult.APPLIED, ReseedResult.CLEARED_NO_TRIGGERS, ReseedResult.STOOD_DOWN).forEach { r ->
            assertEquals("$r", ReseedWorkResult.SUCCESS, run(ReseedCause.ITEM_CHANGE, FakeSteps(result = r)))
        }
    }
}
```

- [ ] **Step 3: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.ReseedWorkFlowTest" --tests "*.ReseedGovernorTest"`
Expected: 컴파일 실패 — `FENCE_LOST`, `ReseedWorkSteps`, `runReseedWork`가 없다.

- [ ] **Step 4: FENCE_LOST 원인을 더한다**

`ReseedGovernor.kt`를 바꾼다.

```kotlin
/**
 * 재배치 원인 (스펙 §6.2). EngineRunLog.cause에 name이 그대로 남는다.
 * FENCE_LOST는 스펙 표 밖의 원인이다 — GEOFENCE_NOT_AVAILABLE(위치 꺼짐 등으로 OS가 펜스를 전부 지움) 복구 (검토 B1).
 */
enum class ReseedCause { BOOT, FENCE_LOST, SENTINEL_EXIT, PERIODIC, APP_OPEN, ITEM_CHANGE, RETRY }
```

`shouldReseed`의 `when`에서 `ReseedCause.BOOT, ReseedCause.ITEM_CHANGE -> true`를 아래로 바꾼다.

```kotlin
            // 지오펜스 소멸(BOOT·FENCE_LOST)·항목 변경은 즉시 반영 — 코얼레싱은 WorkManager 큐가 담당
            ReseedCause.BOOT, ReseedCause.FENCE_LOST, ReseedCause.ITEM_CHANGE -> true
```

- [ ] **Step 5: 워커 분기를 순수 함수로 만든다**

`ReseedWorkFlow.kt`를 만든다.

```kotlin
package com.recordofp.app.platform.work

import com.recordofp.app.data.engine.ReseedResult
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.domain.model.GeoPoint
import kotlin.coroutines.cancellation.CancellationException

/** 워커 한 번의 부수효과 포트 — ReseedWorker가 Android/WorkManager로 구현하고 테스트는 페이크를 쓴다 */
internal interface ReseedWorkSteps {
    suspend fun markFencesLost()
    suspend fun pruneLogs()
    fun missingPermissions(): List<String>
    suspend fun standDown(cause: ReseedCause, missing: List<String>)
    suspend fun currentLocation(): GeoPoint?
    suspend fun logNoLocation(cause: ReseedCause)
    suspend fun reseed(cause: ReseedCause, here: GeoPoint): ReseedResult
    fun scheduleSentinelRetry(delayMs: Long)
}

internal enum class ReseedWorkResult { SUCCESS, RETRY }

/**
 * ReseedWorker의 분기 판단 (스펙 §6.2·§6.4). Android 타입 없이 JVM에서 검증한다.
 * runAttemptCount는 WorkManager 재시도 횟수(첫 시도 0).
 */
internal suspend fun runReseedWork(
    cause: ReseedCause,
    runAttemptCount: Int,
    steps: ReseedWorkSteps,
): ReseedWorkResult {
    // 재부팅·위치 꺼짐으로 OS 펜스가 사라졌다. 다른 무엇보다 먼저 기록해야 이 작업이 재시도로 밀리거나
    // 큐에서 다른 원인으로 대체돼도 다음에 성공하는 재배치가 전체 재등록한다 (검토 B1)
    if (runAttemptCount == 0 && (cause == ReseedCause.BOOT || cause == ReseedCause.FENCE_LOST)) {
        steps.markFencesLost()
    }

    if (cause == ReseedCause.PERIODIC) {
        // §4.4: 진단 로그가 무한정 쌓이지 않게 주기 작업이 돌 때마다 정리한다. 정리 실패가 재배치를 막지 않는다
        try {
            steps.pruneLogs()
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
        }
    }

    val missing = steps.missingPermissions()
    if (missing.isNotEmpty()) {
        // 재시도해도 소용없다. 등록을 전부 걷고(§6.4 권한 회수) 권한은 보호 상태 대시보드가 알린다 (§4.3, 검토 B3)
        steps.standDown(cause, missing)
        return ReseedWorkResult.SUCCESS
    }

    val here = steps.currentLocation()
    if (here == null) {
        steps.logNoLocation(cause)
        return ReseedWorkResult.RETRY // §6.4 위치 미취득 → 백오프 재시도
    }

    return when (steps.reseed(cause, here)) {
        ReseedResult.FAILED -> ReseedWorkResult.RETRY
        ReseedResult.SKIPPED_DEBOUNCE -> {
            // EXIT는 재신호가 없다 — 디바운스 창 이후로 스스로 재예약한다 (§6.2)
            if (cause == ReseedCause.SENTINEL_EXIT) steps.scheduleSentinelRetry(EngineParams.RESEED_MIN_INTERVAL_MS)
            ReseedWorkResult.SUCCESS
        }
        ReseedResult.APPLIED, ReseedResult.CLEARED_NO_TRIGGERS, ReseedResult.STOOD_DOWN -> ReseedWorkResult.SUCCESS
    }
}
```

- [ ] **Step 6: 워커를 위임 구조로 바꾼다**

`ReseedWorker.doWork()`를 교체하고 `missingLocationPermissions()`를 더한다(import `com.recordofp.app.domain.model.GeoPoint`).

```kotlin
    override suspend fun doWork(): Result {
        val cause = inputData.getString(KEY_CAUSE)
            ?.let { runCatching { ReseedCause.valueOf(it) }.getOrNull() }
            ?: ReseedCause.PERIODIC

        // 분기 판단은 runReseedWork(JVM 테스트) — 여기는 Android 접착만
        val steps = object : ReseedWorkSteps {
            override suspend fun markFencesLost() = reseedService.markFencesLost()
            override suspend fun pruneLogs() =
                runLogDao.pruneOlderThan(clock.millis() - EngineParams.RUN_LOG_RETENTION_MS)
            override fun missingPermissions() = missingLocationPermissions()
            override suspend fun standDown(cause: ReseedCause, missing: List<String>) {
                reseedService.standDown(cause, note = missing.joinToString { it.substringAfterLast('.') })
            }
            override suspend fun currentLocation() = locationProvider.currentOrLast()
            override suspend fun logNoLocation(cause: ReseedCause) = runLogDao.insert(
                EngineRunLogEntity(at = clock.millis(), cause = cause.name, result = "NO_LOCATION", registeredCount = 0, note = null),
            )
            override suspend fun reseed(cause: ReseedCause, here: GeoPoint) = reseedService.reseed(cause, here)
            override fun scheduleSentinelRetry(delayMs: Long) =
                runNow(applicationContext, ReseedCause.SENTINEL_EXIT, delayMs = delayMs)
        }
        return when (runReseedWork(cause, runAttemptCount, steps)) {
            ReseedWorkResult.SUCCESS -> Result.success()
            ReseedWorkResult.RETRY -> Result.retry()
        }
    }

    /** 지오펜싱에 필요한 위치 권한 중 없는 것. API 29+는 백그라운드 위치가 필수다 (검토 B3) */
    private fun missingLocationPermissions(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }.filter {
        ContextCompat.checkSelfPermission(applicationContext, it) != PackageManager.PERMISSION_GRANTED
    }
```

`schedulePeriodic`의 정책을 바꾼다.

```kotlin
                // UPDATE: 주기를 튜닝하면(EngineParams) 앱 업데이트 뒤 다음 실행부터 반영된다. KEEP은 옛 주기를 영원히 유지한다 (최종 리뷰 M2)
                ExistingPeriodicWorkPolicy.UPDATE,
```

`ReseedResult` import가 더 이상 쓰이지 않으면 지운다. `UNIQUE_ONESHOT` 주석은 `// 강한 원인: ITEM_CHANGE/BOOT/FENCE_LOST/SENTINEL_EXIT/RETRY`로 고친다.

- [ ] **Step 7: 리시버가 GEOFENCE_NOT_AVAILABLE을 재배치로 잇게 한다**

`GeofenceBroadcastReceiver.onReceive`의 `if (event.hasError()) return`을 바꾼다(import `com.google.android.gms.location.GeofenceStatusCodes`).

```kotlin
        if (event.hasError()) {
            // 위치가 꺼지면 OS가 이 앱의 펜스를 전부 지우고 이 오류를 보낸다. 전체 재등록을 예약한다 —
            // 위치가 다시 켜질 때까지 워커는 NO_LOCATION/FAILED로 백오프 재시도한다 (검토 B1)
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                ReseedWorker.runNow(context, ReseedCause.FENCE_LOST)
            }
            return
        }
```

- [ ] **Step 8: CLAUDE.md에 펜스 소실 표시를 적는다**

`CLAUDE.md` "알아두어야 할 개념"의 `geofence_reg는 OS 등록 상태의 미러` 항목 끝에 이어 쓴다.

```markdown
 어긋남은 DataStore의 **펜스 소실 표시(`fences_lost`)**로 다룬다. BOOT·FENCE_LOST(`GEOFENCE_NOT_AVAILABLE`) 워커 첫 시도와 OS 호출 직전에 표시를 켜고, 미러 기록까지 성공하면 끈다. 표시가 켜져 있으면 다음 재배치는 원인과 상관없이 `replaceAll`로 전체 재등록하고, 조회가 실패하면 미러대로 OS를 되살린다.
```

- [ ] **Step 9: 테스트 통과를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.ReseedWorkFlowTest" --tests "*.ReseedGovernorTest" --tests "*.ReseedServiceTest"`
Expected: PASS

- [ ] **Step 10: 전체 검증 후 커밋한다**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: 테스트 전부 통과, lint 오류 0, 빌드 성공

```bash
git add CLAUDE.md android/app/src/main/java/com/recordofp/app/domain/engine/ReseedGovernor.kt \
  android/app/src/main/java/com/recordofp/app/platform/work/ReseedWorkFlow.kt \
  android/app/src/main/java/com/recordofp/app/platform/work/ReseedWorker.kt \
  android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceBroadcastReceiver.kt \
  android/app/src/test/java/com/recordofp/app/platform/work/ReseedWorkFlowTest.kt \
  android/app/src/test/java/com/recordofp/app/domain/engine/ReseedGovernorTest.kt
git commit -m "fix: 위치 꺼짐 복구(FENCE_LOST)와 워커 분기 추출" -m "GEOFENCE_NOT_AVAILABLE을 재배치로 잇고, BOOT·FENCE_LOST 첫 시도에서 펜스 소실을 먼저 표시한다.
워커 분기를 JVM 테스트로 고정하고 주기 작업은 UPDATE로 바꾼다. (§6.2, §6.4, 검토 B1·B3, 최종 리뷰 C1·M2)"
```

---

### Task 4: 이동 감지 사슬 보강 — 센티널 즉시 이탈, 시계 역행, PERIODIC 디바운스 면제

센티널은 `initialTrigger 0`으로 등록되므로, 등록하는 순간 사용자가 이미 원 밖에 있으면(차량 이동, 오래된 lastLocation) EXIT가 영영 오지 않는다. 센티널만 `INITIAL_TRIGGER_EXIT`로 따로 등록한다. 즉시 들어온 EXIT는 디바운스에 걸리고, 워커가 10분 뒤로 다시 예약하므로(Task 3) 루프가 생기지 않는다. 그 EXIT가 실행 중인 재배치를 REPLACE로 취소해도, Task 1의 NonCancellable 덕분에 미러는 어긋나지 않는다. 거버너도 두 가지를 고친다. 시계가 거꾸로 가면 경과 시간이 음수가 되어 재배치가 막히는 문제를 고치고, PERIODIC(최후 방어선)은 디바운스를 받지 않게 한다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/domain/engine/ReseedGovernor.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceController.kt`
- Test: `android/app/src/test/java/com/recordofp/app/domain/engine/ReseedGovernorTest.kt`, `android/app/src/test/java/com/recordofp/app/data/engine/ReseedServiceTest.kt`

**Interfaces:**
- Consumes: Task 1의 `GeofenceController.add(fences)`, Task 3의 `ReseedCause.FENCE_LOST`
- Produces: 거버너 규칙 — PERIODIC은 항상 실행, 음수 age는 `Long.MAX_VALUE`로 본다.

- [ ] **Step 1: 거버너 테스트를 고친다**

`SENTINEL_EXIT·PERIODIC·RETRY는 10분 디바운스를 따른다`를 아래 세 테스트로 바꾼다.

```kotlin
    @Test
    fun `SENTINEL_EXIT·RETRY는 10분 디바운스를 따른다`() {
        listOf(ReseedCause.SENTINEL_EXIT, ReseedCause.RETRY).forEach { c ->
            assertFalse("$c 9분", governor.shouldReseed(c, now, stamp(9 * min), origin))
            assertTrue("$c 10분", governor.shouldReseed(c, now, stamp(10 * min), origin))
        }
    }

    @Test
    fun `PERIODIC은 디바운스를 받지 않는다 - 다른 신호가 다 죽었을 때의 최후 방어선`() {
        assertTrue(governor.shouldReseed(ReseedCause.PERIODIC, now, stamp(1 * min), origin))
    }

    @Test
    fun `시계가 거꾸로 가 스탬프가 미래에 있으면 간격이 지난 것으로 본다`() {
        val future = stamp(ageMs = -5 * min) // 스탬프가 5분 뒤에 있다
        assertTrue(governor.shouldReseed(ReseedCause.SENTINEL_EXIT, now, future, origin))
        assertTrue(governor.shouldReseed(ReseedCause.APP_OPEN, now, future, origin))
    }
```

`ReseedServiceTest`의 `디바운스에 걸리면 아무것도 하지 않는다`에서 원인을 `ReseedCause.PERIODIC` → `ReseedCause.SENTINEL_EXIT`로 바꾸고, `assertTrue(applier.replaced.isEmpty())`를 더한다. 아래 테스트도 추가한다.

```kotlin
    @Test
    fun `PERIODIC은 방금 재배치했어도 전체 재등록한다`() = runTest {
        val state = FakeStateStore().apply { stamp = ReseedStamp(1_000_000_000_000 - 60_000, here) }
        val applier = FakeApplier()
        val service = build(
            FakeReminders(listOf(convenience)), FakePoi(byQuery = mapOf("CS2" to listOf(poi("1", 37.501)))),
            applier = applier, stateStore = state,
        )
        assertEquals(ReseedResult.APPLIED, service.reseed(ReseedCause.PERIODIC, here))
        assertEquals(1, applier.replaced.size)
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.ReseedGovernorTest" --tests "*.ReseedServiceTest"`
Expected: FAIL — PERIODIC 면제, 시계 역행, `PERIODIC은 방금 재배치했어도` 3건

- [ ] **Step 3: 거버너를 고친다**

`shouldReseed` 본문을 바꾼다.

```kotlin
    fun shouldReseed(cause: ReseedCause, nowMs: Long, last: ReseedStamp?, current: GeoPoint): Boolean {
        if (last == null) return true
        // 시계가 거꾸로 가 스탬프가 미래에 있으면 경과 시간을 알 수 없다 — 간격이 지난 것으로 보고 스탬프를 새로 쓴다
        val age = (nowMs - last.atMs).let { if (it < 0) Long.MAX_VALUE else it }
        return when (cause) {
            // 지오펜스 소멸(BOOT·FENCE_LOST)·항목 변경은 즉시 반영 — 코얼레싱은 WorkManager 큐가 담당.
            // PERIODIC은 다른 신호가 다 죽었을 때의 최후 방어선이라 디바운스를 받지 않는다 (§6.2, 6시간에 카카오 1회분)
            ReseedCause.BOOT, ReseedCause.FENCE_LOST, ReseedCause.ITEM_CHANGE, ReseedCause.PERIODIC -> true
            ReseedCause.APP_OPEN -> age >= EngineParams.RESEED_MIN_INTERVAL_MS && (
                distanceMeters(last.point, current) >= EngineParams.APP_OPEN_RESEED_DISTANCE_M ||
                    age >= EngineParams.APP_OPEN_RESEED_AGE_MS
                )
            ReseedCause.SENTINEL_EXIT, ReseedCause.RETRY ->
                age >= EngineParams.RESEED_MIN_INTERVAL_MS
        }
    }
```

- [ ] **Step 4: 센티널을 별도 요청으로 등록한다**

`GeofenceController`의 `add`를 바꾸고 `request`를 더한다.

```kotlin
    /**
     * 초기 트리거가 달라 센티널은 요청을 따로 만든다 (최종 리뷰 C1).
     * - POI·PLACE: 재배치 순간 이미 영역 안이어도 즉발 금지 — 자연 전이만 (§6.5 스팸 방지)
     * - SENTINEL: 등록 순간 이미 밖이면(빠른 차량, 오래된 lastLocation) 곧바로 EXIT를 낸다 — 센티널 자가 복구.
     *   그 이탈은 방금 재배치했으므로 거버너가 디바운스하고, 워커가 10분 뒤로 다시 예약한다 (루프 없음)
     */
    @SuppressLint("MissingPermission") // 호출부(Worker)가 권한 확인 후 진입 (§4.3)
    private suspend fun add(fences: List<PlannedFence>) {
        val (sentinels, others) = fences.partition { it.kind == FenceKind.SENTINEL }
        if (others.isNotEmpty()) {
            client.addGeofences(request(others, initialTrigger = 0), geofencePendingIntent()).await()
        }
        if (sentinels.isNotEmpty()) {
            client.addGeofences(
                request(sentinels, initialTrigger = GeofencingRequest.INITIAL_TRIGGER_EXIT), geofencePendingIntent(),
            ).await()
        }
    }

    private fun request(fences: List<PlannedFence>, initialTrigger: Int): GeofencingRequest =
        GeofencingRequest.Builder()
            .setInitialTrigger(initialTrigger)
            .addGeofences(fences.map { it.toGeofence() })
            .build()
```

- [ ] **Step 5: 테스트 통과를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.ReseedGovernorTest" --tests "*.ReseedServiceTest"`
Expected: PASS

- [ ] **Step 6: 전체 검증 후 커밋한다**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`

```bash
git add android/app/src/main/java/com/recordofp/app/domain/engine/ReseedGovernor.kt \
  android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceController.kt \
  android/app/src/test/java/com/recordofp/app/domain/engine/ReseedGovernorTest.kt \
  android/app/src/test/java/com/recordofp/app/data/engine/ReseedServiceTest.kt
git commit -m "fix: 센티널 즉시 이탈 감지와 거버너 시계 역행·PERIODIC 면제" -m "등록 순간 이미 원 밖이면 센티널이 곧바로 EXIT를 낸다. 시계가 거꾸로 가도 재배치가 막히지 않고,
PERIODIC 헬스체크는 디바운스 없이 돈다. (§6.2, 최종 리뷰 C1)"
```

---

## 묶음 B — 프라이버시·권한

### Task 5: 백업 차단과 위치 권한 요청 보정

`allowBackup="true"`이면 위치·항목 DB와 DataStore가 클라우드 백업과 기기 이전으로 나간다(§9 위반). 새 기기로 미러만 복원되면 OS에 없는 펜스를 "등록됨"으로 믿는 문제(검토 B1)도 생긴다. 그래서 백업을 끄고, Android 12+의 기기 간 이전에서도 DB와 DataStore를 뺀다. 또 Android 12+는 FINE을 COARSE 없이 요청하면 요청 자체를 무시한다는 공식 문서가 있다. 원격 실기기 노트(Android 16)는 FINE 단독 요청이 허용됐다고 적어 문서와 엇갈리지만, 둘을 함께 요청하면 어느 쪽이든 안전하다. 이 태스크는 매니페스트와 권한 요청 방식을 바꾸므로 JVM 단위 테스트 대상이 아니다. lint·빌드와 수동 확인 목록으로 검증한다.

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/xml/data_extraction_rules.xml`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/onboarding/OnboardingScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`

**Interfaces:**
- Consumes: 없음
- Produces: 문자열 `settings_perm_location`의 뜻이 "정확한 위치"로 바뀐다(Task 6 대시보드가 쓴다).

- [ ] **Step 1: 추출 규칙 파일을 만든다**

`res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  위치·항목 데이터와 지오펜스 미러는 클라우드 백업·기기 이전 대상에서 뺀다 (설계 §9, 검토 B8).
  미러만 새 기기로 옮겨지면 OS에는 없는 펜스를 "등록됨"으로 믿게 된다 (검토 B1).
  DataStore(온보딩 완료 표시 포함)도 빼서 새 기기에서는 권한 온보딩부터 다시 시작한다.
-->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="." />
        <exclude domain="file" path="datastore/" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="." />
        <exclude domain="file" path="datastore/" />
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 2: 매니페스트에서 백업을 끈다**

`<application` 태그 속성을 바꾼다.

```xml
    <!-- 백업 끔(Android 11 이하) + 추출 규칙(Android 12+ 기기 이전)으로 위치·항목이 기기 밖으로 나가지 않게 한다 (§9, 검토 B8) -->
    <application
        android:name=".RecordOfPApp"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.RecordOfP"
        tools:targetApi="31">
```

- [ ] **Step 3: 온보딩에서 FINE과 COARSE를 함께 요청한다**

`OnboardingScreen.kt`의 `locationPermissionLauncher`를 바꾼다.

```kotlin
    // Android 12+는 FINE을 COARSE 없이 요청하면 요청 자체를 무시한다 — 둘을 함께 요청한다 (검토 B2).
    // 사용자가 "대략적 위치"만 고르면 FINE이 없으므로 보호 상태가 '정확한 위치 꺼짐'으로 안내한다
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> viewModel.finish() }
```

위치 단계의 허용 버튼을 바꾼다.

```kotlin
            PrimaryButton(
                text = stringResource(R.string.onboard_allow),
                onClick = {
                    locationPermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                },
            )
```

- [ ] **Step 4: '정확한 위치'를 안내하는 문자열로 바꾼다**

ko(`values/strings.xml`):

```xml
    <string name="onboard_3_body">주변의 상점을 찾으려면 위치 권한이 필요해요. 근처 알림이 정확하려면 \'정확한 위치\'를 켜 주세요.</string>
    <string name="settings_perm_location">정확한 위치</string>
```

en(`values-en/strings.xml`):

```xml
    <string name="onboard_3_body">We need location permission to find nearby stores. For accurate nearby alerts, please turn on \'Precise location\'.</string>
    <string name="settings_perm_location">Precise location</string>
```

- [ ] **Step 5: 검증한다**

Run: `grep -n 'allowBackup\|dataExtractionRules' app/src/main/AndroidManifest.xml && grep -n 'RequestMultiplePermissions\|ACCESS_COARSE_LOCATION' app/src/main/java/com/recordofp/app/ui/onboarding/OnboardingScreen.kt`
Expected: `allowBackup="false"`, 규칙 참조, 다중 권한 요청이 보인다.

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: 테스트 전부 통과, lint 오류 0, 빌드 성공

- [ ] **Step 6: 커밋한다**

```bash
git add android/app/src/main/AndroidManifest.xml android/app/src/main/res/xml/data_extraction_rules.xml \
  android/app/src/main/java/com/recordofp/app/ui/onboarding/OnboardingScreen.kt \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml
git commit -m "fix: 위치·항목 데이터 백업 차단과 FINE+COARSE 동시 요청" -m "allowBackup을 끄고 기기 이전에서도 DB·DataStore를 뺀다. Android 12+가 FINE 단독 요청을 무시하지 않도록
COARSE와 함께 요청하고 '정확한 위치'를 안내한다. (§4.2, §9, 검토 B2·B8)"
```

---

### Task 6: 보호 상태 판정 보강 — 알림 채널·기기 위치, 설정 화면 열기 가드

원격 대시보드는 앱 알림 허용만 본다. 그래서 알림을 길게 눌러 "근처 알림" 채널만 끄면, 대시보드는 "보호됨"인데 알림은 오지 않는다. 같은 이유로 `NearbyNotifier.show()`도 채널이 꺼진 줄 모르고 true를 돌려주고, 그 결과 쿨다운과 일 상한이 소모된다(검토 C2). 기기 위치(시스템 토글)가 꺼져도 OS 펜스가 전부 사라지는데 아무 데도 표시되지 않는다. 이 태스크는 두 가지 판단을 한곳에 둔다. "근처 알림이 보일 수 있는가"는 data에 둔다(ui와 platform이 모두 쓰므로). 기기 위치 상태는 스냅샷에 더한다. 설정 화면 이동은 제조사마다 없는 화면이 있으므로 크래시 가드 헬퍼로 연다.

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/domain/model/NotificationChannels.kt`
- Create: `android/app/src/main/java/com/recordofp/app/data/notify/NearbyAlerts.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/notify/Notifier.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/notify/NearbyNotifier.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/permissions/PermissionStatus.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`
- Modify: `CLAUDE.md`
- Test: `android/app/src/test/java/com/recordofp/app/ui/permissions/PermissionSnapshotTest.kt` (생성)

**Interfaces:**
- Consumes: Task 5의 문자열 `settings_perm_location`
- Produces:
  - `object NotificationChannels { const val NEARBY = "nearby"; const val STATUS = "status" }` (domain/model)
  - `fun nearbyAlertsEnabled(context: Context): Boolean` (data/notify)
  - `data class PermissionSnapshot(notifications, fineLocation, backgroundLocation, batteryUnrestricted, locationServicesOn)`, `fullyProtected`에 `locationServicesOn` 포함
  - `fun appNotificationSettingsIntent(context: Context): Intent`, `fun locationSourceSettingsIntent(): Intent`, `fun batteryOptimizationSettingsIntent(): Intent`, `fun Context.openSettings(intent: Intent)` (ui/permissions) — Task 13 배너가 쓴다

- [ ] **Step 1: 실패 테스트를 쓴다**

`PermissionSnapshotTest.kt`:

```kotlin
package com.recordofp.app.ui.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionSnapshotTest {

    private val allOn = PermissionSnapshot(
        notifications = true, fineLocation = true, backgroundLocation = true,
        batteryUnrestricted = false, locationServicesOn = true,
    )

    @Test
    fun `모두 켜져 있으면 완전 보호 상태다 - 배터리 예외는 권장일 뿐이다`() {
        assertTrue(allOn.fullyProtected)
    }

    @Test
    fun `기기 위치가 꺼져 있으면 권한이 다 있어도 완전 보호가 아니다`() {
        assertFalse(allOn.copy(locationServicesOn = false).fullyProtected)
    }

    @Test
    fun `알림·정확한 위치·항상 허용 중 하나라도 꺼지면 완전 보호가 아니다`() {
        assertFalse(allOn.copy(notifications = false).fullyProtected)
        assertFalse(allOn.copy(fineLocation = false).fullyProtected)
        assertFalse(allOn.copy(backgroundLocation = false).fullyProtected)
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.PermissionSnapshotTest"`
Expected: 컴파일 실패 — `locationServicesOn` 파라미터가 없다.

- [ ] **Step 3: 채널 id와 공용 판단을 만든다**

`domain/model/NotificationChannels.kt`:

```kotlin
package com.recordofp.app.domain.model

/**
 * 알림 채널 id (스펙 §6.5). platform(채널 생성·발행)과 ui·data(보호 상태 판단)가 함께 쓴다 —
 * ui가 platform을 import하지 않도록 순수 Kotlin 쪽에 둔다 (최종 리뷰 I4)
 */
object NotificationChannels {
    /** 근처 알림 (중요도 높음) */
    const val NEARBY = "nearby"

    /** 서비스 상태 (중요도 낮음) */
    const val STATUS = "status"
}
```

`data/notify/NearbyAlerts.kt`:

```kotlin
package com.recordofp.app.data.notify

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.recordofp.app.domain.model.NotificationChannels

/**
 * 근처 알림이 실제로 보일 수 있는가 — 앱 알림(A13+는 POST_NOTIFICATIONS 포함)과 "근처 알림" 채널이 모두 켜져 있어야 한다
 * (검토 C2, 최종 리뷰 I4). 발행(platform NearbyNotifier)과 보호 상태 판정(ui PermissionSnapshot)이 같은 판단을 쓰도록 data에 둔다.
 */
fun nearbyAlertsEnabled(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    // 채널이 아직 없으면(첫 실행 직후) 꺼진 것이 아니다
    val channel = manager.getNotificationChannel(NotificationChannels.NEARBY) ?: return true
    return channel.importance != NotificationManager.IMPORTANCE_NONE
}
```

- [ ] **Step 4: platform이 공용 상수와 판단을 쓰게 한다**

`Notifier.kt`: `CHANNEL_NEARBY`/`CHANNEL_STATUS` 상수를 지우고 `NotificationChannels.NEARBY`/`NotificationChannels.STATUS`를 쓴다. KDoc을 `/** 알림 채널 2개를 만든다 (스펙 §6.5). 채널 id는 ui와 공유하는 NotificationChannels (최종 리뷰 I4) */`로 단다.

`NearbyNotifier.kt`의 `show` 앞부분을 바꾼다(import `android.annotation.SuppressLint`, `com.recordofp.app.data.notify.nearbyAlertsEnabled`, `com.recordofp.app.domain.model.NotificationChannels`).

```kotlin
    /**
     * @return 알림이 실제로 발행됐으면 true. 앱 알림이나 "근처 알림" 채널이 꺼져 있으면 false —
     * 호출부가 표시 기록(쿨다운·상한 소모) 여부를 가른다 (검토 C2, M1)
     */
    @SuppressLint("MissingPermission") // nearbyAlertsEnabled()가 areNotificationsEnabled()로 POST_NOTIFICATIONS 부여 여부를 확인한다
    fun show(group: AlertGroup): Boolean {
        if (!nearbyAlertsEnabled(context)) return false
```

같은 함수의 `NotificationCompat.Builder(context, Notifier.CHANNEL_NEARBY)`를 `NotificationCompat.Builder(context, NotificationChannels.NEARBY)`로 바꾼다.

- [ ] **Step 5: 스냅샷과 설정 이동 헬퍼를 바꾼다**

`PermissionStatus.kt` 전체를 바꾼다.

```kotlin
package com.recordofp.app.ui.permissions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.recordofp.app.data.notify.nearbyAlertsEnabled

data class PermissionSnapshot(
    /** 앱 알림과 "근처 알림" 채널이 모두 켜져 있는가 — 채널만 꺼도 알림은 오지 않는다 (최종 리뷰 I4) */
    val notifications: Boolean,
    /** "정확한 위치" — 사용자가 "대략적 위치"만 허용하면 false다. 지오펜스는 FINE이 필요하다 (검토 B2) */
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
    val batteryUnrestricted: Boolean,
    /** 기기 위치(시스템 토글)가 켜져 있는가 — 꺼지면 OS가 펜스를 전부 지운다 (최종 리뷰 I4) */
    val locationServicesOn: Boolean,
) {
    // 배터리 최적화는 의도적으로 제외 — 배너 과잉 노출 방지, 대시보드(§4.3)에서만 표시
    val fullyProtected: Boolean
        get() = notifications && fineLocation && backgroundLocation && locationServicesOn
}

fun readPermissionSnapshot(context: Context): PermissionSnapshot {
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val pm = context.getSystemService(PowerManager::class.java)
    return PermissionSnapshot(
        notifications = nearbyAlertsEnabled(context),
        fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        batteryUnrestricted = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false,
        locationServicesOn = context.getSystemService(LocationManager::class.java)
            ?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false,
    )
}

/** A11+ 백그라운드 위치는 앱 설정에서만 켤 수 있다 (§4.2) — 설정 화면 딥링크 */
fun appDetailsSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/** 앱 알림 설정 — 채널 목록도 여기서 보인다 */
fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** 기기 위치 켜기 (최종 리뷰 I4) */
fun locationSourceSettingsIntent(): Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

/** 배터리 최적화 목록 — 예외를 직접 요청하지 않고 안내만 한다 (Play 정책, §4.2) */
fun batteryOptimizationSettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

/**
 * 시스템 설정 화면을 연다. 일부 제조사 빌드에 없는 화면이면 앱 상세 설정으로,
 * 그것마저 없으면 아무것도 하지 않는다 (최종 리뷰 M8)
 */
fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(appDetailsSettingsIntent(this))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
```

- [ ] **Step 6: 설정 대시보드에 기기 위치 행을 넣고 가드로 연다**

`SettingsScreen.kt`의 `protectionRows`를 바꾼다.

```kotlin
/** 보호 상태 행 — 꺼진 행 탭 시 이동할 시스템 설정 화면을 함께 담는다 (§4.3). */
private fun protectionRows(context: Context, snapshot: PermissionSnapshot): List<ProtectionRow> = listOf(
    ProtectionRow(R.string.settings_perm_notifications, snapshot.notifications, appNotificationSettingsIntent(context)),
    ProtectionRow(R.string.settings_perm_location, snapshot.fineLocation, appDetailsSettingsIntent(context)),
    ProtectionRow(R.string.settings_perm_background, snapshot.backgroundLocation, appDetailsSettingsIntent(context)),
    // 기기 위치가 꺼지면 OS가 펜스를 전부 지운다 — 권한이 다 있어도 알림이 오지 않는 이유 (최종 리뷰 I4)
    ProtectionRow(R.string.settings_perm_location_services, snapshot.locationServicesOn, locationSourceSettingsIntent()),
    // 목록 화면만 연다 — ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS(자동 요청)는 절대 쓰지 않는다 (§4.2)
    ProtectionRow(R.string.settings_perm_battery, snapshot.batteryUnrestricted, batteryOptimizationSettingsIntent()),
)
```

행 클릭을 `ProtectionPermissionRow(row, onClick = { context.openSettings(row.intent) })`로 바꾼다. import에 `appNotificationSettingsIntent`, `locationSourceSettingsIntent`, `batteryOptimizationSettingsIntent`, `openSettings`(모두 `com.recordofp.app.ui.permissions`)를 더하고, 쓰이지 않게 된 `android.provider.Settings` import를 지운다.

- [ ] **Step 7: 문자열을 더한다**

ko: `<string name="settings_perm_location_services">기기 위치 서비스</string>`
en: `<string name="settings_perm_location_services">Device location</string>`

- [ ] **Step 8: CLAUDE.md의 채널 설명을 고친다**

아래 줄을 찾아

```markdown
- 알림 채널은 `nearby`(높음)와 `status`(낮음) 두 개다(`Notifier`).
```

다음으로 바꾼다.

```markdown
- 알림 채널은 `nearby`(높음)와 `status`(낮음) 두 개다. id는 `domain/model/NotificationChannels`, 생성은 `Notifier`가 한다. "근처 알림이 보일 수 있는가"(앱 알림 + nearby 채널)는 `data/notify/nearbyAlertsEnabled` 하나로 판단한다. 발행과 보호 상태가 같은 판단을 쓴다.
```

- [ ] **Step 9: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.PermissionSnapshotTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → 전부 통과, lint 오류 0

- [ ] **Step 10: 커밋한다**

```bash
git add CLAUDE.md android/app/src/main/java/com/recordofp/app/domain/model/NotificationChannels.kt \
  android/app/src/main/java/com/recordofp/app/data/notify/NearbyAlerts.kt \
  android/app/src/main/java/com/recordofp/app/platform/notify/Notifier.kt \
  android/app/src/main/java/com/recordofp/app/platform/notify/NearbyNotifier.kt \
  android/app/src/main/java/com/recordofp/app/ui/permissions/PermissionStatus.kt \
  android/app/src/main/java/com/recordofp/app/ui/settings/SettingsScreen.kt \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml \
  android/app/src/test/java/com/recordofp/app/ui/permissions/PermissionSnapshotTest.kt
git commit -m "fix: 근처 알림 채널·기기 위치를 보호 상태와 발행에 반영" -m "채널만 꺼도 대시보드가 꺼짐으로 보이고 알림이 상한을 소모하지 않는다. 기기 위치 행을 추가하고,
시스템 설정 이동은 ActivityNotFoundException 가드로 연다. (§4.3, §6.5, 검토 C2, 최종 리뷰 I4·M8)"
```

---

## 묶음 C — 알림 정확도

### Task 7: 알림 묶음 정리 — 이벤트 안 중복 제거와 펜스 기준 알림 id

원격은 NotificationLog를 표시한 **뒤에** 기록한다(`recordShown`). 그래서 같은 이벤트 안에서는 항목 쿨다운이 걸리지 않는다. 같은 항목이 두 펜스로 동시에 통과하면 알림이 두 번 뜬다(실기기 09-03 14:57:09 — rem=1, 두 POI). 이 태스크는 이벤트 안에서 이미 통과한 항목을 다음 펜스에서 건너뛴다. 또 알림 id를 펜스 기준으로 바꾼다. Task 8에서 PLACE 펜스에 카카오 id가 실리면, POI 기준 id로는 같은 가게의 PLACE 알림과 카테고리 알림이 서로 덮어쓰기 때문이다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/data/engine/GeofenceEventHandler.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/notify/NearbyNotifier.kt`
- Test: `android/app/src/test/java/com/recordofp/app/data/engine/GeofenceEventHandlerTest.kt`

**Interfaces:**
- Consumes: 원격 `GeofenceEventHandler.onFenceEvent(fenceIds, triggeringPoint)`
- Produces:
  - `data class AlertGroup(val fenceId: String, val poiId: String?, val poiName: String?, val reminders: List<Reminder>, val distanceM: Int? = null)`
  - `GeofenceEventHandler.LOG_CAUSE = "FENCE_EVENT"`, `GeofenceEventHandler.BLOCK_SAME_EVENT = "BLOCK_SAME_EVENT"`, private `logEvent(at: Instant, result: String, note: String)` — Task 9·10이 쓴다

- [ ] **Step 1: 테스트 헬퍼와 실패 테스트를 쓴다**

`GeofenceEventHandlerTest`의 `spec(...)` 아래에 헬퍼를 더한다.

```kotlin
    private fun placeReg(fenceId: String, kakaoId: String?, name: String) = GeofenceRegEntity(
        geofenceId = fenceId, kind = "PLACE", lat = 37.5, lng = 127.0, radiusM = 150f,
        poiName = name, poiKakaoId = kakaoId, matchKey = fenceId, reseedBatchId = "b", registeredAt = 0,
    )
    private fun placeSpec(id: Long, reminderId: Long, kakaoId: String?) = TriggerSpecEntity(
        id = id, reminderId = reminderId, type = "PLACE", categoryId = null, brandKeyword = null,
        placeName = "CU 역삼점", placeKakaoId = kakaoId, placeLat = 37.5, placeLng = 127.0,
    )
```

기존 `통과한 리마인더는 POI 그룹으로 묶이고 ...` 테스트의 `assertEquals("CU 역삼점", group.poiName)` 아래에 `assertEquals("poi:100", group.fenceId)`를 더한다. 새 테스트 두 개를 추가한다.

```kotlin
    @Test
    fun `한 이벤트에서 같은 항목이 두 펜스로 통과해도 한 묶음에만 들어간다`() = runTest {
        // 같은 가게를 PLACE로도, 카테고리로도 걸어 둔 항목 — 이벤트 하나에 두 펜스가 함께 들어온다.
        // 실기기 09-03 14:57:09: 같은 항목 알림이 두 POI에서 동시에 떴다
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            regs["place:20"] = placeReg("place:20", kakaoId = "100", name = "CU 역삼점")
            links += listOf(RegTriggerEntity("poi:100", 11), RegTriggerEntity("place:20", 20))
        }
        val runs = FakeRuns()
        val handler = build(
            regs,
            FakeSpecs(mapOf(11L to spec(11, 1), 20L to placeSpec(20, 1, "100"))),
            FakeReminderDao(mapOf(1L to reminder(1))),
            runs = runs,
        )

        val out = handler.onFenceEvent(listOf("poi:100", "place:20"), null)

        assertEquals(listOf("poi:100"), out.groups.map { it.fenceId })
        assertTrue(runs.entries.any { it.result == "BLOCK_SAME_EVENT" })
    }

    @Test
    fun `같은 이벤트라도 서로 다른 항목은 각자의 펜스 묶음에 남는다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            regs["poi:200"] = poiReg("poi:200", "200", "GS25 역삼점")
            links += listOf(RegTriggerEntity("poi:100", 11), RegTriggerEntity("poi:200", 12))
        }
        val handler = build(
            regs,
            FakeSpecs(mapOf(11L to spec(11, 1), 12L to spec(12, 2))),
            FakeReminderDao(mapOf(1L to reminder(1), 2L to reminder(2))),
        )

        val out = handler.onFenceEvent(listOf("poi:100", "poi:200"), null)

        assertEquals(listOf("poi:100", "poi:200"), out.groups.map { it.fenceId })
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.GeofenceEventHandlerTest"`
Expected: 컴파일 실패 — `AlertGroup.fenceId`가 없다.

- [ ] **Step 3: 핸들러를 고친다**

`AlertGroup`을 바꾼다.

```kotlin
/** fenceId: 알림 ID의 기준 — 같은 POI를 대변하는 서로 다른 펜스(PLACE·CATEGORY)의 알림이 서로 덮어쓰지 않게 한다 */
data class AlertGroup(
    val fenceId: String,
    val poiId: String?,
    val poiName: String?,
    val reminders: List<Reminder>,
    val distanceM: Int? = null,
)
```

`onFenceEvent`의 루프를 바꾼다(`val groups = ...` 다음 줄부터 `return` 전까지).

```kotlin
        // 한 이벤트에서 같은 항목이 여러 펜스로 통과해도 알림은 한 번만 — 항목 쿨다운은 표시 뒤(recordShown)에야
        // 기록되므로 같은 이벤트 안에서는 걸리지 않는다 (실기기 09-03 14:57:09)
        val passedInThisEvent = mutableSetOf<Long>()

        for (fenceId in fenceIds) {
            val reg = regDao.byId(fenceId) ?: continue // stale 이벤트 폐기 (§6.5.1)
            if (reg.kind == FenceKind.SENTINEL.name) { sentinelExited = true; continue }

            val reminderIds = triggerSpecDao.byIds(regDao.triggerIdsFor(fenceId))
                .map { it.reminderId }.distinct()
            val passed = mutableListOf<Reminder>()

            for (reminderId in reminderIds) {
                if (reminderId in passedInThisEvent) {
                    logEvent(now, BLOCK_SAME_EVENT, "reminder=$reminderId poi=${reg.poiName}")
                    continue
                }
                val row = reminderDao.byId(reminderId) ?: continue
                val history = NotificationGate.History(
                    lastShownForItem = notificationLogDao.lastShownForItem(reminderId)?.let(Instant::ofEpochMilli),
                    lastShownForItemAtPoi = reg.poiKakaoId?.let {
                        notificationLogDao.lastShownForItemAtPoi(reminderId, it)?.let(Instant::ofEpochMilli)
                    },
                    shownTodayForItem = notificationLogDao.countForItemSince(reminderId, startOfDay),
                    shownTodayTotal = notificationLogDao.countTotalSince(startOfDay),
                )
                val decision = gate.evaluate(
                    now = now,
                    status = ReminderStatus.valueOf(row.status),
                    snoozeUntil = row.snoozeUntil?.let(Instant::ofEpochMilli),
                    history = history,
                    policy = policy,
                )
                if (decision == NotificationGate.Decision.PASS) {
                    passed += Reminder(
                        id = row.id, title = row.title, memo = row.memo,
                        status = ReminderStatus.valueOf(row.status), snoozeUntil = row.snoozeUntil,
                        createdAt = row.createdAt, updatedAt = row.updatedAt, completedAt = row.completedAt,
                    )
                    passedInThisEvent += reminderId
                    // NotificationLog 기록은 실제 표시 후(recordShown) — 여기서 기록하면 표시 전 카운트가 된다 (M1)
                } else {
                    logEvent(now, decision.name, "reminder=$reminderId poi=${reg.poiName}")
                }
            }
            if (passed.isNotEmpty()) {
                val distanceM = triggeringPoint?.let {
                    distanceMeters(it, GeoPoint(reg.lat, reg.lng)).roundToInt()
                }
                groups += AlertGroup(reg.geofenceId, reg.poiKakaoId, reg.poiName, passed, distanceM)
            }
        }
```

클래스 끝에 추가한다.

```kotlin
    private suspend fun logEvent(at: Instant, result: String, note: String) = runLogDao.insert(
        EngineRunLogEntity(at = at.toEpochMilli(), cause = LOG_CAUSE, result = result, registeredCount = 0, note = note),
    )

    companion object {
        /** EngineRunLog.cause — 리시버의 오류 기록도 같은 원인으로 남긴다 */
        const val LOG_CAUSE = "FENCE_EVENT"

        /** 같은 이벤트에서 이미 다른 펜스로 통과한 항목 (진단 화면에서 차단으로 보인다) */
        const val BLOCK_SAME_EVENT = "BLOCK_SAME_EVENT"
    }
```

- [ ] **Step 4: 알림 id를 펜스 기준으로 바꾼다**

`NearbyNotifier.show`의 `val notificationId = (group.poiId ?: group.poiName ?: "poi").hashCode()`를 바꾼다.

```kotlin
        // 펜스 단위로 알림 1건 — POI가 같아도 펜스(PLACE·CATEGORY)가 다르면 별개 알림이라 서로 덮어쓰지 않는다
        val notificationId = group.fenceId.hashCode()
```

- [ ] **Step 5: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.GeofenceEventHandlerTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug` → 전부 통과, lint 오류 0

- [ ] **Step 6: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/data/engine/GeofenceEventHandler.kt \
  android/app/src/main/java/com/recordofp/app/platform/notify/NearbyNotifier.kt \
  android/app/src/test/java/com/recordofp/app/data/engine/GeofenceEventHandlerTest.kt
git commit -m "fix: 한 이벤트 안 같은 항목 중복 알림 제거와 펜스 기준 알림 id" -m "같은 항목이 두 펜스로 동시에 통과해도 알림은 한 번만 뜬다(실기기 09-03 사례). 알림 id를 펜스 기준으로 바꿔
같은 가게의 PLACE·카테고리 알림이 서로 덮어쓰지 않는다. (§6.5)"
```

---

### Task 8: PLACE 펜스에 카카오 지점 id 싣기 — 같은 항목·같은 지점 24시간 쿨다운

`PlaceRequest`가 카카오 id를 버리고 플래너가 PLACE 펜스의 `poiId`를 비워 둔다. 그래서 PLACE에는 "같은 항목·같은 지점 24h" 쿨다운(§4.5)이 걸리지 않고 항목 쿨다운(4h)만 걸린다. 매일 드나드는 지점이면 4시간마다 다시 울린다. 핸들러는 이미 `reg.poiKakaoId`로 쿨다운을 계산하므로, 해석(resolver) → 계획(planner) → 미러 기록(service)으로 id만 넘기면 된다. 편집 화면은 id 없는 옛 PLACE를 `""`로 다시 저장할 수 있으므로 빈 id는 없는 것으로 본다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/domain/engine/TriggerResolver.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/domain/engine/FencePlan.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/domain/engine/ReseedPlanner.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/data/engine/ReseedService.kt`
- Test: `TriggerResolverTest.kt`, `ReseedPlannerTest.kt`, `ReseedServiceTest.kt`, `GeofenceEventHandlerTest.kt`

**Interfaces:**
- Consumes: Task 7의 `placeReg`/`placeSpec` 테스트 헬퍼와 펜스 기준 알림 id
- Produces: `PlaceRequest(matchKey, point, name, kakaoId: String? = null)`, `TriggerCandidates(..., placeKakaoId: String? = null, ...)`

- [ ] **Step 1: 실패 테스트를 쓴다**

`TriggerResolverTest`에 추가한다.

```kotlin
    @Test
    fun `PLACE 요청은 카카오 지점 id를 싣고 빈 id는 버린다`() {
        fun place(id: Long, kakaoId: String?) = TriggerSpec(
            id = id, reminderId = 1, type = TriggerType.PLACE,
            placeName = "우리집 앞 GS25", placeKakaoId = kakaoId, placePoint = GeoPoint(37.5, 127.0),
        )
        val out = resolver.resolve(listOf(place(7, "k9"), place(8, ""))).associateBy { it.matchKey }
        assertEquals("k9", (out.getValue("place:7") as PlaceRequest).kakaoId)
        assertEquals(null, (out.getValue("place:8") as PlaceRequest).kakaoId)
    }
```

`ReseedPlannerTest`에 추가한다.

```kotlin
    @Test
    fun `PLACE 펜스는 카카오 지점 id를 poiId로 싣는다 (검토 C1)`() {
        val place = TriggerCandidates(
            matchKey = "place:1", isPlace = true,
            placePoint = GeoPoint(37.5100, 127.0000), placeName = "우리집 앞 GS25", placeKakaoId = "k9",
        )
        val fence = ReseedPlanner().plan(here, listOf(place)).single { it.kind == FenceKind.PLACE }
        assertEquals("k9", fence.poiId)
    }
```

`ReseedServiceTest`에 추가한다.

```kotlin
    @Test
    fun `PLACE 펜스의 미러에 카카오 지점 id가 남는다 (검토 C1)`() = runTest {
        val regDao = FakeRegDao()
        val service = build(FakeReminders(listOf(place.copy(placeKakaoId = "k9"))), FakePoi(), regDao = regDao)
        service.reseed(ReseedCause.ITEM_CHANGE, here)
        assertEquals("k9", regDao.regs.getValue("place:20").poiKakaoId)
    }
```

`GeofenceEventHandlerTest`에 추가한다. 이 테스트는 핸들러 쪽 동작을 문서로 고정한다(핸들러는 이미 `poiKakaoId`를 쓰므로 이 테스트만으로는 실패하지 않는다. 실제 수정은 위 세 테스트가 잡는다).

```kotlin
    @Test
    fun `PLACE 펜스도 같은 항목·같은 지점 24시간 쿨다운이 걸린다 (검토 C1)`() = runTest {
        val regs = FakeRegs().apply {
            regs["place:20"] = placeReg("place:20", kakaoId = "k9", name = "집 앞 CU")
            links += RegTriggerEntity("place:20", 20)
        }
        val notifLog = FakeNotifLog().apply { // 5시간 전 같은 지점 알림 — 항목 쿨다운(4h)은 지났지만 24h 안
            rows += NotificationLogEntity(reminderId = 1, poiKakaoId = "k9", shownAt = noon.toEpochMilli() - 5 * 3_600_000)
        }
        val runs = FakeRuns()
        val handler = build(
            regs, FakeSpecs(mapOf(20L to placeSpec(20, 1, "k9"))), FakeReminderDao(mapOf(1L to reminder(1))), notifLog, runs,
        )

        val out = handler.onFenceEvent(listOf("place:20"), null)

        assertTrue(out.groups.isEmpty())
        assertTrue(runs.entries.any { it.result == "BLOCK_PLACE_COOLDOWN" })
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.TriggerResolverTest" --tests "*.ReseedPlannerTest" --tests "*.ReseedServiceTest" --tests "*.GeofenceEventHandlerTest"`
Expected: 컴파일 실패 — `PlaceRequest.kakaoId`, `TriggerCandidates.placeKakaoId`가 없다.

- [ ] **Step 3: id를 해석→계획→미러로 넘긴다**

`TriggerResolver.kt`:

```kotlin
data class PlaceRequest(
    override val matchKey: String,
    val point: GeoPoint,
    val name: String?,
    /** 카카오 지점 id — PLACE 펜스의 poiId가 되어 "같은 항목·같은 지점 24h" 쿨다운의 키가 된다 (§4.5, 검토 C1) */
    val kakaoId: String? = null,
) : TriggerRequest
```

```kotlin
        TriggerType.PLACE -> {
            val point = spec.placePoint ?: return null
            // 편집 화면은 id 없는 옛 PLACE를 ""로 다시 저장할 수 있다 — 빈 id는 없는 것으로 본다
            PlaceRequest(spec.matchKey, point, spec.placeName, spec.placeKakaoId?.takeIf { it.isNotBlank() })
        }
```

`FencePlan.kt`의 `TriggerCandidates`에서 `val placeName: String? = null,` 다음 줄에 `val placeKakaoId: String? = null,`을 넣는다.

`ReseedPlanner.kt` PLACE 펜스의 `poiName = p.placeName,` 다음 줄에 `poiId = p.placeKakaoId,`를 넣는다.

`ReseedService.kt` `is PlaceRequest -> TriggerCandidates(...)`의 `placePoint = req.point, placeName = req.name,`를 `placePoint = req.point, placeName = req.name, placeKakaoId = req.kakaoId,`로 바꾼다.

- [ ] **Step 4: 테스트 통과와 전체 검증을 확인한다**

Run: 위 Step 2 명령 → PASS
Run: `./gradlew testDebugUnitTest lintDebug` → 전부 통과, lint 오류 0

- [ ] **Step 5: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/domain/engine/TriggerResolver.kt \
  android/app/src/main/java/com/recordofp/app/domain/engine/FencePlan.kt \
  android/app/src/main/java/com/recordofp/app/domain/engine/ReseedPlanner.kt \
  android/app/src/main/java/com/recordofp/app/data/engine/ReseedService.kt \
  android/app/src/test/java/com/recordofp/app/domain/engine/TriggerResolverTest.kt \
  android/app/src/test/java/com/recordofp/app/domain/engine/ReseedPlannerTest.kt \
  android/app/src/test/java/com/recordofp/app/data/engine/ReseedServiceTest.kt \
  android/app/src/test/java/com/recordofp/app/data/engine/GeofenceEventHandlerTest.kt
git commit -m "fix: PLACE 펜스에 카카오 지점 id를 실어 24시간 지점 쿨다운 적용" -m "매일 드나드는 PLACE가 4시간마다 다시 울리지 않는다. 빈 id는 없는 것으로 본다. (§4.5, §6.5, 검토 C1)"
```

---

### Task 9: 표시 결과를 진단에 남기기 — PASS·stale·알림 꺼짐

원격 진단 화면에는 차단된 경우만 보이고, 실제로 알림이 떴는지(PASS)와 등록에 없는 이벤트(stale)는 보이지 않는다. 그래서 필드 테스트(§10.2)에서 발화·미발화를 집계할 수 없다. 이 태스크는 PASS를 **실제로 표시한 뒤에** 기록하고(표시 결과를 아는 곳은 리시버뿐이다), stale은 판정할 때 기록한다. 표시하지 못한 묶음(알림이나 채널 꺼짐)은 쿨다운을 소모하지 않고 `BLOCK_NOTIFICATIONS_OFF`로 남긴다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/data/engine/GeofenceEventHandler.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceBroadcastReceiver.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/settings/DiagnosticsScreen.kt`
- Test: `android/app/src/test/java/com/recordofp/app/data/engine/GeofenceEventHandlerTest.kt`

**Interfaces:**
- Consumes: Task 7의 `AlertGroup`, `logEvent`, `LOG_CAUSE`. Task 6의 "채널 꺼짐이면 `show()`가 false"
- Produces: `suspend fun recordShown(group: AlertGroup)`, `suspend fun recordNotShown(group: AlertGroup)` — Task 10이 쓴다

- [ ] **Step 1: 실패 테스트를 쓴다**

기존 `통과한 리마인더는 POI 그룹으로 묶이고 recordShown 호출로 NotificationLog가 기록된다`를 바꾼다.

```kotlin
    @Test
    fun `통과한 리마인더는 POI 그룹으로 묶이고 표시 후 기록이 NotificationLog와 PASS를 남긴다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            links += listOf(RegTriggerEntity("poi:100", 11), RegTriggerEntity("poi:100", 12))
        }
        val notifLog = FakeNotifLog(); val runs = FakeRuns()
        val handler = build(
            regs,
            FakeSpecs(mapOf(11L to spec(11, 1), 12L to spec(12, 2))),
            FakeReminderDao(mapOf(1L to reminder(1), 2L to reminder(2))),
            notifLog = notifLog, runs = runs,
        )
        val out = handler.onFenceEvent(listOf("poi:100"), null)
        val group = out.groups.single()
        assertEquals("CU 역삼점", group.poiName)
        assertEquals("poi:100", group.fenceId)
        assertEquals(listOf(1L, 2L), group.reminders.map { it.id })
        assertTrue(notifLog.rows.isEmpty()) // 표시 전이므로 아직 기록되지 않는다 (M1)

        handler.recordShown(group)

        assertEquals(2, notifLog.rows.size) // 리마인더별 1행 (쿨다운 원본)
        assertEquals(2, runs.entries.count { it.result == "PASS" }) // 진단에 발화로 보인다 (검토 B7)
    }
```

기존 `등록에 없는 stale 이벤트는 무시된다`를 바꾼다.

```kotlin
    @Test
    fun `등록에 없는 stale 이벤트는 무시되고 진단에 남는다`() = runTest {
        val runs = FakeRuns()
        val handler = build(FakeRegs(), FakeSpecs(emptyMap()), FakeReminderDao(emptyMap()), runs = runs)
        val out = handler.onFenceEvent(listOf("poi:ghost"), null)
        assertTrue(out.groups.isEmpty())
        assertTrue(!out.sentinelExited)
        assertEquals("STALE", runs.entries.single().result)
    }
```

새 테스트를 더한다.

```kotlin
    @Test
    fun `표시하지 못한 묶음은 쿨다운을 소모하지 않고 사유를 남긴다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            links += RegTriggerEntity("poi:100", 11)
        }
        val notifLog = FakeNotifLog(); val runs = FakeRuns()
        val handler = build(regs, FakeSpecs(mapOf(11L to spec(11, 1))), FakeReminderDao(mapOf(1L to reminder(1))), notifLog, runs)
        val group = handler.onFenceEvent(listOf("poi:100"), null).groups.single()

        handler.recordNotShown(group)

        assertTrue(notifLog.rows.isEmpty()) // 보이지 않은 알림으로 상한·쿨다운을 소모하지 않는다 (검토 C2)
        assertTrue(runs.entries.any { it.result == "BLOCK_NOTIFICATIONS_OFF" })
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.GeofenceEventHandlerTest"`
Expected: 컴파일 실패 — `recordShown(group)`, `recordNotShown`이 없다.

- [ ] **Step 3: 핸들러에 기록 함수를 넣는다**

stale 줄을 바꾼다.

```kotlin
            val reg = regDao.byId(fenceId)
            if (reg == null) { // stale 이벤트 폐기 (§6.5.1) — 진단에는 남긴다 (검토 B7)
                logEvent(now, "STALE", "fence=$fenceId")
                continue
            }
```

`recordShown`을 교체하고 `recordNotShown`을 더한다.

```kotlin
    /** 알림이 실제로 화면에 뜬 뒤: 쿨다운 원본(NotificationLog)과 진단용 PASS를 남긴다 (§6.5 3·5단계, 검토 B7) */
    suspend fun recordShown(group: AlertGroup) {
        val shown = clock.instant()
        group.reminders.forEach { r ->
            notificationLogDao.insert(NotificationLogEntity(reminderId = r.id, poiKakaoId = group.poiId, shownAt = shown.toEpochMilli()))
            logEvent(shown, NotificationGate.Decision.PASS.name, "reminder=${r.id} poi=${group.poiName}")
        }
    }

    /** 알림 권한이나 "근처 알림" 채널이 꺼져 표시하지 못했을 때 — 상한·쿨다운은 소모하지 않는다 (검토 C2) */
    suspend fun recordNotShown(group: AlertGroup) {
        val at = clock.instant()
        group.reminders.forEach { r -> logEvent(at, "BLOCK_NOTIFICATIONS_OFF", "reminder=${r.id} poi=${group.poiName}") }
    }
```

- [ ] **Step 4: 리시버 호출부를 바꾼다**

`GeofenceBroadcastReceiver`의 `if (notifier.show(group)) handler.recordShown(group.reminders.map { it.id }, group.poiId)`를 바꾼다.

```kotlin
                    // 표시가 실제로 성공했을 때만 쿨다운을 소모한다 (M1, 검토 C2)
                    if (notifier.show(group)) handler.recordShown(group) else handler.recordNotShown(group)
```

- [ ] **Step 5: 진단 화면에서 PASS를 발화로 보이게 한다**

`DiagnosticsScreen.kt`의 `resultDotColor` 첫 줄을 바꾼다.

```kotlin
    result.contains("APPLIED") || result == "PASS" -> successColor()
```

KDoc을 `/** 결과별 컬러 도트 — APPLIED·PASS 초록 · BLOCK/FAILED/NO_PERMISSION 빨강 · 그 밖 회색 (개편안 §2) */`로 고친다.

- [ ] **Step 6: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.GeofenceEventHandlerTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → 전부 통과, lint 오류 0

- [ ] **Step 7: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/data/engine/GeofenceEventHandler.kt \
  android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceBroadcastReceiver.kt \
  android/app/src/main/java/com/recordofp/app/ui/settings/DiagnosticsScreen.kt \
  android/app/src/test/java/com/recordofp/app/data/engine/GeofenceEventHandlerTest.kt
git commit -m "feat: 알림 발화·stale·표시 실패를 진단 로그에 기록" -m "필드 테스트에서 발화/미발화를 집계할 수 있다. 표시하지 못한 알림은 쿨다운을 소모하지 않는다. (§4.4, §6.5, §10.2, 검토 B7·C2)"
```

---

### Task 10: 리시버 무중단 — 센티널 우선·묶음별 격리·오류 기록

원격 리시버는 프로세스가 죽지 않게 예외를 잡고는 있다. 하지만 센티널 재배치를 알림 루프 **뒤에** 예약하고, 묶음별 예외 격리가 없다. 그래서 알림 하나가 예외를 내면(예: 방금 알림 권한 회수) 나머지 알림과 SENTINEL_EXIT가 함께 사라져 이동 감지 사슬이 끊긴다. 오류도 시스템 로그로만 남아 진단 화면에 보이지 않는다. 이 태스크는 처리 순서를 순수 함수(`runFenceEvent`)로 빼서 JVM에서 테스트한다. 센티널을 먼저 예약하고, 묶음마다 따로 처리하고, 오류는 EngineRunLog에 남긴다. 센티널 예약 자체가 실패해도 알림은 계속 발행한다(로컬 N2).

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/platform/ReceiverErrorLog.kt`
- Create: `android/app/src/main/java/com/recordofp/app/platform/geofence/FenceEventFlow.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceBroadcastReceiver.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/notify/NotificationActionReceiver.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/domain/engine/FencePlan.kt`, `ReseedPlanner.kt` (센티널 키 상수)
- Modify: `android/app/src/main/java/com/recordofp/app/ui/settings/DiagnosticsScreen.kt`
- Test: `android/app/src/test/java/com/recordofp/app/platform/geofence/FenceEventFlowTest.kt`, `android/app/src/test/java/com/recordofp/app/platform/ReceiverErrorLogTest.kt` (둘 다 생성)

**Interfaces:**
- Consumes: Task 9의 `recordShown(group)`, `recordNotShown(group)`, Task 7의 `GeofenceEventHandler.LOG_CAUSE`
- Produces: `const val SENTINEL_FENCE_KEY = "sentinel"` (domain), `internal suspend fun EngineRunLogDao.logReceiverError(clock: Clock, cause: String, error: Exception)`, `internal interface FenceEventSteps`, `internal suspend fun runFenceEvent(ids: List<String>, steps: FenceEventSteps)`

- [ ] **Step 1: 실패 테스트를 쓴다**

`ReceiverErrorLogTest.kt`:

```kotlin
package com.recordofp.app.platform

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeRunLog(private val failInsert: Boolean = false) : EngineRunLogDao {
    val entries = mutableListOf<EngineRunLogEntity>()
    override suspend fun insert(entity: EngineRunLogEntity) {
        if (failInsert) throw IllegalStateException("disk full")
        entries += entity
    }
    override fun observeRecent(limit: Int): Flow<List<EngineRunLogEntity>> = emptyFlow()
    override suspend fun pruneOlderThan(before: Long) {}
}

class ReceiverErrorLogTest {

    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC)

    @Test
    fun `리시버 오류는 원인·ERROR·예외 클래스와 메시지로 기록된다`() = runTest {
        val dao = FakeRunLog()
        dao.logReceiverError(clock, "FENCE_EVENT", SecurityException("POST_NOTIFICATIONS revoked"))
        val row = dao.entries.single()
        assertEquals(5_000L, row.at)
        assertEquals("FENCE_EVENT", row.cause)
        assertEquals("ERROR", row.result)
        assertEquals("SecurityException: POST_NOTIFICATIONS revoked", row.note)
    }

    @Test
    fun `로그 쓰기마저 실패해도 예외를 던지지 않는다`() = runTest {
        FakeRunLog(failInsert = true).logReceiverError(clock, "NOTIFICATION_ACTION", IllegalStateException("x"))
    }
}
```

`FenceEventFlowTest.kt`:

```kotlin
package com.recordofp.app.platform.geofence

import com.recordofp.app.data.engine.AlertGroup
import com.recordofp.app.data.engine.EventOutcome
import com.recordofp.app.domain.engine.SENTINEL_FENCE_KEY
import com.recordofp.app.domain.model.Reminder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeEventSteps(
    private val outcome: EventOutcome? = null,
    private val evaluateError: Exception? = null,
    private val failingFence: String? = null,
    private val hiddenFence: String? = null,
    private val sentinelFails: Boolean = false,
) : FenceEventSteps {
    val calls = mutableListOf<String>()
    val errors = mutableListOf<String>()

    override suspend fun evaluate(ids: List<String>): EventOutcome {
        calls += "evaluate"
        evaluateError?.let { throw it }
        return outcome!!
    }
    override fun scheduleSentinelReseed() {
        if (sentinelFails) throw IllegalStateException("WorkManager not initialized")
        calls += "sentinel"
    }
    override fun show(group: AlertGroup): Boolean {
        if (group.fenceId == failingFence) throw SecurityException("POST_NOTIFICATIONS revoked")
        calls += "show:${group.fenceId}"
        return group.fenceId != hiddenFence
    }
    override suspend fun recordShown(group: AlertGroup) { calls += "shown:${group.fenceId}" }
    override suspend fun recordNotShown(group: AlertGroup) { calls += "notShown:${group.fenceId}" }
    override suspend fun logError(error: Exception) { errors += error.javaClass.simpleName }
}

class FenceEventFlowTest {

    private fun group(fenceId: String) = AlertGroup(
        fenceId, poiId = fenceId, poiName = "CU",
        reminders = listOf(Reminder(id = 1, title = "건전지", createdAt = 0, updatedAt = 0)),
    )

    @Test
    fun `센티널 재배치를 알림보다 먼저 예약한다`() = runTest {
        val steps = FakeEventSteps(EventOutcome(sentinelExited = true, groups = listOf(group("poi:1"))))
        runFenceEvent(listOf("poi:1", SENTINEL_FENCE_KEY), steps)
        assertEquals(listOf("evaluate", "sentinel", "show:poi:1", "shown:poi:1"), steps.calls)
    }

    @Test
    fun `판정이 실패해도 이벤트에 센티널이 있으면 재배치를 예약하고 오류를 기록한다`() = runTest {
        val steps = FakeEventSteps(evaluateError = IllegalStateException("db closed"))
        runFenceEvent(listOf("poi:1", SENTINEL_FENCE_KEY), steps) // 예외가 밖으로 나오지 않는다
        assertEquals(listOf("evaluate", "sentinel"), steps.calls)
        assertEquals(listOf("IllegalStateException"), steps.errors)
    }

    @Test
    fun `판정이 실패하고 센티널이 없으면 오류만 기록한다`() = runTest {
        val steps = FakeEventSteps(evaluateError = IllegalStateException("db closed"))
        runFenceEvent(listOf("poi:1"), steps)
        assertEquals(listOf("evaluate"), steps.calls)
        assertEquals(listOf("IllegalStateException"), steps.errors)
    }

    @Test
    fun `알림 하나가 실패해도 나머지 알림은 발행되고 오류가 기록된다`() = runTest {
        val steps = FakeEventSteps(
            EventOutcome(sentinelExited = false, groups = listOf(group("poi:1"), group("poi:2"))),
            failingFence = "poi:1",
        )
        runFenceEvent(listOf("poi:1", "poi:2"), steps)
        assertEquals(listOf("evaluate", "show:poi:2", "shown:poi:2"), steps.calls)
        assertEquals(listOf("SecurityException"), steps.errors)
    }

    @Test
    fun `표시하지 못한 묶음은 표시 실패로 기록한다`() = runTest {
        val steps = FakeEventSteps(EventOutcome(false, listOf(group("poi:1"))), hiddenFence = "poi:1")
        runFenceEvent(listOf("poi:1"), steps)
        assertEquals(listOf("evaluate", "show:poi:1", "notShown:poi:1"), steps.calls)
    }

    @Test
    fun `센티널 예약이 실패해도 알림은 발행된다`() = runTest {
        val steps = FakeEventSteps(EventOutcome(true, listOf(group("poi:1"))), sentinelFails = true)
        runFenceEvent(listOf("poi:1", SENTINEL_FENCE_KEY), steps)
        assertEquals(listOf("evaluate", "show:poi:1", "shown:poi:1"), steps.calls)
        assertEquals(listOf("IllegalStateException"), steps.errors)
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.FenceEventFlowTest" --tests "*.ReceiverErrorLogTest"`
Expected: 컴파일 실패 — `SENTINEL_FENCE_KEY`, `FenceEventSteps`, `runFenceEvent`, `logReceiverError`가 없다.

- [ ] **Step 3: 센티널 키를 상수로 만든다**

`FencePlan.kt`의 `enum class FenceKind` 위에 추가한다.

```kotlin
/** 센티널 펜스의 OS requestId·미러 키 — 리시버가 이벤트에 센티널이 있는지 볼 때도 쓴다 */
const val SENTINEL_FENCE_KEY = "sentinel"
```

`ReseedPlanner.kt` 센티널의 `key = "sentinel",` → `key = SENTINEL_FENCE_KEY,`

- [ ] **Step 4: 오류 기록 헬퍼와 처리 순서를 만든다**

`platform/ReceiverErrorLog.kt`:

```kotlin
package com.recordofp.app.platform

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import java.time.Clock

/**
 * 리시버 코루틴에서 잡은 예외를 진단 로그로 남긴다 (§4.4, 최종 리뷰 I1).
 * 로그 쓰기마저 실패해도 던지지 않는다 — goAsync 코루틴 밖으로 나간 예외는 프로세스를 죽인다.
 */
internal suspend fun EngineRunLogDao.logReceiverError(clock: Clock, cause: String, error: Exception) {
    try {
        insert(
            EngineRunLogEntity(
                at = clock.millis(), cause = cause, result = "ERROR", registeredCount = 0,
                note = "${error.javaClass.simpleName}: ${error.message}",
            ),
        )
    } catch (_: Exception) {
    }
}
```

`platform/geofence/FenceEventFlow.kt`:

```kotlin
package com.recordofp.app.platform.geofence

import com.recordofp.app.data.engine.AlertGroup
import com.recordofp.app.data.engine.EventOutcome
import com.recordofp.app.domain.engine.SENTINEL_FENCE_KEY
import kotlin.coroutines.cancellation.CancellationException

/** 지오펜스 이벤트 한 번의 부수효과 포트 — 리시버가 구현하고 테스트는 페이크를 쓴다 (최종 리뷰 I1) */
internal interface FenceEventSteps {
    suspend fun evaluate(ids: List<String>): EventOutcome
    fun scheduleSentinelReseed()

    /** @return 실제로 표시했으면 true */
    fun show(group: AlertGroup): Boolean
    suspend fun recordShown(group: AlertGroup)
    suspend fun recordNotShown(group: AlertGroup)

    /** 던지지 않는다 */
    suspend fun logError(error: Exception)
}

/**
 * 지오펜스 이벤트 처리 순서 (스펙 §6.5). 어떤 예외도 밖으로 내보내지 않는다 (최종 리뷰 I1).
 * - 센티널 재배치를 알림보다 먼저 예약한다 — 알림 발행이 실패해도 이동 감지 사슬은 이어진다 (§6.2)
 * - 판정이 실패해도 이벤트에 센티널이 있으면 재배치는 예약한다
 * - 알림은 묶음마다 따로 처리한다 — 하나가 실패해도(예: 방금 알림 권한 회수) 나머지는 보인다
 */
internal suspend fun runFenceEvent(ids: List<String>, steps: FenceEventSteps) {
    try {
        val outcome = try {
            steps.evaluate(ids)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            if (SENTINEL_FENCE_KEY in ids) scheduleSentinelSafely(steps)
            throw e
        }
        if (outcome.sentinelExited) scheduleSentinelSafely(steps)
        outcome.groups.forEach { group ->
            try {
                // 표시가 실제로 성공했을 때만 쿨다운을 소모한다 (M1, 검토 C2)
                if (steps.show(group)) steps.recordShown(group) else steps.recordNotShown(group)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                steps.logError(e)
            }
        }
    } catch (c: CancellationException) {
        throw c
    } catch (e: Exception) {
        steps.logError(e)
    }
}

/** 센티널 예약이 실패해도(WorkManager 오류) 그 이벤트의 알림은 계속 발행한다 */
private suspend fun scheduleSentinelSafely(steps: FenceEventSteps) {
    try {
        steps.scheduleSentinelReseed()
    } catch (c: CancellationException) {
        throw c
    } catch (e: Exception) {
        steps.logError(e)
    }
}
```

- [ ] **Step 5: 리시버가 처리 순서에 위임하게 한다**

`GeofenceBroadcastReceiver`에 의존성을 더한다(import `com.recordofp.app.data.db.EngineRunLogDao`, `com.recordofp.app.data.engine.AlertGroup`, `com.recordofp.app.platform.logReceiverError`, `java.time.Clock`).

```kotlin
    @Inject lateinit var runLogDao: EngineRunLogDao
    @Inject lateinit var clock: Clock
```

`goAsync()` 아래 코루틴을 바꾼다.

```kotlin
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 처리 순서·예외 규칙은 runFenceEvent(JVM 테스트) — 여기는 Android 접착만 (최종 리뷰 I1)
                runFenceEvent(
                    ids,
                    object : FenceEventSteps {
                        override suspend fun evaluate(ids: List<String>) = handler.onFenceEvent(ids, triggeringPoint)
                        override fun scheduleSentinelReseed() = ReseedWorker.runNow(context, ReseedCause.SENTINEL_EXIT)
                        override fun show(group: AlertGroup) = notifier.show(group)
                        override suspend fun recordShown(group: AlertGroup) = handler.recordShown(group)
                        override suspend fun recordNotShown(group: AlertGroup) = handler.recordNotShown(group)
                        override suspend fun logError(error: Exception) =
                            runLogDao.logReceiverError(clock, GeofenceEventHandler.LOG_CAUSE, error)
                    },
                )
            } finally {
                pending.finish()
            }
        }
```

쓰이지 않게 된 `android.util.Log`, `kotlinx.coroutines.CancellationException` import를 지운다. 클래스 KDoc을 `/** 지오펜스 전이 이벤트 진입점 (스펙 §6.5). 처리 순서·예외 규칙은 runFenceEvent (최종 리뷰 I1) */`로 고친다.

- [ ] **Step 6: 알림 액션 리시버도 오류를 진단에 남긴다**

`NotificationActionReceiver`에 `@Inject lateinit var runLogDao: EngineRunLogDao`를 더하고(import `com.recordofp.app.data.db.EngineRunLogDao`, `com.recordofp.app.platform.logReceiverError`), `catch (e: Exception)` 블록을 바꾼다.

```kotlin
            } catch (e: Exception) {
                // 코루틴 밖으로 나간 예외는 프로세스를 죽인다. 알림은 남겨 두어 다시 누를 수 있게 한다 (최종 리뷰 I1)
                runLogDao.logReceiverError(clock, LOG_CAUSE, e)
            } finally {
```

companion object에 `private const val LOG_CAUSE = "NOTIFICATION_ACTION"`을 더하고, 쓰이지 않게 된 `android.util.Log` import를 지운다.

- [ ] **Step 7: 진단 화면에서 ERROR를 빨강으로 보인다**

`resultDotColor`의 빨강 조건에 `|| result == "ERROR"`를 더한다.

```kotlin
    result.startsWith("BLOCK") || result.contains("FAILED") || result.contains("NO_PERMISSION") || result == "ERROR" ->
        MaterialTheme.colorScheme.error
```

- [ ] **Step 8: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.FenceEventFlowTest" --tests "*.ReceiverErrorLogTest" --tests "*.ReseedPlannerTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → 전부 통과, lint 오류 0

- [ ] **Step 9: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/platform/ReceiverErrorLog.kt \
  android/app/src/main/java/com/recordofp/app/platform/geofence/FenceEventFlow.kt \
  android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceBroadcastReceiver.kt \
  android/app/src/main/java/com/recordofp/app/platform/notify/NotificationActionReceiver.kt \
  android/app/src/main/java/com/recordofp/app/domain/engine/FencePlan.kt \
  android/app/src/main/java/com/recordofp/app/domain/engine/ReseedPlanner.kt \
  android/app/src/main/java/com/recordofp/app/ui/settings/DiagnosticsScreen.kt \
  android/app/src/test/java/com/recordofp/app/platform/geofence/FenceEventFlowTest.kt \
  android/app/src/test/java/com/recordofp/app/platform/ReceiverErrorLogTest.kt
git commit -m "fix: 지오펜스 리시버 무중단 - 센티널 우선 예약과 묶음별 예외 격리" -m "알림 하나가 실패해도 나머지 알림과 센티널 재배치가 살아남고, 리시버 오류는 진단 로그에 남는다.
처리 순서는 runFenceEvent로 JVM 테스트한다. (§4.4, §6.2, §6.5, 최종 리뷰 I1)"
```

---

### Task 11: 여러 항목 알림 — 전체 목록과 묶음 [오늘 그만]

원격은 묶음 알림에 "첫 항목 외 N건"만 보여 주고 액션을 하나도 달지 않는다. 설계 §6.5 4단계는 "항목 나열"이다. 이 태스크는 여러 항목 알림에 InboxStyle로 전체 제목을 보여 주고, 묶음 전체를 억제하는 [오늘 그만]을 단다. [완료]는 어느 항목인지 모호하므로 묶음에는 두지 않는다(탭하면 앱이 열린다). 액션 리시버는 항목 id 배열을 받는다. 액션 결정은 작은 순수 함수로 빼서 테스트한다.

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/platform/notify/AlertActions.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/notify/NearbyNotifier.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/notify/NotificationActionReceiver.kt`
- Test: `android/app/src/test/java/com/recordofp/app/platform/notify/AlertActionsTest.kt` (생성)

**Interfaces:**
- Consumes: Task 7의 `AlertGroup.fenceId`, Task 10의 `NotificationActionReceiver` 오류 기록
- Produces: `internal enum class AlertAction { COMPLETE, MUTE_TODAY }`, `internal fun alertActionsFor(group: AlertGroup): List<AlertAction>`, `NotificationActionReceiver.pendingIntent(context, action, reminderIds: List<Long>, notificationId: Int)`, extra `EXTRA_REMINDER_IDS = "reminder_ids"`

- [ ] **Step 1: 실패 테스트를 쓴다**

`AlertActionsTest.kt`:

```kotlin
package com.recordofp.app.platform.notify

import com.recordofp.app.data.engine.AlertGroup
import com.recordofp.app.domain.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertActionsTest {

    private fun group(vararg ids: Long) = AlertGroup(
        fenceId = "poi:1", poiId = "1", poiName = "CU",
        reminders = ids.map { Reminder(id = it, title = "할일$it", createdAt = 0, updatedAt = 0) },
    )

    @Test
    fun `항목이 하나면 완료와 오늘 그만을 둘 다 단다`() {
        assertEquals(listOf(AlertAction.COMPLETE, AlertAction.MUTE_TODAY), alertActionsFor(group(1)))
    }

    @Test
    fun `여러 항목 묶음에는 어느 항목인지 모호한 완료를 빼고 오늘 그만만 단다`() {
        assertEquals(listOf(AlertAction.MUTE_TODAY), alertActionsFor(group(1, 2)))
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.AlertActionsTest"`
Expected: 컴파일 실패 — `AlertAction`, `alertActionsFor`가 없다.

- [ ] **Step 3: 액션 결정 함수를 만든다**

`AlertActions.kt`:

```kotlin
package com.recordofp.app.platform.notify

import com.recordofp.app.data.engine.AlertGroup

/** 근처 알림 액션 (스펙 §4.1 [완료] [오늘 그만]) */
internal enum class AlertAction { COMPLETE, MUTE_TODAY }

/** 여러 항목 묶음에는 [완료]를 두지 않는다 — 어느 항목인지 모호하다. [오늘 그만]은 묶음 전체를 억제한다 */
internal fun alertActionsFor(group: AlertGroup): List<AlertAction> =
    if (group.reminders.size == 1) listOf(AlertAction.COMPLETE, AlertAction.MUTE_TODAY) else listOf(AlertAction.MUTE_TODAY)
```

- [ ] **Step 4: 액션 리시버가 id 배열을 받게 한다**

`NotificationActionReceiver.onReceive` 앞부분과 `when`을 바꾼다.

```kotlin
    override fun onReceive(context: Context, intent: Intent) {
        val reminderIds = intent.getLongArrayExtra(EXTRA_REMINDER_IDS)
        if (reminderIds == null || reminderIds.isEmpty()) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_COMPLETE -> reminderIds.forEach { repository.complete(it) }
                    ACTION_MUTE_TODAY -> {
                        val release = MuteToday.releaseInstant(clock.instant(), zone).toEpochMilli()
                        reminderIds.forEach { repository.muteUntil(it, release) }
                    }
                }
                NotificationManagerCompat.from(context).cancel(notificationId)
```

(그 아래 `catch`/`finally`는 Task 10 그대로 둔다.)

companion object의 extra와 `pendingIntent`를 바꾼다.

```kotlin
        const val EXTRA_REMINDER_IDS = "reminder_ids"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pendingIntent(context: Context, action: String, reminderIds: List<Long>, notificationId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                // 알림·액션마다 다른 requestCode — 다른 알림의 액션 extra를 덮어쓰지 않는다
                (action + notificationId).hashCode(),
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_REMINDER_IDS, reminderIds.toLongArray())
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
```

`EXTRA_REMINDER_ID` 상수는 지운다(MainActivity 딥링크의 `"reminder_id"` extra는 별개이므로 건드리지 않는다).

- [ ] **Step 5: 묶음 알림에 목록과 액션을 단다**

`NearbyNotifier.show`의 `if (group.reminders.size == 1) { builder.addAction(...).addAction(...) }` 블록을 아래로 바꾼다.

```kotlin
        if (group.reminders.size > 1) {
            // 펼치면 전체 항목 목록 (§6.5 4단계 "항목 나열")
            val inbox = NotificationCompat.InboxStyle()
            group.reminders.forEach { inbox.addLine(it.title) }
            builder.setStyle(inbox)
        }
        val ids = group.reminders.map { it.id }
        alertActionsFor(group).forEach { action ->
            val (labelRes, intentAction) = when (action) {
                AlertAction.COMPLETE -> R.string.action_complete to NotificationActionReceiver.ACTION_COMPLETE
                AlertAction.MUTE_TODAY -> R.string.action_mute_today to NotificationActionReceiver.ACTION_MUTE_TODAY
            }
            builder.addAction(
                0, context.getString(labelRes),
                NotificationActionReceiver.pendingIntent(context, intentAction, ids, notificationId),
            )
        }
```

- [ ] **Step 6: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.AlertActionsTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → 전부 통과, lint 오류 0

- [ ] **Step 7: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/platform/notify/AlertActions.kt \
  android/app/src/main/java/com/recordofp/app/platform/notify/NearbyNotifier.kt \
  android/app/src/main/java/com/recordofp/app/platform/notify/NotificationActionReceiver.kt \
  android/app/src/test/java/com/recordofp/app/platform/notify/AlertActionsTest.kt
git commit -m "feat: 여러 항목 근처 알림에 전체 목록과 묶음 [오늘 그만]" -m "묶음 알림을 펼치면 모든 항목이 보이고 [오늘 그만]이 묶음 전체를 억제한다. [완료]는 단건에만 단다. (§4.1, §6.5)"
```

---

## 묶음 D — UI·사용성

### Task 12: 에디터 저장 가드 — 입력 중 브랜드 보존·이중 저장 방지·실패 안내

원격 에디터에는 세 가지 문제가 있다. (a) 브랜드 입력란이 화면의 `remember`라서, 입력만 하고 [+]를 누르지 않은 채 저장하면 그 브랜드가 조용히 버려진다. 그 브랜드가 유일한 트리거면 저장 버튼이 꺼진 채로 남는다. (b) 저장을 연타하면 두 번 저장된다. 편집 모드의 upsert는 트랜잭션이 아니어서(`deleteByReminder` → `upsertAll`) 트리거가 중복될 수 있다. 삭제도 마찬가지다. (c) 저장이 실패하면 앱이 죽는다. 이 태스크는 입력 중 브랜드를 상태에 두고 저장할 때 함께 확정한다. 저장·삭제 중에는 다시 누를 수 없게 하고, 실패하면 문구로 알린다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/ui/editor/EditorViewModel.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/editor/EditorScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`
- Test: `android/app/src/test/java/com/recordofp/app/ui/editor/EditorViewModelTest.kt`

**Interfaces:**
- Consumes: 원격 `EditorViewModel`(편집 모드·`searchJob` 포함)
- Produces: `EditorUiState.brandInput`, `saving`, `saveFailed`, `fun withBrandInputCommitted(): EditorUiState`, `EditorViewModel.onBrandInputChange(v)`, `commitBrandInput()`. `addBrand(keyword)`는 지운다.

- [ ] **Step 1: 페이크와 기존 테스트를 고친다**

`EditorViewModelTest.FakeRepo`를 바꾼다.

```kotlin
    private class FakeRepo : ReminderRepository {
        var saved: Reminder? = null
        var upsertCount = 0
        var upsertError: Exception? = null
        var deletedId: Long? = null
        var deleteCount = 0
        var byIdResult: Reminder? = null
        override fun observeActive(): Flow<List<Reminder>> = emptyFlow()
        override suspend fun upsert(reminder: Reminder): Long {
            upsertCount++
            upsertError?.let { throw it }
            saved = reminder
            return 1
        }
        override suspend fun complete(id: Long) {}
        override suspend fun muteUntil(id: Long, untilEpochMs: Long) {}
        override suspend fun delete(id: Long) { deleteCount++; deletedId = id }
        override suspend fun activeTriggers() = emptyList<com.recordofp.app.domain.model.TriggerSpec>()
        override suspend fun byId(id: Long): Reminder? = byIdResult
    }
```

`저장하면 선택이 트리거 스펙으로 매핑된다`의 `vm.addBrand(" GS25 ")`를 `vm.onBrandInputChange(" GS25 "); vm.commitBrandInput()`로 바꾼다.

- [ ] **Step 2: 새 실패 테스트를 쓴다**

```kotlin
    @Test
    fun `입력만 하고 확정하지 않은 브랜드도 저장된다`() = runTest {
        val vm = vm()
        vm.onTitleChange("휴지")
        vm.onBrandInputChange(" 이마트24 ")
        assertTrue(vm.state.value.canSave) // 입력 중인 브랜드도 트리거로 센다
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("이마트24"), repo.saved!!.triggers.map { it.brandKeyword })
    }

    @Test
    fun `저장 중에 다시 눌러도 한 번만 저장된다`() = runTest {
        val vm = vm()
        vm.onTitleChange("휴지")
        vm.toggleCategory("convenience")
        vm.save()
        vm.save() // 연타
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.upsertCount)
    }

    @Test
    fun `저장이 실패하면 앱을 죽이지 않고 오류를 표시하며 다시 저장할 수 있다`() = runTest {
        repo.upsertError = IllegalStateException("disk full")
        val vm = vm()
        vm.onTitleChange("휴지")
        vm.toggleCategory("convenience")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.saveFailed)
        assertTrue(!s.saved)
        assertTrue(s.canSave)
    }

    @Test
    fun `삭제 중에 다시 눌러도 한 번만 삭제된다`() = runTest {
        repo.byIdResult = Reminder(id = 7, title = "건전지 사기", createdAt = 100, updatedAt = 200)
        val vm = vm(reminderId = 7)
        dispatcher.scheduler.advanceUntilIdle()
        vm.delete()
        vm.delete()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.deleteCount)
    }
```

- [ ] **Step 3: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.EditorViewModelTest"`
Expected: 컴파일 실패 — `onBrandInputChange`, `commitBrandInput`, `saveFailed`가 없다.

- [ ] **Step 4: 상태와 뷰모델을 고친다**

`EditorUiState`에 필드와 함수를 더하고 `canSave`를 바꾼다.

```kotlin
data class EditorUiState(
    val title: String = "",
    val memo: String = "",
    val selectedCategoryIds: Set<String> = emptySet(),
    val brandKeywords: List<String> = emptyList(),
    /** 입력 중인(아직 칩으로 확정하지 않은) 브랜드 — 저장 시 함께 확정된다. 회전에도 남도록 상태에 둔다 (최종 리뷰 I6) */
    val brandInput: String = "",
    val place: PickedPlace? = null,
    val placeQuery: String = "",
    val placeResults: List<PoiCandidate> = emptyList(),
    val placeSearchError: PlaceSearchError? = null,
    /** 저장·삭제 진행 중 — 연타로 두 번 처리되지 않게 한다 (최종 리뷰 I6) */
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
    /** null이면 새 기록, 값이 있으면 편집 중인 기존 기록의 id (§3.1 CRUD 갭) */
    val editingId: Long? = null,
) {
    val canSave: Boolean
        get() = !saving && title.isNotBlank() &&
            (selectedCategoryIds.isNotEmpty() || brandKeywords.isNotEmpty() || brandInput.isNotBlank() || place != null)

    /** 입력 중인 브랜드를 칩으로 확정한다 (앞뒤 공백 제거, 중복 무시) */
    fun withBrandInputCommitted(): EditorUiState {
        val k = brandInput.trim()
        if (k.isEmpty()) return copy(brandInput = "")
        return copy(brandKeywords = if (k in brandKeywords) brandKeywords else brandKeywords + k, brandInput = "")
    }
}
```

`addBrand(keyword)`를 지우고 그 자리에 넣는다.

```kotlin
    fun onBrandInputChange(v: String) = _state.update { it.copy(brandInput = v) }
    fun commitBrandInput() = _state.update { it.withBrandInputCommitted() }
```

`save()`와 `delete()`를 교체한다.

```kotlin
    fun save() {
        // 입력만 하고 확정하지 않은 브랜드도 저장한다 — 조용히 버리지 않는다 (최종 리뷰 I6)
        val s = _state.value.withBrandInputCommitted()
        if (!s.canSave) return // 저장·삭제 중이면 canSave가 false — 연타 무시
        _state.value = s.copy(saving = true, saveFailed = false)
        val triggers = buildList {
            s.selectedCategoryIds.forEach { add(TriggerSpec(type = TriggerType.CATEGORY, categoryId = it)) }
            s.brandKeywords.forEach { add(TriggerSpec(type = TriggerType.BRAND, brandKeyword = it)) }
            s.place?.let {
                add(
                    TriggerSpec(
                        type = TriggerType.PLACE, placeName = it.name,
                        placeKakaoId = it.kakaoId, placePoint = it.point,
                    ),
                )
            }
        }
        viewModelScope.launch {
            try {
                repository.upsert(
                    Reminder(
                        id = s.editingId ?: 0L,
                        title = s.title.trim(),
                        memo = s.memo.trim().ifEmpty { null },
                        createdAt = createdAt,
                        updatedAt = 0,
                        triggers = triggers,
                    ),
                )
                // saving은 그대로 둔다 — 화면이 닫히기 전까지 다시 눌리지 않게
                _state.update { it.copy(saved = true) }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w(TAG, "기록 저장 실패", e)
                // 앱을 죽이지 않고 알린다. 입력은 그대로라 다시 저장할 수 있다 (최종 리뷰 I6)
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    /** 편집 모드에서 기록 삭제. 저장과 동일하게 saved 플래그를 재사용해 화면을 닫는다 (§3.1 CRUD 갭) */
    fun delete() {
        val s = _state.value
        val id = s.editingId ?: return
        if (s.saving) return // 연타 무시
        _state.value = s.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                repository.delete(id)
                _state.update { it.copy(saved = true) }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w(TAG, "기록 삭제 실패", e)
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }
```

(`android.util.Log`는 JVM 테스트에서 `unitTests.isReturnDefaultValues = true` 덕분에 기본값을 돌려준다.)

- [ ] **Step 5: 화면을 고친다**

`EditorScreen`에서 `var brandInput by remember { mutableStateOf("") }`를 지운다. 브랜드 입력 줄을 바꾼다.

```kotlin
                EditorField(
                    value = state.brandInput,
                    onValueChange = viewModel::onBrandInputChange,
                    placeholder = stringResource(R.string.editor_brand_example),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::commitBrandInput) {
```

본문 `Column`의 첫 자식(`EditorField(value = state.title, ...)` 앞)에 오류 문구를 넣는다.

```kotlin
            if (state.saveFailed) {
                // 저장·삭제 실패 — 입력은 그대로 남아 있으니 다시 시도하면 된다 (최종 리뷰 I6)
                Text(
                    stringResource(R.string.editor_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
```

하단 삭제 `TextButton`에 `enabled = !state.saving,`을 더한다.

- [ ] **Step 6: 문자열을 더한다**

ko: `<string name="editor_save_failed">변경 내용을 저장하지 못했어요. 다시 시도해 주세요.</string>`
en: `<string name="editor_save_failed">Couldn\'t save your changes. Please try again.</string>`

- [ ] **Step 7: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.EditorViewModelTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → 전부 통과, lint 오류 0

- [ ] **Step 8: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/ui/editor/EditorViewModel.kt \
  android/app/src/main/java/com/recordofp/app/ui/editor/EditorScreen.kt \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml \
  android/app/src/test/java/com/recordofp/app/ui/editor/EditorViewModelTest.kt
git commit -m "fix: 에디터 저장 가드 - 입력 중 브랜드 보존·연타 방지·실패 안내" -m "확정하지 않은 브랜드도 저장되고, 저장·삭제를 연타해도 한 번만 처리되며, 실패하면 앱이 죽지 않고 문구로 알린다. (§4.1, 최종 리뷰 I6)"
```

---

### Task 13: 홈 보호 배너 — 꺼진 것을 이름으로 알리고 고칠 곳으로

원격 배너는 "알림이 꺼질 수 있는 상태예요 / 설정에서 확인" 한 줄이고, 누르면 앱 안 설정으로 간다. 무엇이 꺼졌는지, '항상 허용'이 왜 필요한지, 어떻게 켜는지 알려 주지 않는다(§4.2 4단계 업셀, §4.3 "왜 알림이 안 오지?"). 이 태스크는 꺼진 것 하나를 우선순위대로 골라 이름으로 알린다(알림 → 정확한 위치 → 항상 허용 → 기기 위치). 버튼은 그것을 고칠 시스템 화면을 바로 연다. '항상 허용'에는 단계 안내를 덧붙인다. 원격의 앰버 톤과 F1 보고(`reportProtection`)는 그대로 둔다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/ui/permissions/PermissionStatus.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/home/HomeScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`
- Test: `android/app/src/test/java/com/recordofp/app/ui/permissions/PermissionSnapshotTest.kt`

**Interfaces:**
- Consumes: Task 6의 `PermissionSnapshot.locationServicesOn`, `appNotificationSettingsIntent`, `locationSourceSettingsIntent`, `appDetailsSettingsIntent`, `Context.openSettings`
- Produces: `enum class ProtectionIssue { NOTIFICATIONS_OFF, PRECISE_LOCATION_OFF, BACKGROUND_LOCATION_OFF, LOCATION_SERVICES_OFF }`, `val PermissionSnapshot.topIssue: ProtectionIssue?`

- [ ] **Step 1: 실패 테스트를 쓴다**

`PermissionSnapshotTest`에 추가한다(import `org.junit.Assert.assertEquals`, `org.junit.Assert.assertNull`).

```kotlin
    @Test
    fun `모두 켜져 있으면 배너로 안내할 것이 없다`() {
        assertNull(allOn.topIssue)
    }

    @Test
    fun `꺼진 것이 여럿이면 알림 → 정확한 위치 → 항상 허용 → 기기 위치 순으로 하나만 안내한다`() {
        val allOff = PermissionSnapshot(
            notifications = false, fineLocation = false, backgroundLocation = false,
            batteryUnrestricted = false, locationServicesOn = false,
        )
        assertEquals(ProtectionIssue.NOTIFICATIONS_OFF, allOff.topIssue)
        assertEquals(ProtectionIssue.PRECISE_LOCATION_OFF, allOff.copy(notifications = true).topIssue)
        assertEquals(
            ProtectionIssue.BACKGROUND_LOCATION_OFF,
            allOff.copy(notifications = true, fineLocation = true).topIssue,
        )
        assertEquals(
            ProtectionIssue.LOCATION_SERVICES_OFF,
            allOff.copy(notifications = true, fineLocation = true, backgroundLocation = true).topIssue,
        )
    }

    @Test
    fun `사용 중에만 허용이면 항상 허용 업셀 카드를 보여준다`() {
        // Android 11+ 온보딩은 "사용 중에만"까지만 얻는다 — 워커는 이 상태에서 펜스를 전부 걷는다 (§4.2 4단계)
        assertEquals(ProtectionIssue.BACKGROUND_LOCATION_OFF, allOn.copy(backgroundLocation = false).topIssue)
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.PermissionSnapshotTest"`
Expected: 컴파일 실패 — `topIssue`, `ProtectionIssue`가 없다.

- [ ] **Step 3: 우선순위 판정을 넣는다**

`PermissionSnapshot`의 `fullyProtected` 아래에 추가한다.

```kotlin
    /** 홈 배너에 보여줄 한 가지 — 우선순위: 알림 → 정확한 위치 → 항상 허용 → 기기 위치 (§4.2·§4.3, 최종 리뷰 I3) */
    val topIssue: ProtectionIssue?
        get() = when {
            !notifications -> ProtectionIssue.NOTIFICATIONS_OFF
            !fineLocation -> ProtectionIssue.PRECISE_LOCATION_OFF
            !backgroundLocation -> ProtectionIssue.BACKGROUND_LOCATION_OFF
            !locationServicesOn -> ProtectionIssue.LOCATION_SERVICES_OFF
            else -> null
        }
```

클래스 바로 아래에 추가한다.

```kotlin
/** 근처 알림을 막고 있는 것 (§4.3 "왜 알림이 안 오지?"의 답) */
enum class ProtectionIssue { NOTIFICATIONS_OFF, PRECISE_LOCATION_OFF, BACKGROUND_LOCATION_OFF, LOCATION_SERVICES_OFF }
```

- [ ] **Step 4: 홈 배너를 바꾼다**

`HomeScreen`의 `if (!snapshot.fullyProtected) { Card(onClick = onSettingsClick, ...) { ... } }` 블록 전체를 `snapshot.topIssue?.let { issue -> ProtectionBanner(issue) }`로 바꾼다. `LifecycleResumeEffect` 안의 `viewModel.reportProtection(snapshot.fullyProtected)`는 그대로 둔다. 파일에 컴포저블을 더한다.

```kotlin
/**
 * 근처 알림을 막고 있는 것 하나를 이름으로 알리고 고칠 곳으로 바로 보낸다 (§4.2 4단계 업셀, §4.3, 최종 리뷰 I3).
 * 여럿이면 우선순위가 높은 하나만 — 고치고 돌아오면(onResume) 다음 것이 보인다. 앰버 톤: 경고이지 오류가 아니다 (개편안 §2).
 */
@Composable
private fun ProtectionBanner(issue: ProtectionIssue) {
    val context = LocalContext.current
    val (titleRes, bodyRes) = when (issue) {
        ProtectionIssue.NOTIFICATIONS_OFF -> R.string.banner_notifications_title to R.string.banner_notifications_body
        ProtectionIssue.PRECISE_LOCATION_OFF -> R.string.banner_precise_title to R.string.banner_precise_body
        ProtectionIssue.BACKGROUND_LOCATION_OFF -> R.string.banner_background_title to R.string.banner_background_body
        ProtectionIssue.LOCATION_SERVICES_OFF -> R.string.banner_location_off_title to R.string.banner_location_off_body
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(bodyRes), style = MaterialTheme.typography.bodySmall)
            if (issue == ProtectionIssue.BACKGROUND_LOCATION_OFF) {
                // A11+ "항상 허용"은 시스템 설정에서만 켤 수 있다 — 단계를 적어 준다 (§4.2)
                Text(stringResource(R.string.banner_background_steps), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                onClick = {
                    context.openSettings(
                        when (issue) {
                            ProtectionIssue.NOTIFICATIONS_OFF -> appNotificationSettingsIntent(context)
                            ProtectionIssue.PRECISE_LOCATION_OFF,
                            ProtectionIssue.BACKGROUND_LOCATION_OFF,
                            -> appDetailsSettingsIntent(context)
                            ProtectionIssue.LOCATION_SERVICES_OFF -> locationSourceSettingsIntent()
                        },
                    )
                },
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
            ) { Text(stringResource(R.string.banner_action_open_settings)) }
        }
    }
}
```

import에 `androidx.compose.foundation.layout.heightIn`, `androidx.compose.material3.ButtonDefaults`, `androidx.compose.material3.TextButton`, `com.recordofp.app.ui.permissions.ProtectionIssue`, `com.recordofp.app.ui.permissions.appDetailsSettingsIntent`, `com.recordofp.app.ui.permissions.appNotificationSettingsIntent`, `com.recordofp.app.ui.permissions.locationSourceSettingsIntent`, `com.recordofp.app.ui.permissions.openSettings`를 더한다.

- [ ] **Step 5: 문자열을 바꾼다**

ko·en 양쪽에서 `banner_protection_title`, `banner_protection_action`을 지우고 아래를 더한다.

ko:

```xml
    <string name="banner_notifications_title">알림이 꺼져 있어요</string>
    <string name="banner_notifications_body">근처를 지날 때 알려드리려면 \'근처 알림\'을 포함해 알림을 켜 주세요.</string>
    <string name="banner_precise_title">정확한 위치 권한이 필요해요</string>
    <string name="banner_precise_body">근처 가게를 찾으려면 위치 권한을 허용하고 \'정확한 위치 사용\'을 켜 주세요.</string>
    <string name="banner_background_title">앱을 닫아도 알려드리려면</string>
    <string name="banner_background_body">근처를 지날 때 알려드리려면 위치 권한을 \'항상 허용\'으로 바꿔 주세요.</string>
    <string name="banner_background_steps">설정 → 권한 → 위치 → 항상 허용</string>
    <string name="banner_location_off_title">기기 위치가 꺼져 있어요</string>
    <string name="banner_location_off_body">기기의 위치를 켜야 근처 알림이 동작해요.</string>
    <string name="banner_action_open_settings">설정 열기</string>
```

en:

```xml
    <string name="banner_notifications_title">Notifications are off</string>
    <string name="banner_notifications_body">Turn on notifications, including \'Nearby alerts\', so we can remind you as you pass by.</string>
    <string name="banner_precise_title">Precise location is needed</string>
    <string name="banner_precise_body">To find stores near you, allow location access and turn on \'Use precise location\'.</string>
    <string name="banner_background_title">Get reminders even when the app is closed</string>
    <string name="banner_background_body">To remind you as you pass by, change location access to \'Allow all the time\'.</string>
    <string name="banner_background_steps">Settings → Permissions → Location → Allow all the time</string>
    <string name="banner_location_off_title">Location is turned off</string>
    <string name="banner_location_off_body">Turn on your device\'s location so nearby alerts can work.</string>
    <string name="banner_action_open_settings">Open settings</string>
```

- [ ] **Step 6: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.PermissionSnapshotTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → 전부 통과, lint 오류 0

- [ ] **Step 7: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/ui/permissions/PermissionStatus.kt \
  android/app/src/main/java/com/recordofp/app/ui/home/HomeScreen.kt \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml \
  android/app/src/test/java/com/recordofp/app/ui/permissions/PermissionSnapshotTest.kt
git commit -m "feat: 홈 보호 배너를 구체적으로 - 항상 허용 업셀과 설정 바로 열기" -m "꺼진 것 하나를 우선순위대로 이름으로 알리고 고칠 시스템 화면을 바로 연다. '항상 허용'에는 단계 안내를 단다. (§4.2, §4.3, 최종 리뷰 I3)"
```

---

### Task 14: 완료 실행 취소와 TalkBack 완료

완료를 되돌릴 방법이 없다. 스와이프는 양방향 모두 완료로 처리되어 실수하기 쉽다. 스와이프를 못 하는 TalkBack 사용자는 완료할 수 없다(§8). 이 태스크는 완료한 뒤 [실행 취소] 스낵바를 띄우고, 스와이프는 한 방향만 허용하고, 카드에 TalkBack 사용자 지정 동작 "완료"를 단다. 로컬 구현에서 나온 N1 결함(실행 취소 뒤 행이 스와이프된 상태로 남고, 완료가 두 번 불림)을 막기 위해 두 가지를 함께 넣는다. 행 키를 `id:updatedAt`으로 해서 되살아난 행이 새 스와이프 상태를 받게 하고, 완료는 확정된 상태 변화(`LaunchedEffect`)에서 한 번만 부른다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/data/repo/ReminderRepository.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/home/HomeViewModel.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/home/HomeScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`
- Test: `RoomReminderRepositoryTest.kt`, `HomeViewModelTest.kt`, 페이크 4곳(`ReseedServiceTest`, `EditorViewModelTest`, `NearbyViewModelTest`, `HomeViewModelTest`)

**Interfaces:**
- Consumes: Task 13의 `HomeScreen`(배너)
- Produces: `suspend fun ReminderRepository.reactivate(id: Long)`, `HomeViewModel.reactivate(id)`

- [ ] **Step 1: 실패 테스트를 쓴다**

`RoomReminderRepositoryTest.FakeDao`의 `setStatus`가 완료 시각도 기록하게 한다.

```kotlin
    val completedAts = mutableListOf<Long?>()
    override suspend fun setStatus(id: Long, status: String, completedAt: Long?, updatedAt: Long) {
        statusCalls += id to status
        completedAts += completedAt
    }
```

테스트를 추가한다.

```kotlin
    @Test
    fun `reactivate는 ACTIVE로 되돌리고 완료 시각을 지운 뒤 재배치를 요청한다`() = runTest {
        repo.reactivate(5)
        assertEquals(listOf(5L to "ACTIVE"), dao.statusCalls)
        assertEquals(listOf<Long?>(null), dao.completedAts)
        assertEquals(1, requester.count) // 다시 활성 — 펜스가 돌아와야 한다
    }
```

`HomeViewModelTest.FakeRepo`에 `val reactivated = mutableListOf<Long>()`와 `override suspend fun reactivate(id: Long) { reactivated += id }`를 더하고 테스트를 추가한다.

```kotlin
    @Test
    fun `완료 실행 취소는 저장소의 reactivate에 위임한다`() = runTest {
        val repo = FakeRepo()
        val vm = HomeViewModel(repo, noopTrigger())
        vm.complete(7)
        vm.reactivate(7)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(7L), repo.completed)
        assertEquals(listOf(7L), repo.reactivated)
    }
```

나머지 페이크 3곳(`ReseedServiceTest.FakeReminders`, `EditorViewModelTest.FakeRepo`, `NearbyViewModelTest.FakeRepo`)에 `override suspend fun reactivate(id: Long) {}`를 더한다.

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.RoomReminderRepositoryTest" --tests "*.HomeViewModelTest"`
Expected: 컴파일 실패 — `reactivate`가 인터페이스에 없다.

- [ ] **Step 3: 저장소와 뷰모델을 구현한다**

`ReminderRepository` 인터페이스의 `complete` 다음 줄에 추가한다.

```kotlin
    /** 완료 실행 취소 — 다시 활성으로 (§4.1 처리, 최종 리뷰 I5) */
    suspend fun reactivate(id: Long)
```

`RoomReminderRepository`의 `complete` 다음에 추가한다.

```kotlin
    override suspend fun reactivate(id: Long) {
        reminderDao.setStatus(id, ReminderStatus.ACTIVE.name, completedAt = null, updatedAt = clock.millis())
        reseedRequester.requestItemChange() // 다시 활성 — 펜스가 돌아와야 한다
    }
```

`HomeViewModel`의 `complete` 다음에 추가한다.

```kotlin
    /** 완료 스낵바의 [실행 취소] (최종 리뷰 I5) */
    fun reactivate(id: Long) = viewModelScope.launch { repository.reactivate(id) }
```

- [ ] **Step 4: 홈 화면에 실행 취소와 TalkBack 동작을 넣는다**

`HomeScreen` 함수 첫머리(`val items by ...` 다음)에 추가한다.

```kotlin
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val completedMessage = stringResource(R.string.home_completed)
    val undoLabel = stringResource(R.string.action_undo)
    // 완료는 되돌릴 수 있어야 한다 — 스와이프·TalkBack 완료 뒤 [실행 취소] 스낵바 (§4.1 처리, 최종 리뷰 I5)
    val completeWithUndo: (Long) -> Unit = { id ->
        viewModel.complete(id)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss() // 연속 완료면 마지막 것만
            val result = snackbarHostState.showSnackbar(
                message = completedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.reactivate(id)
        }
    }
```

`Scaffold(...)`에 `snackbarHost = { SnackbarHost(snackbarHostState) },`를 더한다. `LazyColumn`의 `items`를 바꾼다.

```kotlin
                    // 키에 updatedAt을 넣는다 — 실행 취소로 되살아난 행이 스와이프된 옛 상태를 물려받지 않게 (N1)
                    items(items, key = { "${it.id}:${it.updatedAt}" }) { item ->
                        ReminderRow(
                            item = item,
                            onComplete = { completeWithUndo(item.id) },
                            onClick = { onItemClick(item.id) },
                            modifier = Modifier.animateItem(), // 등장/제거/재배열 애니메이션 (개편안 §2)
                        )
                    }
```

`ReminderRow`를 교체한다.

```kotlin
@Composable
private fun ReminderRow(
    item: Reminder,
    onComplete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 부수효과는 확정된 상태 변화에서 한 번만 — confirmValueChange는 같은 스와이프에 여러 번 불릴 수 있다 (N1)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it == SwipeToDismissBoxValue.StartToEnd },
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) onComplete()
    }
    val completeLabel = stringResource(R.string.action_complete)
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        // 시작→끝 방향만 — 실행 취소 스낵바와 짝을 이루는 한 가지 제스처 (최종 리뷰 I5)
        enableDismissFromEndToStart = false,
        backgroundContent = {
            // 완료 스와이프: 초록 배경 + 체크 (개편안 §2)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(successColor())
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
                    Text(
                        stringResource(R.string.home_completed),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        },
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                // 스와이프를 못 하는 TalkBack 사용자도 완료할 수 있게 사용자 지정 동작을 단다 (§8, 최종 리뷰 I7)
                .semantics {
                    customActions = listOf(CustomAccessibilityAction(completeLabel) { onComplete(); true })
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                if (item.triggers.isNotEmpty()) {
                    TriggerChips(item.triggers)
                }
            }
        }
    }
}
```

import에 `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.rememberCoroutineScope`, `androidx.compose.material3.SnackbarDuration`, `androidx.compose.material3.SnackbarHost`, `androidx.compose.material3.SnackbarHostState`, `androidx.compose.material3.SnackbarResult`, `androidx.compose.ui.semantics.CustomAccessibilityAction`, `androidx.compose.ui.semantics.customActions`, `androidx.compose.ui.semantics.semantics`, `kotlinx.coroutines.launch`를 더한다.

- [ ] **Step 5: 문자열을 더한다**

ko: `<string name="action_undo">실행 취소</string>`
en: `<string name="action_undo">Undo</string>`

- [ ] **Step 6: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.RoomReminderRepositoryTest" --tests "*.HomeViewModelTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → 전부 통과, lint 오류 0

- [ ] **Step 7: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/data/repo/ReminderRepository.kt \
  android/app/src/main/java/com/recordofp/app/ui/home/HomeViewModel.kt \
  android/app/src/main/java/com/recordofp/app/ui/home/HomeScreen.kt \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml \
  android/app/src/test/java/com/recordofp/app/data/repo/RoomReminderRepositoryTest.kt \
  android/app/src/test/java/com/recordofp/app/ui/home/HomeViewModelTest.kt \
  android/app/src/test/java/com/recordofp/app/ui/editor/EditorViewModelTest.kt \
  android/app/src/test/java/com/recordofp/app/ui/nearby/NearbyViewModelTest.kt \
  android/app/src/test/java/com/recordofp/app/data/engine/ReseedServiceTest.kt
git commit -m "feat: 완료 실행 취소 스낵바와 TalkBack 완료 동작" -m "완료를 되돌릴 수 있고, 스와이프는 한 방향만 받으며, TalkBack 사용자도 카드 동작으로 완료한다.
행 키에 updatedAt을 넣고 완료를 확정 상태에서 한 번만 불러 실행 취소 뒤 행이 끼지 않는다. (§4.1, §8, 최종 리뷰 I5·I7)"
```

---

### Task 15: UI 마감 — 뒤로 버튼 설명, 키보드 가림, 뒤로 연타 가드, 정책 기본값 단일화

이 태스크는 작은 결함 네 가지를 묶어 고친다. (1) 주변 보기·진단·설정의 뒤로 아이콘에 설명이 없어 TalkBack이 "라벨 없는 버튼"으로 읽는다. (2) edge-to-edge에서 에디터 아래쪽의 장소 검색란과 결과가 키보드에 가려질 수 있다. (3) 뒤로나 ✕를 연타하거나 저장 직후 ✕를 누르면 홈까지 꺼내져 빈 화면이 될 수 있다. (4) 알림 정책 기본값(4/10/22:00/08:00)이 `SettingsStore`에 세 번 리터럴로 중복돼 있어, `EngineParams`를 튜닝해도 앱에 반영되지 않는다(Global Constraint 위반).

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/ui/common/BackButton.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/nearby/NearbyScreen.kt`, `ui/settings/DiagnosticsScreen.kt`, `ui/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/editor/EditorScreen.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/AppNavHost.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/data/repo/SettingsStore.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`
- Test: `android/app/src/test/java/com/recordofp/app/data/repo/NotificationPolicySettingsTest.kt` (생성)

**Interfaces:**
- Consumes: Task 12의 `EditorScreen`
- Produces: `@Composable fun BackButton(onClick: () -> Unit)`, `private fun NavController.popFrom(route: String)`

- [ ] **Step 1: 기본값 회귀 테스트를 쓴다**

`NotificationPolicySettingsTest.kt`를 만든다. 지금 값이 이미 `EngineParams`와 같으므로 **이 테스트는 처음부터 통과한다.** 목적은 리터럴을 지운 뒤에도 기본값이 그대로라는 회귀 방지다(동작을 바꾸지 않는 리팩터링).

```kotlin
package com.recordofp.app.data.repo

import com.recordofp.app.domain.engine.EngineParams
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicySettingsTest {

    @Test
    fun `기본 설정은 EngineParams의 §4·5 기본값을 그대로 쓴다`() {
        val d = NotificationPolicySettings()
        assertEquals(EngineParams.COOLDOWN_PER_ITEM_MS, Duration.ofHours(d.cooldownHours.toLong()).toMillis())
        assertEquals(EngineParams.DAILY_CAP_TOTAL, d.dailyCapTotal)
        assertEquals(EngineParams.QUIET_START_MINUTE, d.quietStartMinute)
        assertEquals(EngineParams.QUIET_END_MINUTE, d.quietEndMinute)
        assertTrue(d.quietEnabled)
    }
}
```

Run: `./gradlew testDebugUnitTest --tests "*.NotificationPolicySettingsTest"`
Expected: PASS(리팩터링 전 기준선)

- [ ] **Step 2: 기본값 리터럴을 EngineParams로 바꾼다**

`SettingsStore.kt`의 데이터 클래스를 바꾼다(import `com.recordofp.app.domain.engine.EngineParams`, `java.time.Duration`).

```kotlin
/** 알림 정책 설정값 (스펙 §4.5). 기본값은 EngineParams에서만 가져온다 — 리터럴 중복 금지 (§10.2, 최종 리뷰 M1) */
data class NotificationPolicySettings(
    // 선택지 1/4/12/24 (§4.5)
    val cooldownHours: Int = Duration.ofMillis(EngineParams.COOLDOWN_PER_ITEM_MS).toHours().toInt(),
    // 선택지 5/10/20/0(0=무제한)
    val dailyCapTotal: Int = EngineParams.DAILY_CAP_TOTAL,
    val quietEnabled: Boolean = true,
    val quietStartMinute: Int = EngineParams.QUIET_START_MINUTE,
    val quietEndMinute: Int = EngineParams.QUIET_END_MINUTE,
)
```

`DataStoreSettingsStore`의 두 군데 리터럴 읽기를 하나의 `read`로 합친다.

```kotlin
    private val defaults = NotificationPolicySettings()

    private fun read(p: Preferences) = NotificationPolicySettings(
        cooldownHours = p[cooldownKey] ?: defaults.cooldownHours,
        dailyCapTotal = p[capKey] ?: defaults.dailyCapTotal,
        quietEnabled = p[quietEnabledKey] ?: defaults.quietEnabled,
        quietStartMinute = p[quietStartKey] ?: defaults.quietStartMinute,
        quietEndMinute = p[quietEndKey] ?: defaults.quietEndMinute,
    )

    override val policy: Flow<NotificationPolicySettings> = dataStore.data.map { read(it) }

    override suspend fun updatePolicy(transform: (NotificationPolicySettings) -> NotificationPolicySettings) {
        dataStore.edit { p ->
            val next = transform(read(p))
            p[cooldownKey] = next.cooldownHours
            p[capKey] = next.dailyCapTotal
            p[quietEnabledKey] = next.quietEnabled
            p[quietStartKey] = next.quietStartMinute
            p[quietEndKey] = next.quietEndMinute
        }
    }
```

(`edit` 블록의 `p`는 `MutablePreferences`이고 `Preferences`의 하위 타입이므로 `read(p)`가 그대로 받는다.)

Run: `./gradlew testDebugUnitTest --tests "*.NotificationPolicySettingsTest" --tests "*.StoreGatePolicyProviderTest"` → PASS

- [ ] **Step 3: 뒤로 버튼에 설명을 단다**

`ui/common/BackButton.kt`:

```kotlin
package com.recordofp.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.recordofp.app.R

/** 상단 바 뒤로 가기 — 하위 화면 공통 (§8 TalkBack 설명, 최종 리뷰 C2) */
@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}
```

`NearbyScreen.kt`, `DiagnosticsScreen.kt`, `SettingsScreen.kt`의 `navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }`를 `navigationIcon = { BackButton(onClick = onBack) }`로 바꾼다(import `com.recordofp.app.ui.common.BackButton`). 각 파일에서 쓰이지 않게 된 `ArrowBack`·`IconButton`·`Icon` import만 지운다(다른 곳에서 쓰이면 남긴다. 컴파일 경고로 확인한다).

문자열 ko `<string name="action_back">뒤로</string>`, en `<string name="action_back">Back</string>`.

- [ ] **Step 4: 에디터가 키보드 인셋을 받게 한다**

`AndroidManifest.xml`의 MainActivity에 속성을 더한다.

```xml
        <!-- adjustResize: edge-to-edge에서 키보드 인셋을 Compose(imePadding)로 받는다 — 에디터 아래쪽 장소 검색란 (최종 리뷰 C2) -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
```

`EditorScreen` 본문 `Column`의 modifier를 바꾼다(import `androidx.compose.foundation.layout.consumeWindowInsets`, `androidx.compose.foundation.layout.imePadding`).

```kotlin
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // 키보드가 아래쪽 장소 검색란·결과를 가리지 않게 — Scaffold가 이미 준 시스템 바 여백은 빼고 더한다
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
```

- [ ] **Step 5: 뒤로 연타 가드를 넣는다**

`AppNavHost.kt` 파일 끝에 추가한다(import `androidx.navigation.NavController`).

```kotlin
/**
 * 지금 화면이 route일 때만 뒤로 간다 — 뒤로·✕ 연타나 저장 직후 ✕가 시작 화면까지 꺼내 빈 화면이 되는 것을 막는다.
 * 백 스택은 popBackStack 즉시 바뀌므로 두 번째 호출은 무시된다 (최종 리뷰 C2)
 */
private fun NavController.popFrom(route: String) {
    if (currentDestination?.route == route) popBackStack()
}
```

`MainGraph`의 네 군데를 바꾼다.
- `EditorScreen(onDone = { navController.popBackStack() })` → `EditorScreen(onDone = { navController.popFrom(Routes.EDITOR) })`
- `NearbyScreen(onBack = { navController.popBackStack() })` → `NearbyScreen(onBack = { navController.popFrom(Routes.NEARBY) })`
- `SettingsScreen(... onBack = { navController.popBackStack() })` → `onBack = { navController.popFrom(Routes.SETTINGS) }`
- `DiagnosticsScreen(onBack = { navController.popBackStack() })` → `DiagnosticsScreen(onBack = { navController.popFrom(Routes.DIAGNOSTICS) })`

(`Routes.EDITOR`는 `"editor?reminderId={reminderId}"` 패턴이고, `currentDestination?.route`도 같은 패턴 문자열을 돌려주므로 비교가 성립한다.)

- [ ] **Step 6: 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: 테스트 전부 통과, lint 오류 0, 빌드 성공

- [ ] **Step 7: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/ui/common/BackButton.kt \
  android/app/src/main/java/com/recordofp/app/ui/nearby/NearbyScreen.kt \
  android/app/src/main/java/com/recordofp/app/ui/settings/DiagnosticsScreen.kt \
  android/app/src/main/java/com/recordofp/app/ui/settings/SettingsScreen.kt \
  android/app/src/main/java/com/recordofp/app/ui/editor/EditorScreen.kt \
  android/app/src/main/AndroidManifest.xml \
  android/app/src/main/java/com/recordofp/app/ui/AppNavHost.kt \
  android/app/src/main/java/com/recordofp/app/data/repo/SettingsStore.kt \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml \
  android/app/src/test/java/com/recordofp/app/data/repo/NotificationPolicySettingsTest.kt
git commit -m "fix: 뒤로 버튼 설명·키보드 가림·뒤로 연타 가드·정책 기본값 단일화" -m "TalkBack이 뒤로 버튼을 읽고, 키보드가 장소 검색란을 가리지 않으며, 연타해도 빈 화면이 되지 않는다.
알림 정책 기본값은 EngineParams에서만 가져온다. (§4.5, §8, §10.2, 최종 리뷰 C2·M1)"
```

---

## 마무리 — 사용자 기기 확인 목록

각 묶음 PR 본문에는 아래 목록 중 그 묶음과 관련된 항목 번호를 적는다. 네 묶음이 모두 병합되면 사용자가 실기기에서 한꺼번에 확인한다. `tools/device/e2e.sh`(macOS는 `ADB=~/Library/Android/sdk/platform-tools/adb`)와 진단 화면(설정 → 문제 해결)을 쓴다. 확인한 결과는 `docs/superpowers/notes/`에 2차 검증 노트로 남긴다.

1. **재부팅**: `adb reboot` → 잠금 해제 → 진단에 `BOOT → APPLIED`가 남고 note가 `full-resync`인지 확인한다.
2. **위치 껐다 켜기**: 기기 위치를 끄면 진단에 `FENCE_LOST`가 생기고, 홈 배너는 "기기 위치가 꺼져 있어요"가 된다. 다시 켜면 `FENCE_LOST → APPLIED`(full-resync)가 남는다.
3. **부팅 직후 오프라인**: 비행기 모드로 재부팅 → `BOOT → FAILED`, note가 `restored-from-mirror`로 시작하는지 확인한다. 네트워크를 켜면 재시도 후 APPLIED가 된다.
4. **'항상 허용' 해제와 재허용**: 해제하면 진단에 `STOOD_DOWN · ACCESS_BACKGROUND_LOCATION`이 남고, 홈 배너가 단계 안내를 담은 업셀 카드가 된다. [설정 열기]로 다시 허용하고 돌아오면 곧바로 `APP_OPEN → APPLIED`가 남는다.
5. **근처 알림 채널만 끄기**: 알림을 길게 눌러 채널을 끄면 대시보드의 알림 행이 꺼짐이 된다. 이벤트가 오면 `BLOCK_NOTIFICATIONS_OFF`가 남고, 하루 상한은 줄지 않는다.
6. **같은 가게를 PLACE와 카테고리로**: 그 가게에 가면 알림이 한 번만 뜬다. 진단에 `BLOCK_SAME_EVENT`가 남는다.
7. **여러 항목 알림**: 펼치면 모든 제목이 보이고, [오늘 그만]을 누르면 묶음 전체가 다음 날 05:00까지 억제된다.
8. **온보딩(Android 12+)**: 위치 허용을 누르면 "정확한 위치/대략적 위치" 선택 대화상자가 뜬다. 대략적 위치만 고르면 홈 배너가 "정확한 위치 권한이 필요해요"가 된다.
9. **에디터**: 브랜드를 입력만 하고 [+] 없이 저장해도 홈 칩에 브랜드가 보인다. 저장을 빠르게 두 번 눌러도 항목은 하나다.
10. **완료 실행 취소**: 스와이프 완료 → [실행 취소] → 행이 정상 모양으로 돌아온다(스와이프된 채로 끼지 않는다). TalkBack에서 카드의 "작업" 메뉴로도 완료할 수 있다.
11. **다크 모드 + 키보드**: 에디터에서 장소 검색란을 누르면 키보드가 입력란과 결과를 가리지 않는다.
12. **뒤로 연타**: 설정·주변 보기·에디터에서 뒤로를 빠르게 여러 번 눌러도 홈에서 멈춘다.
13. **백업 차단**: `adb shell dumpsys package com.recordofp.app | grep -i backup`에서 `ALLOW_BACKUP` 플래그가 없는지 확인한다.
