# v1 보강 이식 Implementation Plan

| | |
|---|---|
| 상태 | 진행 중 — 묶음 A·B·C 병합(PR #3·#4·#5), 묶음 D(Task 12~20) 구현·최종 리뷰 반영 완료(`feat/hardening-ui`, PR 전) |
| 최종 수정 | 2026-10-10 |

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 원격 기준 `feat/v1`(PR #1, 2026-10-09 `main` 병합)에, 2026-09-29 계획 검토와 로컬 참고 구현(`archive/v1-local`)에서 나온 수정 중 원격에 없는 것을 TDD로 옮긴다.

**Architecture:** 원격 구조는 그대로 둔다. 큐 2개(`reseed_now` REPLACE / `reseed_opportunistic` KEEP), 표시 후 기록(`recordShown`), `ProtectionReseedTrigger`, 클린 미니멀 UI가 여기에 해당한다. 엔진 신뢰성은 세 가지로 보강한다. DataStore의 **펜스 소실 표시(`fences_lost`)**, OS 호출부터 미러 기록까지를 묶는 **NonCancellable 선기록**, 이 앱의 OS 펜스를 PendingIntent 단위로 전부 바꾸는 **`FenceApplier.replaceAll`**이다. 알림 쪽은 이벤트 안에서 같은 항목을 한 번만 내보내고, 알림 id를 펜스 기준으로 바꾸고, PLACE 펜스에 카카오 지점 id를 싣는다. UI는 디자인 개편본 위에 손으로 다시 구현한다. 원격 UI가 로컬과 달라 cherry-pick이 되지 않기 때문이다.

**Tech Stack:** Kotlin 2.1.21, AGP 8.10, Jetpack Compose(BOM 2025.05.01), Hilt, Room 2.7.1, WorkManager 2.10.1, DataStore Preferences, Play Services Location, JUnit4 + kotlinx-coroutines-test + turbine.

**Spec:**
- 설계 문서(권위): `docs/superpowers/specs/2026-08-31-record-of-p-design.md`
- 계획 검토: `docs/superpowers/reviews/2026-09-29-v1-plan-review.md` (코드 주석의 `검토 B1` 등)
- 대조표(무엇을 옮길지의 근거): `.superpowers/sdd/2026-08-31-record-of-p-v1/2026-10-03-local-vs-remote.md` (gitignore 대상, 로컬에만 있음). 코드 주석의 `최종 리뷰 C1`·`I2` 등은 로컬 최종 리뷰 번호다.
- 참고 구현: 브랜치 `archive/v1-local` — `git show archive/v1-local:<경로>`로 읽는다. **cherry-pick 하지 않는다.** 원격 코드와 구조가 달라서, 이 계획의 코드가 원격에 맞게 고쳐 둔 버전이다.
- 묶음 D의 모양: 디자인 개편안 2 `docs/superpowers/specs/2026-10-10-design-refresh-heydealer.md`(2026-10-10 승인, 목업 링크는 문서 머리)와 디자인 시스템 `docs/design/design-system.md`

## Global Constraints

- 명령은 항상 `android/`에서 실행한다: `./gradlew testDebugUnitTest`, 전체 검증은 `./gradlew testDebugUnitTest lintDebug assembleDebug`.
- 기준선(이 계획 시작 시점, 커밋 `3d7fced`): 단위 테스트 80개 통과, `lintDebug` 오류 0·경고 22, `assembleDebug` 성공. 태스크가 끝날 때마다 테스트 전부 통과, lint 오류 0을 유지한다. 새 경고가 생기면 보고한다.
- 묶음 D 기준선(`main` 1bdcaa5, PR #6 병합 뒤): 테스트 131개 통과, lint 오류 0·경고 25(모두 기존 경고 — 의존성 버전 등). 묶음 D는 "묶음 D 공통 규칙"도 따른다.
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
4. **같은 항목이 걸린 같은 카테고리 지점 두 곳이 겹치는 자리** — 두 지점의 DWELL이 한 이벤트로 함께 들어와도 알림은 한 번만 떠야 한다(실기기 09-03 14:57:09 — CU·이마트24에서 같은 항목 알림이 동시에 떴다). PLACE(ENTER)와 POI(DWELL)는 한 이벤트에 함께 오지 않는다. → Task 7 `한 이벤트에서 같은 항목이 두 펜스로 통과해도 한 묶음에만 들어간다`
5. **'항상 허용'을 끄고, 같은 자리에서 6시간 안에 다시 켰을 때** — 돌아오자마자 펜스가 다시 등록돼야 한다. → Task 2 `standDown은 재배치 스탬프를 지워 권한이 돌아오면 APP_OPEN이 바로 재배치한다`

묶음 D(UI)의 Review Focus는 "묶음 D — UI·사용성" 절 머리에 따로 있다.

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
| D UI (실행 순서: 16 → 12 → 15 → 17 → 13 → 14 → 18 → 19 → 20) | 16 | 개편안 2 §1(토큰)·§4(아이콘) |
| | 12 | I6 |
| | 15 | C2-b, C2-c, C2-f, M1 |
| | 17 | 개편안 2 §2 에디터 |
| | 13 | I3, 묶음 B 인계(빠른 설정 위치 토글), 개편안 2 §2 보호 배너 |
| | 14 | I5, I7, N1 대책, 개편안 2 §2 완료 스와이프·스낵바 |
| | 18 | 개편안 2 §2 기록 카드·개수 pill·빈 상태 |
| | 19 | 개편안 2 §2 주변 보기 |
| | 20 | 개편안 2 §2·§3 근처 알림 |

## File Structure

```
android/app/src/main/
├─ AndroidManifest.xml                         [T5 백업 차단·추출 규칙, T15 adjustResize]
├─ res/xml/data_extraction_rules.xml           [T5 생성] DB·DataStore를 백업·기기 이전에서 제외
├─ res/values{,-en}/strings.xml                [T5·T6·T12~T15·T17~T20 문자열]
├─ res/values{,-night}/colors.xml              [T20 notification_accent]
├─ res/drawable/ic_cat_*·ic_trigger_*·ic_banner_*·ic_alert_error.xml [T16·T13·T17, tools/design/material_symbols.py가 만든다]
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
   │  ├─ notify/NearbyNotifier.kt              [T6 채널 판정, T7 펜스 기준 id, T11 InboxStyle·액션, T20 setColor]
   │  ├─ notify/AlertActions.kt                [T11 생성] 묶음별 액션 결정 (JVM 테스트)
   │  ├─ notify/NotificationActionReceiver.kt  [T10 오류 기록, T11 LongArray]
   │  ├─ work/ReseedWorkFlow.kt                [T3 생성] 워커 분기 판단 (JVM 테스트)
   │  └─ work/ReseedWorker.kt                  [T3 분기 위임·UPDATE]
   └─ ui/
      ├─ AppNavHost.kt                         [T15 popFrom]
      ├─ theme/{Color,Theme,Type}.kt           [T16 대비 보정·inverse·onSuccess·AppTextStyles]
      ├─ theme/{CategoryColors,Spacing,Motion,Previews}.kt [T16 생성]
      ├─ common/TriggerVisual.kt               [T16 생성] 트리거 → 타일 색·아이콘·이름
      ├─ common/TriggerTile.kt                 [T18 생성]
      ├─ common/DistanceBadge.kt               [T16 숫자 색, T19 distanceParts·isFarDistance]
      ├─ common/BackButton.kt                  [T15 생성]
      ├─ permissions/PermissionStatus.kt       [T6 채널·기기 위치·설정 열기, T13 topIssue]
      ├─ permissions/RememberPermissionSnapshot.kt [T13 생성] 재개·위치 토글 때 다시 읽기
      ├─ home/HomeScreen.kt                    [T13 배너, T14 실행 취소, T18 HomeContent·개수 pill]
      ├─ home/{ProtectionBanner,UndoSnackbar,ReminderCard,HomeEmptyState,HomePreviews}.kt [T13·T14·T18 생성]
      ├─ home/HomeViewModel.kt                 [T14 reactivate]
      ├─ editor/EditorViewModel.kt             [T12 저장 가드, T17 EditorActions]
      ├─ editor/EditorScreen.kt                [T12 브랜드 입력·오류 문구, T15 imePadding, T17 EditorContent]
      ├─ editor/{EditorComponents,EditorPreviews}.kt [T17 생성]
      ├─ nearby/{NearbyScreen,NearbyViewModel}.kt [T15 BackButton, T19 그룹 visual·NearbyContent]
      ├─ nearby/NearbyPreviews.kt              [T19 생성]
      ├─ onboarding/OnboardingScreen.kt        [T5 FINE+COARSE]
      └─ settings/{SettingsScreen,DiagnosticsScreen}.kt [T6·T9·T10·T13 스냅샷·T15]
tools/design/material_symbols.py               [T16 생성] Material Symbols → VectorDrawable
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

부팅 직후에는 네트워크가 없는 경우가 많다. 이때 카카오 조회가 실패하면 원격은 FAILED만 남기고 OS에는 펜스가 하나도 없는 상태로 백오프한다(§6.4 "구 데이터가 무등록보다 낫다" 위반). 이 태스크는 OS를 믿을 수 없는 상태(`osUntrusted`)에서 조회가 실패하면 미러대로 OS를 되살린다. 원격 미러에는 전이 칼럼이 없으므로, 전이는 펜스 종류에서 다시 계산한다. 또 권한을 회수해 정리(`standDown`)할 때 재배치 스탬프를 지워서, 권한을 다시 켜면 F1의 APP_OPEN이 디바운스에 걸리지 않게 한다. 정리 자체도 `replaceAll(emptyList())`로 바꿔 고아 펜스까지 지운다. 마지막으로 권한이 없는 동안 시도마다 쌓이던 진단 행을 없앤다(실기기 1차 F2). 걷어낼 등록이 없고 소실 표시도 없으면 `standDown`은 기록하지 않는다. 워커가 따로 남기던 `NO_PERMISSION` 행은 Task 3에서 `STOOD_DOWN`·사유 한 행으로 합쳐진다.

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
        val regDao = FakeRegDao().apply {
            regs["poi:1"] = GeofenceRegEntity("poi:1", "POI", 37.501, 127.0, 120f, "CU", "1", "cat:convenience", "b0", 0)
        }
        val runLog = FakeRunLog()
        val service = build(FakeReminders(listOf(convenience)), FakePoi(), regDao = regDao, runLog = runLog)

        service.standDown(ReseedCause.PERIODIC, note = "ACCESS_BACKGROUND_LOCATION")

        val row = runLog.entries.single()
        assertEquals("STOOD_DOWN", row.result)
        assertEquals("ACCESS_BACKGROUND_LOCATION", row.note)
    }

    @Test
    fun `걷어낼 등록이 없으면 standDown은 진단 기록을 남기지 않는다 - 권한 없는 동안 시도마다 쌓이던 소음(F2)`() = runTest {
        val runLog = FakeRunLog(); val applier = FakeApplier()
        val service = build(FakeReminders(listOf(convenience)), FakePoi(), runLog = runLog, applier = applier)

        assertEquals(ReseedResult.STOOD_DOWN, service.standDown(ReseedCause.PERIODIC, note = "ACCESS_BACKGROUND_LOCATION"))

        assertTrue(runLog.entries.isEmpty())
        assertTrue(applier.replaced.isEmpty()) // OS도 건드리지 않는다
        assertTrue(applier.applied.isEmpty())
    }

    @Test
    fun `소실 표시가 있으면 미러가 비어 있어도 standDown이 OS를 비우고 기록한다`() = runTest {
        // 재부팅 직후 권한이 없으면 미러는 비었어도 OS에 고아 펜스가 있을 수 있다 — 소음이 아니다
        val runLog = FakeRunLog(); val applier = FakeApplier()
        val state = FakeStateStore().apply { lost = true }
        val service = build(FakeReminders(listOf(convenience)), FakePoi(), runLog = runLog, applier = applier, stateStore = state)

        service.standDown(ReseedCause.BOOT, note = "ACCESS_FINE_LOCATION")

        assertTrue(applier.replaced.single().isEmpty())
        assertFalse(state.lost)
        assertEquals("STOOD_DOWN", runLog.entries.single().result)
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
            // 걷어낼 것이 없다. 권한이 없는 동안 워커가 시도할 때마다 같은 행이 쌓이므로 기록하지 않는다 (F2)
            return@withContext ReseedResult.STOOD_DOWN
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
전이는 펜스 종류 규칙(FenceKind.transition)으로 되살린다. 걷어낼 등록이 없으면 standDown은 기록하지 않는다.
(§6.3.5, §6.4, 검토 B3, 최종 리뷰 I2, 실기기 F2)"
```

---

### Task 3: 워커 분기 추출과 FENCE_LOST 배선

위치가 꺼지면 OS가 이 앱의 펜스를 전부 지우고 `GEOFENCE_NOT_AVAILABLE`을 보낸다. 원격 리시버는 이 오류를 무시하므로 PERIODIC(최대 6시간)까지 알림이 없다. 이 태스크는 그 오류를 `FENCE_LOST` 원인으로 재배치에 연결한다. 워커는 BOOT·FENCE_LOST 첫 시도에서 권한·위치 확인보다 먼저 펜스 소실을 표시한다. 워커의 분기 판단은 Android 타입이 없는 순수 함수로 빼서 JVM에서 테스트한다. 주기 작업 정책도 `UPDATE`로 바꾼다(KEEP이면 주기를 튜닝해도 반영되지 않는다). 권한이 없을 때 워커가 따로 남기던 `NO_PERMISSION` 행은 없어지고, `standDown`의 `STOOD_DOWN`·사유 한 행이 그 역할을 한다(F2, Task 2).

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

같은 파일의 "재배치(Reseed)" 항목 두 곳도 고친다.
- 원인 목록 `` 원인(`BOOT`/`SENTINEL_EXIT`/`PERIODIC`/`APP_OPEN`/`ITEM_CHANGE`/`RETRY`) `` → `` 원인(`BOOT`/`FENCE_LOST`/`SENTINEL_EXIT`/`PERIODIC`/`APP_OPEN`/`ITEM_CHANGE`/`RETRY`) ``
- `BOOT·PERIODIC은 미러와 상관없이 계획된 펜스를 전부 다시 등록한다.` → `BOOT·FENCE_LOST·PERIODIC과 펜스 소실 표시가 켜진 재배치는 미러와 상관없이 이 앱의 OS 펜스를 전부 지우고(`replaceAll`) 계획된 펜스를 전부 다시 등록한다.`

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

- [ ] **Step 7: 설계 문서를 1.1로 갱신하고 커밋한다 (묶음 A 전체 반영)**

설계 문서는 살아있는 문서다(CLAUDE.md 문서 규칙). 묶음 A가 바꾼 엔진 동작을 `docs/superpowers/specs/2026-08-31-record-of-p-design.md`에 반영한다. §번호는 바꾸지 않는다.

1. §6.2 표에서 `| BOOT / PACKAGE_REPLACED | 재부팅·앱 업데이트 후 복구 |` 바로 아래에 한 행을 넣는다.
   `| FENCE_LOST | 위치가 꺼지는 등으로 OS가 이 앱의 지오펜스를 전부 지웠을 때(`GEOFENCE_NOT_AVAILABLE`) 복구 |`
2. §6.2의 `디바운스: 재배치 최소 간격 10분(ITEM_CHANGE는 예외 — 즉시 반영하되 30초 코얼레싱).`을 바꾼다.
   `디바운스: 재배치 최소 간격 10분. 예외는 ITEM_CHANGE(즉시 반영하되 30초 코얼레싱), BOOT·FENCE_LOST(펜스가 사라졌다), PERIODIC(최후 방어선이고 6시간에 한 번이라 쿼터 부담이 없다)다. 시계가 거꾸로 가 마지막 재배치 시각이 미래에 있으면 간격이 지난 것으로 본다.`
3. §6.3 5번의 `   - SENTINEL: 반경 1.0km, `EXIT`.`을 바꾼다.
   `   - SENTINEL: 반경 1.0km, `EXIT`. 등록하는 순간 이미 원 밖이면(차량 이동, 오래된 위치) 곧바로 EXIT를 낸다. POI·PLACE는 등록하는 순간 이미 안에 있어도 알리지 않는다.`
4. §6.3 6번(`6. **적용**: …차분 적용으로 이벤트 유실 창을 줄인다.`) 끝에 이어 쓴다.
   ` 단, OS 등록을 믿을 수 없을 때(BOOT·FENCE_LOST, 또는 OS 반영과 미러 기록이 함께 끝나지 못해 남은 **펜스 소실 표시**)와 PERIODIC 헬스체크는 이 앱의 OS 지오펜스를 전부 지우고(고아 포함) 계획 전체를 다시 등록한다. OS 반영을 시작하면 작업이 취소돼도 미러 기록까지 마친다.`
5. §6.4 첫 항목(`- 카카오 API 실패/오프라인: …RETRY 백오프 예약.`) 끝에 이어 쓴다.
   ` 단, OS 등록을 믿을 수 없는 상태라면 OS에 "유지할 기존 등록"이 없으므로 미러(`geofence_reg`)대로 OS를 되살린 뒤 RETRY한다(부팅 직후 오프라인).`
6. §6.4 `- 권한 회수 감지: …(고아 지오펜스 방지).` 끝에 이어 쓴다.
   ` 이때 마지막 재배치 기록도 지워, 권한이 돌아오면 다음 재배치가 디바운스 없이 바로 돈다. 걷어낼 등록이 없으면 진단 기록을 남기지 않는다(권한이 없는 동안 시도마다 쌓이는 소음 방지).`
7. 머리말 표: `| 버전 | 1.0 |` → `| 버전 | 1.1 |`, `최종 수정`을 커밋하는 날짜로 바꾼다.
8. 끝의 변경 이력 표에 한 행을 더한다(날짜는 커밋하는 날짜).
   `| 1.1 | <날짜> | §6.2, §6.3, §6.4 | FENCE_LOST 원인, 디바운스 예외(BOOT·FENCE_LOST·PERIODIC)와 시계 역행, 펜스 소실 표시와 전체 재등록, 센티널 즉시 이탈, 조회 실패 시 미러 복구, 권한 회수 시 스탬프 삭제와 무소음 | 보강 계획 묶음 A(T1~T4), 검토 B1·B3·B4, 최종 리뷰 C1·I2, 실기기 F2 |`

```bash
git add docs/superpowers/specs/2026-08-31-record-of-p-design.md
git commit -m "docs: 설계 문서 1.1 — 묶음 A 엔진 보강 반영" -m "FENCE_LOST, 디바운스 예외, 펜스 소실 표시·전체 재등록, 센티널 즉시 이탈, 미러 복구, 권한 회수 정리. (§6.2, §6.3, §6.4)"
```

### 묶음 A 인계 (2026-10-09 최종 리뷰)

묶음 A(`feat/hardening-engine`) 최종 리뷰에서 나왔지만 이 묶음에서 고치지 않은 것이다. 해당 태스크를 실행할 때 반영한다.

- **Task 10**: 센티널 이탈은 미러에 `sentinel` 행이 있는지와 상관없이 펜스 키로 판정한다. 첫 재배치 직후 `INITIAL_TRIGGER_EXIT` 이탈이 미러 커밋보다 먼저 도착하면, 지금은 stale로 버려져 센티널이 소모된다. 잘못된 센티널 이탈의 대가는 디바운스되는 재배치 1회뿐이다.
- **Task 9 또는 10**: `DiagnosticsScreen`의 `NO_PERMISSION` 색 분기를 정리한다. 워커는 더 이상 이 행을 쓰지 않는다(F2). 다만 진단 로그 보존 기간 안의 옛 행이 남아 있을 수 있다.
- **후속(태스크 미정)**: `ReseedService`의 NonCancellable 안 GMS 호출에 타임아웃(`EngineParams` 상수)을 둔다. GMS Task가 멈추면 mutex를 프로세스가 죽을 때까지 잡는다. `TimeoutCancellationException`을 실패로 처리하도록 세 곳(reseed·restoreFromMirror·standDown)의 catch 순서를 바꿔야 하므로 별도 TDD로 한다.
- **다음 설계 문서 개정**: §5.3의 `geofenceId(PK, uuid)`는 실제로는 안정 키(`sentinel`·`poi:<id>`·`place:<id>`)다. §6.2 RETRY의 "15분→1h→6h"는 WorkManager 지수 백오프(15분 시작, 상한 5시간)와 다르다.

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

- [ ] **Step 11: 설계 문서를 1.2로 갱신하고 커밋한다 (묶음 B 전체 반영)**

설계 문서는 살아있는 문서다(CLAUDE.md 문서 규칙). 묶음 B(Task 5~6)가 바꾼 동작을 `docs/superpowers/specs/2026-08-31-record-of-p-design.md`에 반영한다. §번호는 바꾸지 않는다.

1. §4.2 표의 3단계 행 `| 3. 위치(사용 중) | `ACCESS_FINE_LOCATION` | 주변 보기·지오펜스 불가, 목록 앱으로 동작 |`을 바꾼다.
   `| 3. 위치(사용 중) | `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`(함께 요청 — Android 12+는 FINE 단독 요청을 무시한다) | 주변 보기·지오펜스 불가, 목록 앱으로 동작. "대략적 위치"만 허용하면 보호 상태가 "정확한 위치 꺼짐"으로 안내한다 |`
2. §4.3 첫 문장 `알림 권한 / 위치 권한 / 항상 허용 / 배터리 최적화 예외 / 최근 엔진 동작 시각을 신호등으로 표시. 하나라도 꺼지면 홈 상단에 배너로 노출한다.`를 바꾼다.
   `알림(앱 알림과 "근처 알림" 채널) / 정확한 위치 / 항상 허용 / 기기 위치 서비스 / 배터리 최적화 예외 / 최근 엔진 동작 시각을 신호등으로 표시. 배터리 최적화 예외를 뺀 항목 중 하나라도 꺼지면 홈 상단에 배너로 노출한다(배터리 예외는 권장 사항이라 배너에 넣지 않는다). 꺼진 항목을 누르면 해당 시스템 설정 화면을 열고, 그 화면이 없는 제조사 빌드에서는 앱 상세 설정으로 대신 연다.`
3. §6.5 5번 `5. NotificationLog 기록`을 바꾼다.
   `5. 알림이 실제로 표시된 뒤에만 NotificationLog 기록. 앱 알림이나 "근처 알림" 채널이 꺼져 있으면 발행하지 않고 기록하지 않는다 — 보이지 않은 알림이 쿨다운·하루 상한을 소모하지 않게 한다.`
4. §9 첫 항목 `- 위치·항목 데이터는 기기 밖으로 나가지 않는다. 유일한 외부 통신은 카카오 POI 조회(좌표 전송, 무저장).` 끝에 이어 쓴다.
   ` 클라우드 백업과 기기 간 이전에서도 DB·DataStore를 뺀다(`allowBackup=false`, 데이터 추출 규칙). 미러만 새 기기로 옮겨지면 OS에 없는 펜스를 "등록됨"으로 믿게 되는 문제도 함께 막는다.`
5. 머리말 표: `| 버전 | 1.1 |` → `| 버전 | 1.2 |`, `최종 수정`을 커밋하는 날짜로 바꾼다.
6. 끝의 변경 이력 표에 한 행을 더한다(날짜는 커밋하는 날짜).
   `| 1.2 | <날짜> | §4.2, §4.3, §6.5, §9 | 위치 권한 FINE+COARSE 동시 요청, 보호 상태에 "근처 알림" 채널·기기 위치 서비스 추가와 배너 기준, 채널 꺼짐 시 미발행·미기록, 백업·기기 이전 제외 | 보강 계획 묶음 B(T5~T6), 검토 B2·B8·C2, 최종 리뷰 I4·M8 |`

```bash
git add docs/superpowers/specs/2026-08-31-record-of-p-design.md
git commit -m "docs: 설계 문서 1.2 — 묶음 B 프라이버시·권한 반영" -m "FINE+COARSE 요청, 보호 상태 항목(채널·기기 위치), 채널 꺼짐 시 미발행, 백업 제외. (§4.2, §4.3, §6.5, §9)"
```

### 묶음 B 인계 (2026-10-09 최종 리뷰)

묶음 B(`feat/hardening-privacy`) 최종 리뷰에서 나왔지만 이 묶음에서 고치지 않은 것이다.

- **Task 13**: 빠른 설정(알림창)에서 기기 위치를 켜면 액티비티가 멈추지 않아 `LifecycleResumeEffect`가 돌지 않는다. 그래서 배너·대시보드와 보호 복구 재배치(F1)가 다음 화면 이동까지 늦어진다. 재개된 동안 `LocationManager.MODE_CHANGED_ACTION`도 받아 스냅샷을 다시 읽는다(Home·Settings).
- **Task 9**: `show()`가 false일 때(앱 알림·근처 알림 채널 꺼짐) `BLOCK_NOTIFICATIONS_OFF`를 진단에 남기는 일은 Task 9 몫이다. 기기 확인 5번의 진단 절반은 Task 9 이후에 확인할 수 있다.

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

`DiagnosticsScreen.kt`의 `resultDotColor`를 바꾼다. PASS를 초록으로 더하고, 묶음 A 인계대로 `NO_PERMISSION` 분기를 지운다. 워커는 088ec01(F2) 뒤로 이 결과를 쓰지 않는다. 남은 옛 행은 보존 기간(14일) 안에 지워지고, 그동안은 회색으로 보인다.

```kotlin
    result.contains("APPLIED") || result == "PASS" -> successColor()
    result.startsWith("BLOCK") || result.contains("FAILED") ->
        MaterialTheme.colorScheme.error
```

KDoc을 `/** 결과별 컬러 도트 — APPLIED·PASS 초록 · BLOCK/FAILED 빨강 · 그 밖(STOOD_DOWN·STALE 등) 회색 (개편안 §2) */`로 고친다.

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

> 묶음 A 인계: 센티널 이탈은 미러 유무와 상관없이 키로 판정한다 — "묶음 A 인계" 절 참고.

원격 리시버는 프로세스가 죽지 않게 예외를 잡고는 있다. 하지만 센티널 재배치를 알림 루프 **뒤에** 예약하고, 묶음별 예외 격리가 없다. 그래서 알림 하나가 예외를 내면(예: 방금 알림 권한 회수) 나머지 알림과 SENTINEL_EXIT가 함께 사라져 이동 감지 사슬이 끊긴다. 오류도 시스템 로그로만 남아 진단 화면에 보이지 않는다. 이 태스크는 처리 순서를 순수 함수(`runFenceEvent`)로 빼서 JVM에서 테스트한다. 센티널을 먼저 예약하고, 묶음마다 따로 처리하고, 오류는 EngineRunLog에 남긴다. 센티널 예약 자체가 실패해도 알림은 계속 발행한다(로컬 N2).

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/platform/ReceiverErrorLog.kt`
- Create: `android/app/src/main/java/com/recordofp/app/platform/geofence/FenceEventFlow.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceBroadcastReceiver.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/notify/NotificationActionReceiver.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/domain/engine/FencePlan.kt`, `ReseedPlanner.kt` (센티널 키 상수)
- Modify: `android/app/src/main/java/com/recordofp/app/data/engine/GeofenceEventHandler.kt` (센티널을 키로 판정 — 묶음 A 인계)
- Modify: `android/app/src/main/java/com/recordofp/app/ui/settings/DiagnosticsScreen.kt`
- Test: `android/app/src/test/java/com/recordofp/app/platform/geofence/FenceEventFlowTest.kt`, `android/app/src/test/java/com/recordofp/app/platform/ReceiverErrorLogTest.kt` (둘 다 생성), `android/app/src/test/java/com/recordofp/app/data/engine/GeofenceEventHandlerTest.kt` (수정)

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

`GeofenceEventHandlerTest`에 추가한다(import `com.recordofp.app.domain.engine.SENTINEL_FENCE_KEY`). 묶음 A 인계 항목이다.

```kotlin
    @Test
    fun `미러에 센티널 행이 없어도 센티널 키 이벤트는 이탈로 보고하고 stale로 남기지 않는다`() = runTest {
        // 첫 재배치 직후 즉시 이탈(INITIAL_TRIGGER_EXIT)이 미러 기록보다 먼저 도착하는 경우 (묶음 A 인계)
        val runs = FakeRuns()
        val out = build(FakeRegs(), FakeSpecs(emptyMap()), FakeReminderDao(emptyMap()), runs = runs)
            .onFenceEvent(listOf(SENTINEL_FENCE_KEY), null)
        assertTrue(out.sentinelExited)
        assertTrue(runs.entries.isEmpty())
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.FenceEventFlowTest" --tests "*.ReceiverErrorLogTest" --tests "*.GeofenceEventHandlerTest"`
Expected: 컴파일 실패 — `SENTINEL_FENCE_KEY`, `FenceEventSteps`, `runFenceEvent`, `logReceiverError`가 없다.

- [ ] **Step 3: 센티널 키를 상수로 만든다**

`FencePlan.kt`의 `enum class FenceKind` 위에 추가한다.

```kotlin
/** 센티널 펜스의 OS requestId·미러 키 — 리시버가 이벤트에 센티널이 있는지 볼 때도 쓴다 */
const val SENTINEL_FENCE_KEY = "sentinel"
```

`ReseedPlanner.kt` 센티널의 `key = "sentinel",` → `key = SENTINEL_FENCE_KEY,`

`GeofenceEventHandler.onFenceEvent` 루프 첫머리에서 센티널을 미러 조회보다 먼저 키로 판정한다(묶음 A 인계). Task 9의 stale 분기 바로 앞에 넣고, 그 뒤의 `if (reg.kind == FenceKind.SENTINEL.name) { ... }` 줄은 지운다(쓰이지 않게 된 `FenceKind` import도 지운다).

```kotlin
            // 센티널은 미러와 상관없이 키로 판정한다 — 첫 재배치 직후 즉시 이탈(INITIAL_TRIGGER_EXIT)이 미러 기록보다
            // 먼저 도착해도 이동 감지 사슬이 끊기지 않는다. 잘못 판정한 대가는 디바운스되는 재배치 1회다 (묶음 A 인계)
            if (fenceId == SENTINEL_FENCE_KEY) { sentinelExited = true; continue }
```

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

`resultDotColor`의 빨강 조건에 `|| result == "ERROR"`를 더한다(`NO_PERMISSION`은 Task 9에서 지웠다). KDoc의 빨강 목록에도 ERROR를 더한다.

```kotlin
    result.startsWith("BLOCK") || result.contains("FAILED") || result == "ERROR" ->
        MaterialTheme.colorScheme.error
```

- [ ] **Step 8: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.FenceEventFlowTest" --tests "*.ReceiverErrorLogTest" --tests "*.ReseedPlannerTest" --tests "*.GeofenceEventHandlerTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → 전부 통과, lint 오류 0

- [ ] **Step 9: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/platform/ReceiverErrorLog.kt \
  android/app/src/main/java/com/recordofp/app/platform/geofence/FenceEventFlow.kt \
  android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceBroadcastReceiver.kt \
  android/app/src/main/java/com/recordofp/app/platform/notify/NotificationActionReceiver.kt \
  android/app/src/main/java/com/recordofp/app/domain/engine/FencePlan.kt \
  android/app/src/main/java/com/recordofp/app/domain/engine/ReseedPlanner.kt \
  android/app/src/main/java/com/recordofp/app/data/engine/GeofenceEventHandler.kt \
  android/app/src/main/java/com/recordofp/app/ui/settings/DiagnosticsScreen.kt \
  android/app/src/test/java/com/recordofp/app/platform/geofence/FenceEventFlowTest.kt \
  android/app/src/test/java/com/recordofp/app/platform/ReceiverErrorLogTest.kt \
  android/app/src/test/java/com/recordofp/app/data/engine/GeofenceEventHandlerTest.kt
git commit -m "fix: 지오펜스 리시버 무중단 - 센티널 우선 예약과 묶음별 예외 격리" -m "알림 하나가 실패해도 나머지 알림과 센티널 재배치가 살아남고, 리시버 오류는 진단 로그에 남는다.
처리 순서는 runFenceEvent로 JVM 테스트한다. 센티널 이탈은 미러와 상관없이 키로 판정한다(묶음 A 인계).
(§4.4, §6.2, §6.5, 최종 리뷰 I1)"
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

- [ ] **Step 8: 설계 문서를 1.3으로, CLAUDE.md 이벤트 파이프라인을 갱신하고 커밋한다 (묶음 C 전체 반영)**

설계 문서는 살아있는 문서다(CLAUDE.md 문서 규칙). 묶음 C(Task 7~11)가 바꾼 동작과 묶음 A 인계의 "다음 설계 문서 개정" 두 항목을 `docs/superpowers/specs/2026-08-31-record-of-p-design.md`에 반영한다. §번호는 바꾸지 않는다.

1. §4.1 2번의 `액션 버튼 [완료] [오늘 그만]. 탭하면 해당 항목으로 딥링크.`를 바꾼다.
   `항목이 하나면 액션 버튼 [완료] [오늘 그만]을 달고, 탭하면 해당 항목으로 딥링크한다. 여러 항목이면 펼쳤을 때 모든 제목을 보여 주고 [오늘 그만]만 단다(묶음 전체를 억제한다. [완료]는 어느 항목인지 모호해서 두지 않는다). 탭하면 홈으로 간다.`
2. §4.4 끝에 이어 쓴다.
   ` 실제로 표시한 알림(PASS), 필터 차단 사유, 같은 이벤트 안의 중복(BLOCK_SAME_EVENT), 등록에 없는 이벤트(STALE), 알림이 꺼져 표시하지 못한 경우(BLOCK_NOTIFICATIONS_OFF), 리시버 오류(ERROR)를 모두 남긴다. 그래서 필드 테스트(§10.2)의 발화·미발화를 이 화면만으로 집계할 수 있다.`
3. §4.5 표 바로 아래에 한 줄을 더한다.
   `"같은 지점"은 카카오 장소 id로 판정한다. PLACE 트리거도 사용자가 고른 장소의 카카오 id로 이 재알림 간격이 걸린다(id 없이 저장된 옛 PLACE는 항목당 쿨다운만 걸린다).`
4. §5.3 `GeofenceReg` 행의 `geofenceId(PK, uuid)`를 `geofenceId(PK, 안정 키 = OS requestId — `sentinel`·`poi:<카카오 id>`·`place:<TriggerSpec id>`)`로 바꾼다(묶음 A 인계).
5. §6.2 RETRY 행의 `직전 재배치 실패 시 백오프(15분→1h→6h) 재시도`를 `직전 재배치 실패 시 WorkManager 지수 백오프(15분에서 시작해 두 배씩, 상한 5시간)로 재시도`로 바꾼다(묶음 A 인계).
6. §6.3 1번 끝에 이어 쓴다.
   ` PLACE 펜스에는 사용자가 고른 장소의 카카오 id를 실어 같은 지점 재알림 간격(§4.5)의 키로 쓴다.`
7. §6.5 1·3·4·5번을 바꾼다.
   - 1번: `1. 이벤트의 geofenceId가 현재 등록 테이블에 유효한지 검증(stale 이벤트는 폐기하고 진단에 STALE로 남긴다). 단, 센티널은 미러와 상관없이 키(`sentinel`)로 판정한다 — 첫 재배치 직후 즉시 이탈이 미러 기록보다 먼저 도착해도 이동 감지가 끊기지 않게 한다. 센티널 이탈 재배치는 알림 발행보다 먼저 예약한다.`
   - 3번 끝에 이어 쓴다: ` 한 이벤트에 여러 펜스가 함께 들어와 같은 항목이 두 번 통과하면 첫 펜스에만 넣는다(BLOCK_SAME_EVENT). 표시 기록은 표시한 뒤에 하므로 같은 이벤트 안에서는 쿨다운이 걸리지 않기 때문이다.`
   - 4번: `4. 통과 항목을 펜스 단위로 묶어 **알림 1건** 발행(항목 나열 — §4.1). 알림 id도 펜스 기준이라 같은 가게의 PLACE 알림과 카테고리 알림이 서로 덮어쓰지 않는다. 묶음마다 따로 처리해, 하나가 실패해도(예: 방금 알림 권한 회수) 나머지 알림과 센티널 재배치는 살아남는다. 리시버에서 잡은 오류는 진단에 ERROR로 남긴다. 알림 채널 분리: "근처 알림"(높음), "서비스 상태"(낮음)`
   - 5번 끝에 이어 쓴다: ` 표시한 항목은 진단에 PASS로, 표시하지 못한 묶음은 BLOCK_NOTIFICATIONS_OFF로 남긴다.`
8. 머리말 표: `| 버전 | 1.2 |` → `| 버전 | 1.3 |`, `최종 수정`을 커밋하는 날짜로 바꾼다.
9. 끝의 변경 이력 표에 한 행을 더한다(날짜는 커밋하는 날짜).
   `| 1.3 | <날짜> | §4.1, §4.4, §4.5, §5.3, §6.2, §6.3, §6.5 | 여러 항목 알림의 목록·[오늘 그만], 진단 기록(PASS·STALE·BLOCK_SAME_EVENT·BLOCK_NOTIFICATIONS_OFF·ERROR), PLACE 지점 쿨다운(카카오 id), 이벤트 안 중복 제거와 펜스 기준 알림, 센티널 키 판정과 우선 예약, 묶음별 예외 격리. 기존 불일치 정정(geofenceId는 안정 키, RETRY 백오프) | 보강 계획 묶음 C(T7~T11), 검토 B7·C1·C2, 최종 리뷰 I1, 묶음 A 인계 |`

`CLAUDE.md` "두 개의 핵심 파이프라인"의 2번(이벤트) 줄을 바꾼다.

`2. **이벤트(Notification)** — 지오펜스 전이 → `GeofenceBroadcastReceiver`(goAsync, 처리 순서·예외 격리는 `runFenceEvent`) → 센티널은 키로 판정해 재배치를 알림보다 먼저 예약 → 미러에 없는 id(stale)는 폐기 → `reg_trigger`→`trigger_spec`→`reminder` 로드 → `NotificationGate` 필터 체인(상태→스누즈→방해금지→항목 쿨다운→항목·지점 쿨다운→항목당 일 상한→전체 일 상한) → 같은 이벤트에서 이미 통과한 항목은 건너뜀 → 펜스 단위로 묶어 알림 1건 발행(알림 id도 펜스 기준). `NotificationLog`는 알림이 실제로 표시된 뒤에 `recordShown`으로 기록한다. 통과(PASS)·차단 사유·stale·표시 실패·리시버 오류는 진단 화면용으로 `EngineRunLog`에 남긴다.`

```bash
git add docs/superpowers/specs/2026-08-31-record-of-p-design.md CLAUDE.md
git commit -m "docs: 설계 문서 1.3 — 묶음 C 알림 정확도 반영" -m "여러 항목 알림, 진단 기록, PLACE 지점 쿨다운, 이벤트 안 중복 제거·펜스 기준 알림, 센티널 키 판정, 묶음별 격리.
geofenceId·RETRY 백오프 기존 불일치 정정. (§4.1, §4.4, §4.5, §5.3, §6.2, §6.3, §6.5)"
```

### 묶음 C 인계 (2026-10-09 최종 리뷰)

묶음 C(`feat/hardening-alerts`) 최종 리뷰에서 나왔지만 이 묶음에서 고치지 않은 것이다.

- **후속(태스크 미정)**: 지오펜스 브로드캐스트는 각자 코루틴에서 돌아, 거의 동시에 온 두 이벤트가 모두 기록(`recordShown`) 전에 게이트를 통과하면 같은 항목 알림이 두 번 뜰 수 있다. 프로세스 전역 `Mutex`로 `runFenceEvent`를 직렬화한다. 동시성 변경이라 별도 TDD로 한다.
- **후속(태스크 미정)**: 리시버 ERROR 행의 note에는 예외 클래스·메시지만 있다. 어느 펜스·항목의 표시가 실패했는지(`fenceId`, 리마인더 id)를 함께 남긴다.
- **묶음 D 전 디자인 라운드**: 여러 항목 알림의 InboxStyle은 펼쳐도 일정 줄 수(Android 버전에 따라 5~7줄)만 보인다. 넘치면 요약 줄에 "+N"을 단다(문자열 ko·en 필요). 묶음 알림의 모양(목록·액션)도 이 라운드에서 함께 확인한다. → 2026-10-10 디자인 라운드(개편안 2 §2)가 여러 항목 알림을 "T11 그대로"로 정해 묶음 D에서 하지 않는다. "+N"은 후속 후보로 남긴다.

---

## 묶음 D — UI·사용성 (2026-10-10 갱신: 디자인 개편안 2 반영)

> **갱신 기록**: 디자인 개편안 2(`docs/superpowers/specs/2026-10-10-design-refresh-heydealer.md`, 2026-10-10 승인)의 §5를 태스크로 옮겼다. 원래 Task 12~15의 동작 수정은 그대로 두고 시각 사항을 더했으며, 새 Task 16~20을 붙였다. 태스크 번호는 추가한 순서다. **실행은 아래 표의 순서이고, 이 절의 문서 순서도 같다.** 이 절의 코드는 `main`(1bdcaa5)에서 딴 임시 worktree에 이 순서로 적용해, 태스크마다 `./gradlew testDebugUnitTest lintDebug assembleDebug`가 통과하는 것을 확인했다(테스트 수는 표의 누적값, lint는 매번 오류 0·경고 25).

| 순서 | 태스크 | 내용 | 테스트(누적) |
|---|---|---|---|
| 1 | Task 16 | 토큰·아이콘 — 대비 보정, 완료·스낵바 색, 카테고리 색, 간격·모션, 글자 추가 스타일, 아이콘, 트리거 시각 매핑, 디자인 시스템 1.2 | 140 |
| 2 | Task 12 | 에디터 저장 가드 — 입력 중 브랜드 보존, 연타 방지, 실패 안내 | 146 |
| 3 | Task 15 | UI 마감 — 뒤로 버튼 설명, 키보드 가림, 뒤로 연타 가드, 정책 기본값 단일화 | 147 |
| 4 | Task 17 | 에디터 시각 — 카드 바탕 입력, 카테고리 색 칩, 브랜드 [추가]·안내, 저장 실패 면 | 147 |
| 5 | Task 13 | 홈 보호 배너 — 꺼진 것을 이름으로, 단계 칩, 빠른 설정 위치 토글 반영 | 150 |
| 6 | Task 14 | 완료 실행 취소 — 한 방향 스와이프, 스낵바, TalkBack 완료 | 152 |
| 7 | Task 18 | 홈 카드·개수 pill·빈 상태 | 155 |
| 8 | Task 19 | 주변 보기 — 그룹 타일, 지점 카드, 큰 거리 숫자 | 159 |
| 9 | Task 20 | 근처 알림 — 제목 이모지 제거, 브랜드 블루. 문서 마감(설계 1.4, 디자인 시스템 1.3) | 160 |

Task 15를 앞으로 옮겼다. 뒤로 버튼(`BackButton`)과 키보드 인셋(`imePadding`)이 먼저 들어가 있어야 Task 17·19가 화면을 다시 그릴 때 그대로 들고 간다.

### 묶음 D 공통 규칙

Global Constraints에 더해 이 묶음의 모든 태스크에 적용한다.

- 화면 코드의 색·글꼴은 테마 토큰과 `ui/theme` 헬퍼만 쓴다(`Color(0x…)`·`.sp` 금지). 간격은 `Spacing` 단계(4·8·12·16·20·24·32dp)에 있는 값을 토큰으로 쓴다. 단계 밖의 값(카드 사이 10, 카드 안쪽 14, 칩 패딩 9, 행 좌우 18 등)은 그 컴포넌트 안 리터럴로 둔다.
- 고치는 화면은 본체를 상태와 동작만 받는 `XxxContent`로 떼고 `@LightDarkPreviews`(라이트·다크)와 큰 글꼴(`fontScale = 2f`) 미리보기를 둔다. 미리보기는 `RecordOfPTheme { }`를 기본값으로 감싼다(기본값이 `uiMode`를 따르므로 `successColor()` 같은 헬퍼와 스킴이 같은 테마를 본다).
- 고정 높이 대신 `heightIn(min = …)`을 쓴다. 큰 글꼴에서 잘리지 않게 하기 위해서다. 터치 영역은 48dp 이상이다(Material 버튼·칩·IconButton은 기본으로 보장한다).
- 아이콘은 `tools/design/material_symbols.py`의 `ICONS`에 한 줄 넣고 저장소 루트에서 `python3 -I tools/design/material_symbols.py`를 다시 실행해 만든다. drawable을 손으로 쓰지 않고 아이콘 라이브러리를 더하지 않는다. 스크립트는 jsDelivr에서 `@material-symbols/svg-400@0.47.0`을 받는다(개발 도구가 쓰는 네트워크이고, 앱의 외부 통신과는 무관하다).
- `TriggerCatalog.emoji`는 화면에서 쓰지 않는다. 필드는 남긴다(개편안 2 §4, iOS 포팅 때 판단).
- 묶음 C 인계의 InboxStyle "+N"은 하지 않는다. 개편안 2 §2가 여러 항목 알림을 "T11 그대로"로 정했다.
- 커밋 전에 `./gradlew testDebugUnitTest lintDebug assembleDebug`의 테스트 수가 위 표와 같은지 본다. lint 경고가 25개를 넘으면 무엇이 늘었는지 보고한다.

### 구현에서 개편안 2와 다른 점 (2026-10-10 사용자 승인)

목업을 코드로 옮기면서 생긴 차이다. 사용자가 계획 검토에서 이대로 승인했다.

1. **입력 필드 높이**: 개편안은 제목 56dp, 나머지 48~52dp다. 계획은 모든 필드를 Material 텍스트 필드 최소 높이 56dp로 두고, 옆의 [추가]·검색 버튼도 56dp로 맞춘다. `OutlinedTextField`의 TalkBack 힌트와 포커스 테두리(1dp → 2dp 블루)를 그대로 쓰기 위해서다.
2. **브랜드·지점 칩 지우기**: 목업은 칩 안의 28dp ✕ 버튼이다. 계획은 칩 전체를 누르면 빠지게 한다(터치 영역 48dp). ✕는 표시로 남고, TalkBack은 "GS25 삭제"로 읽는다.
3. **브랜드 안내 문구**: 입력란에 글자가 있을 때만 보인다. 목업은 입력 중 상태만 그렸다.
4. **메모 입력**: 지금은 두 줄 높이로 시작한다. 목업에 맞춰 한 줄로 시작해 내용만큼 늘어난다.
5. **영어 문구**: 앱 영어는 기록을 "note"라고 부른다(New note, Edit note, Delete this note?). 개편안 §3의 "reminder"를 "note"로 바꿔 쓴다 — "No notes yet", "%d notes", "Saved with the note even if you don't tap Add".
6. **글자 크기**: 목업의 14px 버튼 글자(배너 [설정 열기])는 타입 스케일의 `labelLarge`(15sp)로 쓴다. 타입 스케일 밖의 크기는 개편안이 정한 두 가지(거리 숫자 22, 빈 상태 제목 20)만 더한다.
7. **다크 알림 강조색**: 개편안은 `setColor(primary)`만 정했다. 다크에서는 다크 primary(`#5B95F8`, `values-night`)를 쓴다.
8. **카탈로그에서 빠진 옛 카테고리**: 지금은 "편의점"으로 잘못 보인다. 계획은 id를 그대로 보이고 회색 타일·검색 아이콘으로 그린다.
9. **카드 글자 줄 수**: 홈 카드 제목은 두 줄, 트리거 줄은 한 줄까지 보이고 넘치면 말줄임한다(개편안에 없는 경우).

### 묶음 D Review Focus

개편안이 암시하지만 화면 목업이 다루지 않은 조건 중, 사용자에게 가장 먼저 문제가 될 다섯 가지다. 각 줄의 확인 수단을 담당 태스크에 넣었다.

1. **카탈로그에서 빠진 옛 카테고리 id가 저장된 기록**(앱 업데이트로 카탈로그 항목이 빠진 경우) — 홈 카드·에디터가 죽거나 다른 카테고리 이름을 보이면 안 된다. → Task 16 `카탈로그에서 빠진 옛 카테고리 id도 그릴 수 있다 - 고유 색 없이 검색 아이콘`
2. **브랜드 입력란에 공백만 있을 때** — 저장 버튼이 켜지거나 빈 브랜드 칩이 생기면 안 된다. → Task 12 `공백만 입력한 브랜드는 트리거로 세지 않는다`
3. **시스템 글꼴 크기 최대(200%)** — 개수 pill·칩·배너 버튼·스낵바·카드가 잘리거나 겹치면 안 된다. → 고정 높이 금지(`heightIn`), Task 13·17·18·19의 큰 글꼴 미리보기, 마무리 기기 확인 17번
4. **1km를 넘는 거리와 쉼표 소수점 언어의 기기** — "1,1km"로 보이거나 먼 곳이 가까운 곳처럼 강조되면 안 된다. → Task 19 `1km를 넘어야 멀다`, `기기 언어가 쉼표 소수점이어도 점으로 쓴다`
5. **대소문자를 섞어 입력한 브랜드(GS25)** — 주변 보기 그룹 머리가 matchKey의 소문자("gs25")로 보이면 안 된다. → Task 19 `그룹은 머리에 그릴 트리거를 함께 낸다 - 브랜드는 입력한 대소문자 그대로`

---

### Task 16: 토큰·아이콘 — 개편안 2의 바탕 (디자인 시스템 1.2)

개편안 2 §1(토큰)과 §4(아이콘)를 코드와 디자인 시스템 문서로 옮긴다. 화면 구성은 아직 바꾸지 않는다. 눈에 보이는 변화는 라이트의 보조 글자·오류·테두리·완료 바탕 색과 에디터 장소 결과의 거리 배지 숫자 색뿐이다. 대비 약속(글자 4.5:1, 테두리·아이콘 3:1, 다크 타일 6:1)은 JVM 테스트로 지킨다. 트리거를 타일 색·아이콘·이름으로 바꾸는 `TriggerVisual`도 여기서 만든다. 뒤 태스크의 에디터 칩·홈 카드·주변 보기 그룹 머리가 모두 이것을 쓴다.

**Files:**
- Create: `tools/design/material_symbols.py`
- Create(스크립트가 만든다): `android/app/src/main/res/drawable/ic_cat_convenience.xml`, `ic_cat_mart.xml`, `ic_cat_pharmacy.xml`, `ic_cat_bank.xml`, `ic_cat_post.xml`, `ic_cat_fuel.xml`, `ic_cat_laundry.xml`, `ic_cat_cafe.xml`, `ic_cat_hospital.xml`, `ic_cat_subway.xml`, `ic_trigger_brand.xml`, `ic_trigger_search.xml`, `ic_trigger_place.xml`
- Create: `android/app/src/main/java/com/recordofp/app/ui/theme/CategoryColors.kt`, `Spacing.kt`, `Motion.kt`, `Previews.kt`
- Create: `android/app/src/main/java/com/recordofp/app/ui/common/TriggerVisual.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/theme/Color.kt`, `Theme.kt`, `Type.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/common/DistanceBadge.kt`
- Modify: `docs/design/design-system.md`(1.2), `CLAUDE.md`(디자인 규칙), `docs/superpowers/specs/2026-10-10-design-refresh-heydealer.md`(머리말 상태)
- Test: `android/app/src/test/java/com/recordofp/app/ui/theme/ThemeContrastTest.kt`, `android/app/src/test/java/com/recordofp/app/ui/common/TriggerVisualTest.kt` (생성)

**Interfaces:**
- Consumes: `TriggerCatalog.entries`·`byId(id)`·`CatalogEntry.isBrandPreset`, `catalogLabelRes(categoryId)`
- Produces:
  - `ui/theme`: `internal val LightColors: ColorScheme`, `internal val DarkColors: ColorScheme`; 색 값 `BlueTextLight`, `OnSuccessLight`·`OnSuccessDark`, `InverseSurfaceLight`·`Dark`, `InverseOnSurfaceLight`·`Dark`, `InversePrimaryLight`·`Dark`, `InverseSuccessLight`·`Dark`; `@Composable fun onSuccessColor(): Color`, `@Composable fun inverseSuccessColor(): Color`; `data class TileColors(val tint: Color, val ink: Color)`; `object CategoryPalette { fun of(categoryId: String, dark: Boolean): TileColors? }`; `@Composable fun categoryTileColors(categoryId: String): TileColors?`; `object Spacing { xxs, xs, s, m, l, xl, xxl, screen }`(Dp); `object Motion { const val SHORT_MS: Int; const val STANDARD_MS: Int; val easing: Easing }`; `object AppTextStyles { val numberLarge: TextStyle; val emptyTitle: TextStyle }`; `annotation class LightDarkPreviews`
  - `ui/common`: `sealed interface TriggerVisual` — `Category(categoryId: String)`, `BrandPreset(categoryId: String)`, `BrandKeyword(keyword: String)`, `Place(name: String)`; `fun categoryVisual(categoryId: String): TriggerVisual`; `fun TriggerSpec.visual(): TriggerVisual`; `@DrawableRes fun TriggerVisual.iconRes(): Int`; `@Composable fun TriggerVisual.label(): String`; `@Composable fun TriggerVisual.tileColors(): TileColors`

- [ ] **Step 1: 실패 테스트를 쓴다**

`android/app/src/test/java/com/recordofp/app/ui/theme/ThemeContrastTest.kt`:

```kotlin
package com.recordofp.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.recordofp.app.domain.model.TriggerCatalog
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 개편안 2 §1.1·§1.3의 대비 약속을 지킨다 — WCAG 글자 4.5:1, 테두리·아이콘 3:1 */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb)
    }

    private fun assertContrast(name: String, fg: Color, bg: Color, min: Double) {
        val c = contrast(fg, bg)
        assertTrue("$name 대비 ${"%.2f".format(c)} < $min", c >= min)
    }

    @Test
    fun `보조 글자·연블루 위 블루·오류는 쓰이는 면 위에서 글자 대비 기준을 넘는다`() {
        for ((theme, s) in listOf("라이트" to LightColors, "다크" to DarkColors)) {
            assertContrast("$theme 보조 글자/종이", s.onSurfaceVariant, s.background, 4.5)
            assertContrast("$theme 보조 글자/칩", s.onSurfaceVariant, s.secondaryContainer, 4.5)
            assertContrast("$theme 보조 글자/카드", s.onSurfaceVariant, s.surface, 4.5)
            assertContrast("$theme 블루 글자/연블루", s.onPrimaryContainer, s.primaryContainer, 4.5)
            assertContrast("$theme 블루 글자/카드", s.onPrimaryContainer, s.surface, 4.5)
            assertContrast("$theme 블루 글자/종이", s.onPrimaryContainer, s.background, 4.5)
            assertContrast("$theme 오류/카드", s.error, s.surface, 4.5)
            assertContrast("$theme 오류 면 글자", s.onErrorContainer, s.errorContainer, 4.5)
            assertContrast("$theme 경고 글자/경고 면", s.onTertiaryContainer, s.tertiaryContainer, 4.5)
            assertContrast("$theme 경고 버튼 글자", s.surface, s.onTertiaryContainer, 4.5)
            assertContrast("$theme 개수 pill", s.background, s.onSurface, 4.5)
        }
        assertContrast("라이트 흰 글자/오류", Color.White, LightColors.error, 4.5)
    }

    @Test
    fun `입력 테두리는 카드 위에서 3대1을 넘는다`() {
        assertContrast("라이트 테두리", LightColors.outline, LightColors.surface, 3.0)
        assertContrast("다크 테두리", DarkColors.outline, DarkColors.surface, 3.0)
    }

    @Test
    fun `완료 면과 스낵바의 글자는 글자 대비 기준을 넘는다`() {
        assertContrast("라이트 완료", OnSuccessLight, SuccessLight, 4.5)
        assertContrast("다크 완료", OnSuccessDark, SuccessDark, 4.5)
        for ((theme, s) in listOf("라이트" to LightColors, "다크" to DarkColors)) {
            assertContrast("$theme 스낵바 글자", s.inverseOnSurface, s.inverseSurface, 4.5)
            assertContrast("$theme 스낵바 실행 취소", s.inversePrimary, s.inverseSurface, 4.5)
        }
        assertContrast("라이트 스낵바 체크 원", InverseSuccessLight, InverseSurfaceLight, 3.0)
        assertContrast("다크 스낵바 체크 원", InverseSuccessDark, InverseSurfaceDark, 3.0)
    }

    @Test
    fun `브랜드 프리셋을 뺀 카탈로그 카테고리마다 라이트·다크 타일 색이 있다`() {
        for (entry in TriggerCatalog.entries) {
            if (entry.isBrandPreset) {
                assertNull(entry.id, CategoryPalette.of(entry.id, dark = false))
            } else {
                assertNotNull(entry.id, CategoryPalette.of(entry.id, dark = false))
                assertNotNull(entry.id, CategoryPalette.of(entry.id, dark = true))
            }
        }
    }

    @Test
    fun `카테고리 ink는 tint 위에서 라이트 4·5대1·다크 6대1을, 카드 위에서 4·5대1을 넘는다`() {
        for (entry in TriggerCatalog.entries.filterNot { it.isBrandPreset }) {
            val light = CategoryPalette.of(entry.id, dark = false)!!
            val dark = CategoryPalette.of(entry.id, dark = true)!!
            assertContrast("${entry.id} 라이트 타일", light.ink, light.tint, 4.5)
            assertContrast("${entry.id} 다크 타일", dark.ink, dark.tint, 6.0)
            // 에디터의 선택 안 된 칩은 카드 위에 카테고리 ink 아이콘을 그린다
            assertContrast("${entry.id} 라이트 카드", light.ink, LightColors.surface, 4.5)
            assertContrast("${entry.id} 다크 카드", dark.ink, DarkColors.surface, 4.5)
        }
    }
}
```

(JVM 테스트 이름에는 `.`을 쓸 수 없어 "4.5"를 "4·5"로 적었다. Compose의 `Color`와 `luminance()`는 JVM 단위 테스트에서 그대로 동작한다 — 계획 작성 때 확인했다.)

`android/app/src/test/java/com/recordofp/app/ui/common/TriggerVisualTest.kt`:

```kotlin
package com.recordofp.app.ui.common

import com.recordofp.app.R
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import com.recordofp.app.ui.theme.CategoryPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TriggerVisualTest {

    @Test
    fun `카탈로그 카테고리는 Category, 브랜드 프리셋은 BrandPreset이 된다`() {
        for (entry in TriggerCatalog.entries) {
            val visual = TriggerSpec(type = TriggerType.CATEGORY, categoryId = entry.id).visual()
            val expected = if (entry.isBrandPreset) {
                TriggerVisual.BrandPreset(entry.id)
            } else {
                TriggerVisual.Category(entry.id)
            }
            assertEquals(expected, visual)
        }
    }

    @Test
    fun `카테고리마다 아이콘이 다르고 브랜드·지점은 공용 아이콘을 쓴다`() {
        val categoryIcons = TriggerCatalog.entries.filterNot { it.isBrandPreset }
            .map { TriggerVisual.Category(it.id).iconRes() }
        assertEquals(categoryIcons.size, categoryIcons.toSet().size)
        assertEquals(R.drawable.ic_trigger_brand, TriggerVisual.BrandPreset("daiso").iconRes())
        assertEquals(R.drawable.ic_trigger_search, TriggerVisual.BrandKeyword("GS25").iconRes())
        assertEquals(R.drawable.ic_trigger_place, TriggerVisual.Place("크린토피아 역삼점").iconRes())
    }

    @Test
    fun `카탈로그에서 빠진 옛 카테고리 id도 그릴 수 있다 - 고유 색 없이 검색 아이콘`() {
        val visual = TriggerSpec(type = TriggerType.CATEGORY, categoryId = "removed_cat").visual()
        assertEquals(TriggerVisual.Category("removed_cat"), visual)
        assertEquals(R.drawable.ic_trigger_search, visual.iconRes())
        assertNull(CategoryPalette.of("removed_cat", dark = false))
    }

    @Test
    fun `값이 빠진 브랜드·지점 트리거도 빈 이름으로 그린다`() {
        assertEquals(TriggerVisual.BrandKeyword(""), TriggerSpec(type = TriggerType.BRAND).visual())
        assertEquals(TriggerVisual.Place(""), TriggerSpec(type = TriggerType.PLACE).visual())
        assertEquals(TriggerVisual.Category(""), TriggerSpec(type = TriggerType.CATEGORY).visual())
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.ThemeContrastTest" --tests "*.TriggerVisualTest"`
Expected: 컴파일 실패 — `LightColors`가 private이고, `OnSuccessLight`·`CategoryPalette`·`TriggerVisual`·`R.drawable.ic_trigger_brand`가 없다.

- [ ] **Step 3: 색 토큰을 바꾼다**

`ui/theme/Color.kt` 전체를 바꾼다.

```kotlin
package com.recordofp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 고정 브랜드 팔레트 (디자인 시스템 §2.1).
 * 흰 종이(background) 위의 잉크(onSurface) — 블루는 "행동"에만 아껴 쓴다.
 * 다이나믹 컬러는 쓰지 않는다: 기록 앱의 종이는 기기 벽지를 따라가지 않는다.
 * 라이트의 보조 글자·연블루 위 블루·오류·테두리·성공은 WCAG 대비에 맞춰 보정했다(개편안 2 §1.1) — ThemeContrastTest가 지킨다.
 */

// ── 라이트 ──────────────────────────────────────────────
val PaperLight = Color(0xFFF7F8FA) // background: 종이
val CardLight = Color(0xFFFFFFFF) // surface: 카드
val InkLight = Color(0xFF191F28) // onSurface: 잉크
val SubInkLight = Color(0xFF636E7C) // onSurfaceVariant: 보조
val BlueLight = Color(0xFF1B6EF3) // primary: 행동
val BlueContainerLight = Color(0xFFEAF1FE) // primaryContainer: 연블루
val BlueTextLight = Color(0xFF1762D8) // onPrimaryContainer: 연블루·카드 위 블루 글자(거리 숫자, 저장·추가)
val ChipLight = Color(0xFFF2F4F6) // secondaryContainer: 회색 pill 칩
val ChipInkLight = Color(0xFF4E5968) // onSecondaryContainer
val AmberContainerLight = Color(0xFFFFF4E0) // tertiaryContainer: 보호 배너(경고≠오류)
val AmberInkLight = Color(0xFF96660A) // onTertiaryContainer
val ErrorLight = Color(0xFFDC2E3C)
val LineLight = Color(0xFFE5E8EB) // outlineVariant: 헤어라인
val OutlineLight = Color(0xFF878F9B) // outline: 입력 필드 외곽선(카드 위 3:1)
val SuccessLight = Color(0xFF0A8049) // 완료 스와이프 바탕
val OnSuccessLight = Color(0xFFFFFFFF)
val InverseSurfaceLight = Color(0xFF191F28) // 스낵바 바탕
val InverseOnSurfaceLight = Color(0xFFF7F8FA)
val InversePrimaryLight = Color(0xFF9EC1FF) // 스낵바 [실행 취소]
val InverseSuccessLight = Color(0xFF2ED07E) // 스낵바 앞 성공 체크 원

// ── 다크 ────────────────────────────────────────────────
val PaperDark = Color(0xFF101418)
val CardDark = Color(0xFF1B2027)
val InkDark = Color(0xFFE9EDF2)
val SubInkDark = Color(0xFF8B95A1)
val BlueDark = Color(0xFF5B95F8) // 채도 낮춘 블루 — 어둠 속 눈부심 방지. onPrimaryContainer도 이 값
val BlueContainerDark = Color(0xFF1E2C42)
val ChipDark = Color(0xFF242B34)
val ChipInkDark = Color(0xFFB0B8C1)
val AmberContainerDark = Color(0xFF332916)
val AmberInkDark = Color(0xFFF0C070)
val ErrorDark = Color(0xFFFF6B6B)
val LineDark = Color(0xFF232A33)
val OutlineDark = Color(0xFF646E7C)
val SuccessDark = Color(0xFF2ED07E)
val OnSuccessDark = Color(0xFF1B2027)
val InverseSurfaceDark = Color(0xFFE9EDF2)
val InverseOnSurfaceDark = Color(0xFF101418)
val InversePrimaryDark = Color(0xFF1762D8)
val InverseSuccessDark = Color(0xFF0A8049)

// Material 스킴에 없는 시맨틱 컬러는 테마 헬퍼로 제공한다

/** 완료 스와이프 바탕, 진단 APPLIED·PASS 도트 */
@Composable
fun successColor(): Color = if (isSystemInDarkTheme()) SuccessDark else SuccessLight

/** 성공 면 위의 글자·아이콘 — 완료 스와이프의 체크·"완료" */
@Composable
fun onSuccessColor(): Color = if (isSystemInDarkTheme()) OnSuccessDark else OnSuccessLight

/** 스낵바(inverseSurface) 위의 성공 체크 원 */
@Composable
fun inverseSuccessColor(): Color = if (isSystemInDarkTheme()) InverseSuccessDark else InverseSuccessLight
```

`ui/theme/Theme.kt`를 고친다.

1. 첫 KDoc을 바꾼다.

```kotlin
/**
 * 고정 브랜드 테마 (디자인 시스템 §2).
 * 다이나믹 컬러를 제거하고 라이트/다크 모두 고정 팔레트를 쓴다 — 콘텐츠가 주인공,
 * 크롬은 물러난다. 토큰 값은 Color.kt·CategoryColors.kt, 타입은 Type.kt, 형태는 Shape.kt, 간격·모션은 Spacing.kt·Motion.kt.
 * 스킴은 ThemeContrastTest가 대비를 확인하도록 internal로 둔다.
 */
```

2. `private val LightColors`를 `internal val LightColors`로, `private val DarkColors`를 `internal val DarkColors`로 바꾼다.
3. `LightColors`의 `onPrimaryContainer = BlueLight,`를 `onPrimaryContainer = BlueTextLight,`로 바꾼다. `DarkColors`의 `onPrimaryContainer = BlueDark`는 그대로 둔다(다크는 이미 기준을 넘는다).
4. `LightColors`의 마지막 `outlineVariant = LineLight,` 다음에 더한다.

```kotlin
    // 스낵바 (개편안 2 §1.2)
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight,
```

5. `DarkColors`의 마지막 `outlineVariant = LineDark,` 다음에 더한다.

```kotlin
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
```

- [ ] **Step 4: 카테고리 색, 간격, 모션, 글자 추가 스타일, 미리보기 묶음을 더한다**

`ui/theme/CategoryColors.kt`:

```kotlin
package com.recordofp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 타일 한 쌍 — 바탕(tint)과 그 위의 아이콘·글자(ink) */
@Immutable
data class TileColors(val tint: Color, val ink: Color)

/**
 * 카테고리 10색 (디자인 시스템 §2.6, 개편안 2 §1.3). Material 슬롯이 아니라서 카탈로그 id로 찾는다.
 * 도메인 카탈로그(TriggerCatalog)에는 색을 넣지 않는다. 색은 아이콘·이름과 함께 쓴다 — 색만으로 구분하지 않는다.
 * ink는 tint 위에서 라이트 4.5:1, 다크 6:1을 넘는다 — ThemeContrastTest가 지킨다.
 */
object CategoryPalette {
    private val light: Map<String, TileColors> = mapOf(
        "convenience" to TileColors(Color(0xFFE5F6EC), Color(0xFF137444)),
        "mart" to TileColors(Color(0xFFFFF0E2), Color(0xFFB4520A)),
        "pharmacy" to TileColors(Color(0xFFFCEAF3), Color(0xFFB42A72)),
        "bank" to TileColors(Color(0xFFECEEFC), Color(0xFF3A49B8)),
        "post" to TileColors(Color(0xFFFDECE7), Color(0xFFB83A18)),
        "fuel" to TileColors(Color(0xFFE2F4F4), Color(0xFF0C7276)),
        "laundry" to TileColors(Color(0xFFF1EAFD), Color(0xFF6A3CBC)),
        "cafe" to TileColors(Color(0xFFF4EDE6), Color(0xFF835532)),
        "hospital" to TileColors(Color(0xFFE3F2F9), Color(0xFF0B6A8F)),
        "subway" to TileColors(Color(0xFFEDF5DE), Color(0xFF4F7212)),
    )

    private val dark: Map<String, TileColors> = mapOf(
        "convenience" to TileColors(Color(0xFF163024), Color(0xFF5CCB8C)),
        "mart" to TileColors(Color(0xFF3A2614), Color(0xFFFF9F57)),
        "pharmacy" to TileColors(Color(0xFF3A1A2D), Color(0xFFF27DBB)),
        "bank" to TileColors(Color(0xFF1F2444), Color(0xFF9AA5F7)),
        "post" to TileColors(Color(0xFF3B1F17), Color(0xFFFF8C69)),
        "fuel" to TileColors(Color(0xFF123335), Color(0xFF52C7CC)),
        "laundry" to TileColors(Color(0xFF2A1F42), Color(0xFFB99AF7)),
        "cafe" to TileColors(Color(0xFF2F251D), Color(0xFFD9A67E)),
        "hospital" to TileColors(Color(0xFF12303D), Color(0xFF5CC0E6)),
        "subway" to TileColors(Color(0xFF243016), Color(0xFFB0D66A)),
    )

    /** 고유 색이 없으면(브랜드 프리셋, 카탈로그에 없는 id) null — 호출부가 칩 색을 쓴다 */
    fun of(categoryId: String, dark: Boolean): TileColors? = (if (dark) this.dark else light)[categoryId]
}

/** 지금 테마(라이트·다크)의 카테고리 타일 색 */
@Composable
fun categoryTileColors(categoryId: String): TileColors? = CategoryPalette.of(categoryId, isSystemInDarkTheme())
```

`ui/theme/Spacing.kt`:

```kotlin
package com.recordofp.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 간격 단계 (디자인 시스템 §2.4, 개편안 2 §1.4). 화면 코드는 이 단계에 있는 값을 토큰으로 쓴다.
 * 카드 사이 10, 칩 사이 6, 카드 안쪽 14처럼 단계 밖의 값은 그 컴포넌트 안에 둔다.
 */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** 화면 좌우 여백 */
    val screen = l
}
```

`ui/theme/Motion.kt`:

```kotlin
package com.recordofp.app.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/** 모션 (디자인 시스템 §2.5, 개편안 2 §1.4) */
object Motion {
    /** 짧은 전환 — 칩 선택, 버튼 눌림 */
    const val SHORT_MS = 150

    /** 표준 — 카드 등장·제거, 스낵바 */
    const val STANDARD_MS = 250

    /** Material 표준 이징 */
    val easing: Easing = FastOutSlowInEasing
}
```

`ui/theme/Previews.kt`:

```kotlin
package com.recordofp.app.ui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * 화면 미리보기 라이트·다크 두 벌 (디자인 시스템 §5). 미리보기 안에서는 `RecordOfPTheme { }`를 기본값으로 감싼다 —
 * 기본값이 uiMode를 따르므로 successColor() 같은 헬퍼와 스킴이 같은 테마를 본다.
 */
@Preview(name = "라이트", showBackground = true)
@Preview(name = "다크", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
annotation class LightDarkPreviews
```

`ui/theme/Type.kt` 끝에 더한다(같은 파일의 `private fun style(...)`을 쓴다).

```kotlin

/**
 * Material 슬롯 밖의 글자 스타일 (디자인 시스템 §2.2 추가 스타일, 개편안 2 §2).
 * 굵기만 바꿀 때는 슬롯 스타일의 copy(fontWeight = …)를 쓴다 — 크기를 새로 만들지 않는다.
 */
object AppTextStyles {
    /** 판단에 쓰는 숫자 — 주변 보기 거리 22/26 Bold, tabular */
    val numberLarge: TextStyle =
        style(22, FontWeight.Bold, 26, letterSpacing = -0.02).copy(fontFeatureSettings = "tnum")

    /** 빈 상태 제목 20/28 Bold */
    val emptyTitle: TextStyle = style(20, FontWeight.Bold, 28, letterSpacing = -0.01)
}
```

- [ ] **Step 5: 아이콘을 가져온다**

`tools/design/material_symbols.py`:

```python
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
```

Run(저장소 루트): `python3 -I tools/design/material_symbols.py`
Expected: `ic_cat_convenience.xml ← storefront`부터 `ic_trigger_place.xml ← location_on`까지 13줄. `android/app/src/main/res/drawable/`에 파일 13개가 생긴다. Material Symbols SVG는 `viewBox="0 -960 960 960"`이므로 템플릿이 `translateY="960"`으로 옮긴다. 아이콘 색은 쓰는 쪽 `Icon(tint = …)`이 입힌다.

- [ ] **Step 6: 트리거 시각 매핑을 만든다**

`ui/common/TriggerVisual.kt`:

```kotlin
package com.recordofp.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.recordofp.app.R
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import com.recordofp.app.ui.theme.TileColors
import com.recordofp.app.ui.theme.categoryTileColors

/**
 * 트리거를 화면에 그리는 방법 — 타일 색·아이콘·이름 (개편안 2 §1.3·§2·§4).
 * 홈 카드, 에디터 칩, 주변 보기 그룹 머리가 함께 쓴다. 카탈로그의 이모지는 화면에서 쓰지 않는다(§4).
 */
sealed interface TriggerVisual {
    /** 카테고리. 카탈로그에서 빠진 옛 id도 여기로 온다 — 고유 색 없이 검색 아이콘과 id 그대로 그린다 */
    data class Category(val categoryId: String) : TriggerVisual

    /** 카탈로그의 브랜드 프리셋(다이소·올리브영) — 브랜드 색(칩)과 쇼핑백 아이콘 */
    data class BrandPreset(val categoryId: String) : TriggerVisual

    /** 직접 입력한 브랜드 — 입력한 대소문자 그대로 보인다 */
    data class BrandKeyword(val keyword: String) : TriggerVisual

    /** 특정 지점 — 연블루 */
    data class Place(val name: String) : TriggerVisual
}

/** 카탈로그 id → 카테고리 또는 브랜드 프리셋 */
fun categoryVisual(categoryId: String): TriggerVisual =
    if (TriggerCatalog.byId(categoryId)?.isBrandPreset == true) {
        TriggerVisual.BrandPreset(categoryId)
    } else {
        TriggerVisual.Category(categoryId)
    }

fun TriggerSpec.visual(): TriggerVisual = when (type) {
    TriggerType.CATEGORY -> categoryVisual(categoryId.orEmpty())
    TriggerType.BRAND -> TriggerVisual.BrandKeyword(brandKeyword.orEmpty())
    TriggerType.PLACE -> TriggerVisual.Place(placeName.orEmpty())
}

/** Material Symbols Rounded 아이콘 (개편안 2 §4, tools/design/material_symbols.py) */
@DrawableRes
fun TriggerVisual.iconRes(): Int = when (this) {
    is TriggerVisual.Category -> when (categoryId) {
        "convenience" -> R.drawable.ic_cat_convenience
        "mart" -> R.drawable.ic_cat_mart
        "pharmacy" -> R.drawable.ic_cat_pharmacy
        "bank" -> R.drawable.ic_cat_bank
        "post" -> R.drawable.ic_cat_post
        "fuel" -> R.drawable.ic_cat_fuel
        "laundry" -> R.drawable.ic_cat_laundry
        "cafe" -> R.drawable.ic_cat_cafe
        "hospital" -> R.drawable.ic_cat_hospital
        "subway" -> R.drawable.ic_cat_subway
        else -> R.drawable.ic_trigger_search // 카탈로그에서 빠진 옛 id
    }
    is TriggerVisual.BrandPreset -> R.drawable.ic_trigger_brand
    is TriggerVisual.BrandKeyword -> R.drawable.ic_trigger_search
    is TriggerVisual.Place -> R.drawable.ic_trigger_place
}

/** 화면에 보일 이름. 카탈로그에 없는 id는 id 그대로 — 다른 카테고리 이름으로 잘못 보이지 않게 */
@Composable
fun TriggerVisual.label(): String = when (this) {
    is TriggerVisual.Category -> catalogLabel(categoryId)
    is TriggerVisual.BrandPreset -> catalogLabel(categoryId)
    is TriggerVisual.BrandKeyword -> keyword
    is TriggerVisual.Place -> name
}

@Composable
private fun catalogLabel(categoryId: String): String =
    if (TriggerCatalog.byId(categoryId) != null) stringResource(catalogLabelRes(categoryId)) else categoryId

/** 타일 색: 카테고리는 고유 색, 브랜드는 칩 색, 특정 지점은 연블루 (개편안 2 §1.3) */
@Composable
fun TriggerVisual.tileColors(): TileColors {
    val scheme = MaterialTheme.colorScheme
    val neutral = TileColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
    return when (this) {
        is TriggerVisual.Category -> categoryTileColors(categoryId) ?: neutral
        is TriggerVisual.BrandPreset, is TriggerVisual.BrandKeyword -> neutral
        is TriggerVisual.Place -> TileColors(scheme.primaryContainer, scheme.onPrimaryContainer)
    }
}
```

- [ ] **Step 7: 거리 배지 숫자 색을 고친다**

`ui/common/DistanceBadge.kt`에서 KDoc과 숫자 색을 바꾼다. `primary`는 연블루 위에서 4.18:1이라 기준에 못 미친다.

```kotlin
/**
 * 거리 배지 — 연블루 pill + 블루 숫자, tabular-nums (개편안 §2).
 * 숫자는 onPrimaryContainer — 연블루 위 4.5:1 (개편안 2 §1.1). 에디터 장소 검색 결과와 주변 보기 POI 행이 함께 쓴다.
 */
```

`color = MaterialTheme.colorScheme.primary,`를 `color = MaterialTheme.colorScheme.onPrimaryContainer,`로 바꾼다.

- [ ] **Step 8: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.ThemeContrastTest" --tests "*.TriggerVisualTest"` → PASS(9개)
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: 테스트 140개 통과, lint 오류 0·경고 25. 은행 아이콘처럼 경로가 긴 drawable의 `VectorPath` 경고는 템플릿의 `tools:ignore`가 막는다. 경고가 26이면 템플릿이 그대로인지 본다.

- [ ] **Step 9: 디자인 시스템 1.2, CLAUDE.md, 개편안 상태를 고친다**

토큰을 바꾼 커밋에서 디자인 시스템 문서를 함께 고친다(CLAUDE.md 디자인 규칙). `docs/design/design-system.md`:

1. 머리말 표를 바꾼다(날짜는 커밋하는 날짜).

```markdown
| 상태 | 승인 — 현재 UI의 기준 |
| 버전 | 1.2 |
| 최종 수정 | <커밋하는 날짜> |
| 구현 | Android `ui/theme/{Color,CategoryColors,Type,Shape,Spacing,Motion,Theme,Previews}.kt`, 아이콘 `res/drawable/ic_cat_*`·`ic_trigger_*` · iOS 미착수 |
| 근거 | 디자인 개편안 `docs/superpowers/specs/2026-09-03-design-refresh-clean-minimal.md`, 개편안 2 `docs/superpowers/specs/2026-10-10-design-refresh-heydealer.md`(결정 기록)과 현재 코드 값 |
```

2. §1.3 첫 문단(`바탕을 해치지 않는 선에서 화면에 성격을 준다. 아래 항목은 방향이고, …`)을 바꾼다.

```text
바탕을 해치지 않는 선에서 화면에 성격을 준다. 실제 모양은 개편안 2(방향 B "카테고리 타일", 2026-10-10 승인)에서 정했다. 카드 왼쪽에 카테고리 색 타일을 두어 목록을 훑을 때 장소 종류가 먼저 보이게 한다.
```

   같은 절의 네 항목 끝에 각각 덧붙인다 — 숫자 강조: `(§2.2 추가 스타일 — 홈 개수 pill, 주변 보기 거리)`, 브랜드 그래픽: `(홈 빈 상태)`, 마이크로 인터랙션: `(§2.5)`, 카테고리 컬러 포인트: `(§2.6)`.

3. §2.1의 표와 그 아래 두 줄을 바꾼다.

```markdown
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
| 오류 | error | `#DC2E3C` | `#FF6B6B` | `Error*` |
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
- 글자는 WCAG 4.5:1, 테두리·아이콘은 3:1을 넘는다. 라이트의 보조 글자·블루 글자·오류·테두리·성공은 이 기준에 맞춰 보정했다(개편안 2 §1.1). `ThemeContrastTest`가 쓰이는 면 위의 대비를 확인한다.
- `primary`는 종이 위 글자로 4.32:1이라 기준에 못 미친다. 블루 면 위 흰 글자(4.59:1)와 아이콘에만 쓰고, 종이·카드·연블루 위의 블루 글자(거리 숫자, 에디터 [저장]·[추가])는 `onPrimaryContainer`를 쓴다.
```

4. §2.2의 `숫자를 세로로 맞춰야 하는 곳(…)은 tabular-nums(…)를 쓴다.` 줄 다음에 더한다.

```markdown

Material 슬롯 밖의 스타일은 `AppTextStyles`(`Type.kt`)에 둔다.

| 이름 | 크기/행간(sp) | 굵기 | 자간(em) | 쓰임 |
|---|---|---|---|---|
| `numberLarge` | 22 / 26 | Bold, tabular | -0.02 | 주변 보기 거리 숫자 |
| `emptyTitle` | 20 / 28 | Bold | -0.01 | 빈 상태 제목 |

굵기만 바꿀 때는 슬롯 스타일의 `copy(fontWeight = …)`를 쓴다(개수 pill·완료 글자 Bold, 칩 Medium·SemiBold). 크기를 새로 만들지 않는다.
```

5. §2.4의 본문(`화면 좌우 여백과 카드 그리드의 기준은 20dp다. …`)을 바꾼다.

```markdown
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

단계 밖의 값(카드 사이 10, 카드 안쪽 14, 칩 패딩 9, 행 좌우 18 등)은 그 컴포넌트 안에 둔다. 온보딩·설정·진단 화면은 다음에 손댈 때 토큰으로 옮긴다.
```

6. §2.5의 본문(`토큰은 아직 없다. …`부터 끝까지)을 바꾼다.

```markdown
`ui/theme/Motion.kt`의 값을 쓴다. 이징은 Material 표준(`FastOutSlowInEasing`)이다.

| 토큰 | 값 | 쓰임 |
|---|---|---|
| `SHORT_MS` | 150ms | 칩 선택 색 전환, 버튼 눌림 |
| `STANDARD_MS` | 250ms | 홈 카드 등장·제거·재배열(`animateItem`) |

스낵바는 Material `SnackbarHost`의 기본 전환을 쓴다. 스와이프 완료는 `SwipeToDismissBox`(시작→끝 한 방향)다.
```

7. §2.5 다음에 두 절을 새로 붙인다.

```markdown
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

화면은 `TriggerVisual.iconRes()`로 고른다. 카탈로그의 이모지(`TriggerCatalog.emoji`)는 화면에서 쓰지 않는다.
```

8. §5 전체를 바꾼다.

```markdown
## 5. 코드 규칙

- 색·글꼴은 `MaterialTheme.colorScheme`·`MaterialTheme.typography`와 이 문서의 헬퍼로만 쓴다: `successColor()`·`onSuccessColor()`·`inverseSuccessColor()`, `TriggerVisual.tileColors()`(카테고리 색), `AppTextStyles`, `PillShape`, `tabularNums()`. 화면 코드에 `Color(0x…)`나 `.sp` 리터럴을 쓰지 않는다.
- 간격은 §2.4의 `Spacing` 단계에 있는 값을 토큰으로 쓴다. 단계 밖의 값은 컴포넌트 안에 둔다.
- 고치거나 새로 만드는 화면은 본체를 상태와 동작만 받는 `XxxContent`로 떼고, `@LightDarkPreviews`(라이트·다크 두 벌)와 큰 글꼴(`fontScale = 2f`) 미리보기를 둔다. 미리보기 안은 `RecordOfPTheme { }`를 기본값으로 감싼다.
- 고정 높이 대신 `heightIn(min = …)`을 쓴다(큰 글꼴). 색은 아이콘·이름과 함께 쓴다 — 색만으로 구분하지 않는다.
- 사용자에게 보이는 문자열은 ko·en 둘 다 넣는다(CLAUDE.md 작업 규칙).
```

9. 끝의 변경 이력 표 맨 위(1.1 행 위)에 한 행을 더한다.

`| 1.2 | <커밋하는 날짜> | §1.3, §2.1, §2.2, §2.4, §2.5, §2.6, §2.7, §5 | 개편안 2 토큰 반영 — 라이트 대비 보정(보조 글자·블루 글자·오류·테두리·성공), 완료·스낵바 색, 카테고리 10색, 글자 추가 스타일, 간격·모션 토큰, Material Symbols 아이콘, 미리보기·간격 규칙 | 개편안 2 §1·§4(2026-10-10 승인), 보강 계획 Task 16 |`

`CLAUDE.md`를 고친다.

- "문서 목록"의 "디자인 개편안 2" 줄 끝 문장 `값은 묶음 D 구현 때 디자인 시스템으로 옮긴다.`를 아래로 바꾼다.

```text
토큰은 디자인 시스템 1.2로 옮겼고, 화면은 묶음 D(`feat/hardening-ui`)에서 바꾼다.
```

- "프로젝트 규칙 → 디자인"에서 `화면 코드에서 색·글꼴은 테마 토큰만 쓴다`로 시작하는 줄 전체를 아래로 바꾼다.

```text
- 화면 코드에서 색·글꼴은 테마 토큰만 쓴다(`Color(0x…)`·`.sp` 리터럴 금지). 간격은 `Spacing` 토큰을 쓰고, 고치는 화면은 본체를 `XxxContent`로 떼어 `@LightDarkPreviews`(라이트·다크)와 큰 글꼴 미리보기를 둔다(디자인 시스템 §5). 아이콘은 `tools/design/material_symbols.py`로 Material Symbols를 가져온다(라이브러리 추가 금지). 두 화면 이상에서 쓰는 컴포넌트는 `ui/common`으로 옮긴다.
```

`docs/superpowers/specs/2026-10-10-design-refresh-heydealer.md` 머리말의 `상태` 칸을 아래로, `최종 수정`을 커밋하는 날짜로 바꾼다(시점 기록이라 본문은 고치지 않는다).

```text
진행 중 — 묶음 D 구현 중(보강 계획 Task 16~20, `feat/hardening-ui`). 실기기 라이트·다크 스크린샷 확인 후 완료
```

- [ ] **Step 10: 커밋한다**

```bash
git add tools/design/material_symbols.py android/app/src/main/res/drawable/ic_cat_*.xml android/app/src/main/res/drawable/ic_trigger_*.xml \
  android/app/src/main/java/com/recordofp/app/ui/theme/ \
  android/app/src/main/java/com/recordofp/app/ui/common/TriggerVisual.kt \
  android/app/src/main/java/com/recordofp/app/ui/common/DistanceBadge.kt \
  android/app/src/test/java/com/recordofp/app/ui/theme/ThemeContrastTest.kt \
  android/app/src/test/java/com/recordofp/app/ui/common/TriggerVisualTest.kt \
  docs/design/design-system.md CLAUDE.md docs/superpowers/specs/2026-10-10-design-refresh-heydealer.md
git commit -m "feat: 디자인 토큰 개편안 2 — 대비 보정·카테고리 색·간격·모션·아이콘" -m "라이트 보조 글자·블루 글자·오류·테두리·성공을 WCAG 대비에 맞추고, 완료·스낵바 색과 카테고리 10색,
간격·모션·글자 추가 스타일, Material Symbols 아이콘과 트리거 시각 매핑을 더한다. 디자인 시스템 1.2. (개편안 2 §1·§4)"
```

---

### Task 12: 에디터 저장 가드 — 입력 중 브랜드 보존·이중 저장 방지·실패 안내

원격 에디터에는 세 가지 문제가 있다. (a) 브랜드 입력란이 화면의 `remember`라서, 입력만 하고 [+]를 누르지 않은 채 저장하면 그 브랜드가 조용히 버려진다. 그 브랜드가 유일한 트리거면 저장 버튼이 꺼진 채로 남는다. (b) 저장을 연타하면 두 번 저장된다. 편집 모드의 upsert는 트랜잭션이 아니어서(`deleteByReminder` → `upsertAll`) 트리거가 중복될 수 있다. 삭제도 마찬가지다. (c) 저장이 실패하면 앱이 죽는다. 이 태스크는 입력 중 브랜드를 상태에 두고 저장할 때 함께 확정한다. 저장·삭제 중에는 다시 누를 수 없게 하고, 실패하면 문구로 알린다. 화면 모양은 Task 17이 개편안 2대로 다시 그린다 — 여기서는 동작에 필요한 만큼만 화면을 고친다. ko·en 문자열 키를 맞춰 보는 테스트도 더한다. 이 묶음의 거의 모든 태스크가 문자열을 더하기 때문이다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/ui/editor/EditorViewModel.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/editor/EditorScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`
- Test: `android/app/src/test/java/com/recordofp/app/ui/editor/EditorViewModelTest.kt`
- Test: `android/app/src/test/java/com/recordofp/app/ui/StringResourcesTest.kt` (생성)

**Interfaces:**
- Consumes: 원격 `EditorViewModel`(편집 모드·`searchJob` 포함)
- Produces: `EditorUiState.brandInput`, `saving`, `saveFailed`, `fun withBrandInputCommitted(): EditorUiState`, `EditorViewModel.onBrandInputChange(v)`, `commitBrandInput()`. `addBrand(keyword)`는 지운다. `StringResourcesTest`(ko·en 키 대조).

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

`저장하면 선택이 트리거 스펙으로 매핑된다`의 `vm.addBrand(" GS25 ")`를 두 줄로 바꾼다.

```kotlin
        vm.onBrandInputChange(" GS25 ")
        vm.commitBrandInput()
```

- [ ] **Step 2: 새 실패 테스트를 쓴다**

`EditorViewModelTest` 끝에 더한다.

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
    fun `공백만 입력한 브랜드는 트리거로 세지 않는다`() = runTest {
        val vm = vm()
        vm.onTitleChange("휴지")
        vm.onBrandInputChange("   ")
        assertTrue(!vm.state.value.canSave)
        vm.commitBrandInput()
        assertEquals(emptyList<String>(), vm.state.value.brandKeywords)
        assertEquals("", vm.state.value.brandInput)
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

`android/app/src/test/java/com/recordofp/app/ui/StringResourcesTest.kt`를 만든다. 지금 키는 이미 같으므로 **이 테스트는 처음부터 통과한다.** 이후 태스크가 문자열을 더하거나 지울 때 한쪽만 고치는 것을 막는 회귀 방지다.

```kotlin
package com.recordofp.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** 사용자에게 보이는 문자열은 ko·en 둘 다 둔다 (CLAUDE.md 작업 규칙, 설계 §8). 단위 테스트는 android/app에서 돈다 */
class StringResourcesTest {

    private fun keys(path: String): Set<String> =
        Regex("""<(string|plurals) name="([^"]+)"""")
            .findAll(File(path).readText())
            .map { it.groupValues[2] }
            .toSet()

    @Test
    fun `ko와 en의 문자열 키가 같다`() {
        val ko = keys("src/main/res/values/strings.xml")
        val en = keys("src/main/res/values-en/strings.xml")
        assertEquals("en에 없는 키", emptySet<String>(), ko - en)
        assertEquals("ko에 없는 키", emptySet<String>(), en - ko)
    }
}
```

- [ ] **Step 3: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.EditorViewModelTest" --tests "*.StringResourcesTest"`
Expected: 테스트 소스 컴파일 실패 — `onBrandInputChange`, `commitBrandInput`, `saveFailed`가 없다.

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

하단 삭제 `TextButton`에 `enabled = !state.saving,`을 더한다. (이 오류 문구와 입력 줄은 Task 17이 개편안 2 모양으로 다시 그린다.)

- [ ] **Step 6: 문자열을 더한다**

`editor_delete_confirm` 다음 줄에 넣는다.

ko: `<string name="editor_save_failed">변경 내용을 저장하지 못했어요. 다시 시도해 주세요.</string>`
en: `<string name="editor_save_failed">Couldn\'t save your changes. Please try again.</string>`

- [ ] **Step 7: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.EditorViewModelTest" --tests "*.StringResourcesTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → 테스트 146개 통과, lint 오류 0·경고 25

- [ ] **Step 8: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/ui/editor/EditorViewModel.kt \
  android/app/src/main/java/com/recordofp/app/ui/editor/EditorScreen.kt \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml \
  android/app/src/test/java/com/recordofp/app/ui/editor/EditorViewModelTest.kt \
  android/app/src/test/java/com/recordofp/app/ui/StringResourcesTest.kt
git commit -m "fix: 에디터 저장 가드 - 입력 중 브랜드 보존·연타 방지·실패 안내" -m "확정하지 않은 브랜드도 저장되고, 저장·삭제를 연타해도 한 번만 처리되며, 실패하면 앱이 죽지 않고 문구로 알린다.
ko·en 문자열 키를 맞춰 보는 테스트를 더한다. (§4.1, §8, 최종 리뷰 I6)"
```

---

### Task 15: UI 마감 — 뒤로 버튼 설명, 키보드 가림, 뒤로 연타 가드, 정책 기본값 단일화

> 실행 순서 3번(Task 12 다음). Task 17·19가 화면을 다시 그릴 때 여기서 넣은 `BackButton`과 `imePadding`을 그대로 들고 간다.

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
- Produces: `@Composable fun BackButton(onClick: () -> Unit)`(ui/common), `private fun NavController.popFrom(route: String)`

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

`SettingsStore.kt`의 데이터 클래스와 KDoc을 바꾼다(import `com.recordofp.app.domain.engine.EngineParams`, `java.time.Duration`).

```kotlin
/**
 * 알림 정책 설정값 (스펙 §4.5) — §6.5 필터 체인 정책으로는 [com.recordofp.app.data.engine.StoreGatePolicyProvider]가 변환한다.
 * 기본값은 EngineParams에서만 가져온다 — 리터럴 중복 금지 (§10.2, 최종 리뷰 M1)
 */
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

`DataStoreSettingsStore`의 `policy`와 `updatePolicy`(두 군데 리터럴 읽기)를 하나의 `read`로 합친다.

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

`NearbyScreen.kt`, `DiagnosticsScreen.kt`, `SettingsScreen.kt`에서 아래 블록을 `navigationIcon = { BackButton(onClick = onBack) },` 한 줄로 바꾸고 import `com.recordofp.app.ui.common.BackButton`을 더한다.

```kotlin
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
```

세 파일 모두에서 `import androidx.compose.material.icons.automirrored.filled.ArrowBack`을 지운다. `SettingsScreen.kt`는 `IconButton`을 더 쓰지 않으므로 `import androidx.compose.material3.IconButton`도 지운다(`NearbyScreen`·`DiagnosticsScreen`은 새로고침·내보내기 버튼에서 계속 쓴다).

`action_refresh` 다음 줄에 문자열을 더한다. ko `<string name="action_back">뒤로</string>`, en `<string name="action_back">Back</string>`.

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
Expected: 테스트 147개 통과, lint 오류 0·경고 25, 빌드 성공

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

### Task 17: 에디터 시각 — 카드 바탕 입력, 카테고리 색 칩, 브랜드 [추가]·안내, 저장 실패 면

개편안 2 §2 에디터를 그린다. 입력 필드는 카드 바탕에 1dp 테두리(포커스 2dp 블루), 섹션 제목은 15 SemiBold 잉크다. 카테고리 칩은 선택 안 됨이면 카드 바탕에 카테고리 색 아이콘, 선택이면 카테고리 tint 바탕과 ink 테두리에 체크다. 브랜드는 [추가] 버튼과 "추가를 누르지 않아도 저장할 때 함께 추가돼요" 안내를 둔다. 고른 브랜드는 회색 칩, 지점은 연블루 칩이다. 저장 실패는 본문 맨 위 오류 면으로 알리고, 삭제는 아래 가운데 빨간 텍스트 버튼이다. 동작은 바꾸지 않는다(Task 12의 테스트가 그대로 통과해야 한다). 화면 본체를 `EditorContent`로 떼고 미리보기를 둔다.

**Files:**
- Modify: `tools/design/material_symbols.py`(아이콘 한 줄), Create(스크립트): `android/app/src/main/res/drawable/ic_alert_error.xml`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/editor/EditorViewModel.kt`(`EditorActions`)
- Modify(전체 교체): `android/app/src/main/java/com/recordofp/app/ui/editor/EditorScreen.kt`
- Create: `android/app/src/main/java/com/recordofp/app/ui/editor/EditorComponents.kt`, `EditorPreviews.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`

**Interfaces:**
- Consumes: Task 16의 `TriggerVisual`·`categoryVisual`·`iconRes()`·`label()`·`tileColors()`, `Spacing`, `Motion`, `PillShape`, `LightDarkPreviews`; Task 12의 `EditorUiState.brandInput`·`saving`·`saveFailed`, `onBrandInputChange`·`commitBrandInput`; Task 15의 `consumeWindowInsets`·`imePadding` 본문
- Produces: `interface EditorActions`(뷰모델이 구현), `@Composable fun EditorContent(state: EditorUiState, actions: EditorActions, onClose: () -> Unit)`; `EditorComponents.kt`의 `internal` 컴포넌트 `EditorSection`, `EditorField`, `CategoryChip`, `RemovableChip`, `AddBrandButton`, `SearchPlaceButton`, `SaveFailedNotice`, `PlaceResultCard`

- [ ] **Step 1: 기준선을 확인한다**

이 태스크는 동작을 바꾸지 않는 화면 작업이라 새 단위 테스트가 없다. 시작 전에 기준선을 확인한다.

Run: `./gradlew testDebugUnitTest --tests "*.EditorViewModelTest"` → PASS

- [ ] **Step 2: 뷰모델이 화면 동작 인터페이스를 구현하게 한다**

`EditorViewModel.kt`의 `@HiltViewModel` 바로 위에 인터페이스를 더한다.

```kotlin
/** 에디터 화면이 부르는 동작 — 뷰모델이 구현하고, 미리보기는 빈 구현을 넘긴다 (디자인 시스템 §5) */
interface EditorActions {
    fun onTitleChange(v: String)
    fun onMemoChange(v: String)
    fun toggleCategory(id: String)
    fun onBrandInputChange(v: String)
    fun commitBrandInput()
    fun removeBrand(keyword: String)
    fun onPlaceQueryChange(v: String)
    fun searchPlace()
    fun pickPlace(place: PickedPlace)
    fun clearPlace()
    fun save()
    fun delete()
}
```

클래스 선언의 `) : ViewModel() {`를 `) : ViewModel(), EditorActions {`로 바꾸고, 위 12개 함수의 선언 앞에 `override`를 붙인다(`fun onTitleChange(v: String) = …` → `override fun onTitleChange(v: String) = …`, `fun save() {` → `override fun save() {` 등). 본문은 바꾸지 않는다.

- [ ] **Step 3: 오류 아이콘을 가져온다**

`tools/design/material_symbols.py`의 `ICONS`에서 `"ic_trigger_place": "location_on",` 다음 줄에 넣는다.

```python
    "ic_alert_error": "error",
```

Run(저장소 루트): `python3 -I tools/design/material_symbols.py` → 마지막 줄 `ic_alert_error.xml ← error`. 이미 있던 13개 파일은 같은 내용으로 다시 써진다(`git status`에 바뀐 것으로 나오지 않는다).

- [ ] **Step 4: 문자열을 바꾼다**

`editor_brand_example` 다음 줄에 넣는다.

ko:

```xml
    <string name="editor_brand_add">추가</string>
    <string name="editor_brand_pending_hint">추가를 누르지 않아도 저장할 때 함께 추가돼요</string>
```

en:

```xml
    <string name="editor_brand_add">Add</string>
    <string name="editor_brand_pending_hint">Saved with the note even if you don\'t tap Add</string>
```

ko·en 양쪽에서 `action_add_brand` 줄을 지운다(+ 아이콘 버튼의 설명이었고, [추가] 글자 버튼으로 바뀌어 더 쓰지 않는다 — 남기면 lint `UnusedResources` 경고가 는다).

- [ ] **Step 5: 에디터 컴포넌트를 만든다**

`ui/editor/EditorComponents.kt`:

```kotlin
package com.recordofp.app.ui.editor

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recordofp.app.R
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.ui.common.DistanceBadge
import com.recordofp.app.ui.common.TriggerVisual
import com.recordofp.app.ui.common.iconRes
import com.recordofp.app.ui.common.label
import com.recordofp.app.ui.common.tileColors
import com.recordofp.app.ui.theme.Motion
import com.recordofp.app.ui.theme.PillShape
import com.recordofp.app.ui.theme.Spacing
import kotlin.math.roundToInt

// 에디터 전용 컴포넌트 (개편안 2 §2 에디터). 크기 값은 컴포넌트 고유값이라 여기에 둔다.

/** 섹션 — 제목 15 SemiBold 잉크 + 내용. 앞 블록과 24dp 떨어진다(바깥 간격 12 + 여기 12) */
@Composable
internal fun EditorSection(
    @StringRes title: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/**
 * 입력 필드 — 카드 바탕, 1dp 테두리(포커스 2dp 블루), 12dp 라운드.
 * 높이는 Material 텍스트 필드 최소 56dp를 따른다(개편안의 48~52dp보다 크다 — TalkBack 힌트·포커스 처리를 그대로 쓰기 위해).
 */
@Composable
internal fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        textStyle = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = scheme.surface,
            unfocusedContainerColor = scheme.surface,
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline,
            focusedPlaceholderColor = scheme.onSurfaceVariant,
            unfocusedPlaceholderColor = scheme.onSurfaceVariant,
            cursorColor = scheme.primary,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 카테고리 칩 36dp. 선택 안 됨 = 카드 + 1dp 헤어라인 + 카테고리 색 아이콘,
 * 선택 = 카테고리 tint + 1.5dp ink 테두리 + 체크 + ink 글자. 색은 짧은 전환(150ms)으로 바뀐다 (개편안 2 §1.4·§2)
 */
@Composable
internal fun CategoryChip(
    visual: TriggerVisual,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tile = visual.tileColors()
    val scheme = MaterialTheme.colorScheme
    val spec = tween<Color>(Motion.SHORT_MS, easing = Motion.easing)
    val container by animateColorAsState(if (selected) tile.tint else scheme.surface, spec, label = "chipContainer")
    val border by animateColorAsState(if (selected) tile.ink else scheme.outlineVariant, spec, label = "chipBorder")
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                visual.label(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
        },
        leadingIcon = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = tile.ink, modifier = Modifier.size(16.dp))
            } else {
                Icon(painterResource(visual.iconRes()), contentDescription = null, tint = tile.ink, modifier = Modifier.size(16.dp))
            }
        },
        shape = PillShape,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = container,
            labelColor = scheme.onSurface,
            selectedContainerColor = container,
            selectedLabelColor = tile.ink,
        ),
        modifier = Modifier.heightIn(min = 36.dp),
    )
}

/**
 * 고른 브랜드·지점 칩 — 칩 전체를 누르면 뺀다(터치 영역 48dp). 브랜드는 회색 + 검색 아이콘,
 * 지점은 연블루 + 핀 (개편안 2 §2). ✕의 설명("삭제")이 칩 이름과 합쳐져 TalkBack이 "GS25 삭제"로 읽는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemovableChip(visual: TriggerVisual, onRemove: () -> Unit) {
    val tile = visual.tileColors()
    InputChip(
        selected = false,
        onClick = onRemove,
        label = {
            Text(
                visual.label(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (visual is TriggerVisual.Place) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
        },
        leadingIcon = {
            Icon(painterResource(visual.iconRes()), contentDescription = null, tint = tile.ink, modifier = Modifier.size(14.dp))
        },
        trailingIcon = {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_remove),
                tint = tile.ink,
                modifier = Modifier.size(14.dp),
            )
        },
        shape = PillShape,
        border = null,
        colors = InputChipDefaults.inputChipColors(containerColor = tile.tint, labelColor = tile.ink),
    )
}

/** 브랜드 [추가] — 연블루 면 + 블루 글자, 입력란과 같은 높이 */
@Composable
internal fun AddBrandButton(enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = Spacing.m),
        modifier = Modifier.heightIn(min = 56.dp),
    ) {
        Text(stringResource(R.string.editor_brand_add), style = MaterialTheme.typography.labelLarge)
    }
}

/** 장소 검색 버튼 — 회색 정사각, 입력란과 같은 높이 */
@Composable
internal fun SearchPlaceButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.size(56.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_trigger_search),
            contentDescription = stringResource(R.string.editor_place_hint),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 저장·삭제 실패 — 본문 맨 위 오류 면. 나타나면 TalkBack이 읽는다 */
@Composable
internal fun SaveFailedNotice() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(painterResource(R.drawable.ic_alert_error), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.editor_save_failed),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
        }
    }
}

/** 장소 검색 결과 카드 — 이름 + 거리 배지 */
@Composable
internal fun PlaceResultCard(candidate: PoiCandidate, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                candidate.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f, fill = false).padding(end = Spacing.s),
            )
            DistanceBadge(candidate.distanceM.roundToInt())
        }
    }
}
```

- [ ] **Step 6: 에디터 화면을 다시 쓴다**

`ui/editor/EditorScreen.kt` 전체를 바꾼다. 삭제 확인 대화상자는 화면 상태라 `EditorContent` 안에 둔다. 브랜드 입력란의 키보드 [완료]는 [추가]와 같고, 장소 입력란의 [검색]은 검색 버튼과 같다.

```kotlin
package com.recordofp.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.ui.common.TriggerVisual
import com.recordofp.app.ui.common.categoryVisual
import com.recordofp.app.ui.theme.Spacing

/**
 * 제목 입력 → 트리거 선택(카테고리 칩 다중 선택 / 브랜드 입력 / 장소 검색) → 저장.
 * "3탭 + 타이핑 이내" 목표 (설계 §4.1). 모양은 개편안 2 §2 에디터 — 카드 바탕 입력, 카테고리 색 칩, 브랜드 안내, 저장 실패 면.
 */
@Composable
fun EditorScreen(
    onDone: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    EditorContent(state = state, actions = viewModel, onClose = onDone)
}

/** 에디터 본체 — 상태와 동작만 받는다(미리보기는 EditorPreviews.kt) */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorContent(
    state: EditorUiState,
    actions: EditorActions,
    onClose: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.editor_delete)) },
            text = { Text(stringResource(R.string.editor_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; actions.delete() }) {
                    Text(stringResource(android.R.string.ok), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(android.R.string.cancel))
                    }
                },
                title = {
                    Text(stringResource(if (state.editingId != null) R.string.editor_title_edit else R.string.title_editor))
                },
                actions = {
                    // 종이 위 블루 글자는 onPrimaryContainer — primary는 종이 위 4.5:1이 안 된다 (개편안 2 §1.1)
                    TextButton(
                        onClick = actions::save,
                        enabled = state.canSave,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    ) {
                        Text(stringResource(R.string.editor_save), style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // 키보드가 아래쪽 장소 검색란·결과를 가리지 않게 — Scaffold가 이미 준 시스템 바 여백은 빼고 더한다
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // 저장·삭제 실패 — 입력은 그대로 남아 있으니 다시 시도하면 된다 (최종 리뷰 I6)
            if (state.saveFailed) SaveFailedNotice()
            EditorField(
                value = state.title,
                onValueChange = actions::onTitleChange,
                placeholder = stringResource(R.string.editor_title_hint),
                emphasized = true,
            )
            EditorField(
                value = state.memo,
                onValueChange = actions::onMemoChange,
                placeholder = stringResource(R.string.editor_memo_hint),
            )

            EditorSection(R.string.editor_section_category) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    TriggerCatalog.entries.forEach { entry ->
                        CategoryChip(
                            visual = categoryVisual(entry.id),
                            selected = entry.id in state.selectedCategoryIds,
                            onClick = { actions.toggleCategory(entry.id) },
                        )
                    }
                }
            }

            EditorSection(R.string.editor_section_brand) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    EditorField(
                        value = state.brandInput,
                        onValueChange = actions::onBrandInputChange,
                        placeholder = stringResource(R.string.editor_brand_example),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { actions.commitBrandInput() }),
                        modifier = Modifier.weight(1f),
                    )
                    AddBrandButton(enabled = state.brandInput.isNotBlank(), onClick = actions::commitBrandInput)
                }
                if (state.brandInput.isNotBlank()) {
                    // [추가]를 누르지 않아도 저장할 때 확정된다는 것을 입력하는 동안 알려 준다 (개편안 2 §3)
                    Text(
                        stringResource(R.string.editor_brand_pending_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.brandKeywords.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        state.brandKeywords.forEach { keyword ->
                            RemovableChip(
                                visual = TriggerVisual.BrandKeyword(keyword),
                                onRemove = { actions.removeBrand(keyword) },
                            )
                        }
                    }
                }
            }

            EditorSection(R.string.editor_section_place) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    EditorField(
                        value = state.placeQuery,
                        onValueChange = actions::onPlaceQueryChange,
                        placeholder = stringResource(R.string.editor_place_hint),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { actions.searchPlace() }),
                        modifier = Modifier.weight(1f),
                    )
                    SearchPlaceButton(onClick = actions::searchPlace)
                }
                state.placeSearchError?.let { error ->
                    // F4: 원인별 안내 — 401(서비스)을 "네트워크 확인"으로 오인시키지 않는다
                    Text(
                        stringResource(
                            when (error) {
                                PlaceSearchError.NO_LOCATION -> R.string.nearby_no_location
                                PlaceSearchError.NETWORK -> R.string.editor_place_error_network
                                PlaceSearchError.SERVICE -> R.string.editor_place_error_service
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.placeResults.forEach { candidate ->
                    PlaceResultCard(
                        candidate = candidate,
                        onClick = { actions.pickPlace(PickedPlace(candidate.name, candidate.id, candidate.point)) },
                    )
                }
                state.place?.let { place ->
                    RemovableChip(visual = TriggerVisual.Place(place.name), onRemove = actions::clearPlace)
                }
            }

            if (state.editingId != null) {
                // 삭제는 저장과 동선을 분리해 맨 아래 가운데 빨간 텍스트 버튼으로 (개편안 2 §2)
                Box(Modifier.fillMaxWidth().padding(top = Spacing.xs), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !state.saving,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.editor_delete), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 7: 미리보기를 둔다**

`ui/editor/EditorPreviews.kt`:

```kotlin
package com.recordofp.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.ui.theme.LightDarkPreviews
import com.recordofp.app.ui.theme.RecordOfPTheme

// 에디터 미리보기 — 라이트·다크 두 벌과 큰 글꼴 (디자인 시스템 §5, 개편안 2 목업 "에디터")

private object NoopEditorActions : EditorActions {
    override fun onTitleChange(v: String) {}
    override fun onMemoChange(v: String) {}
    override fun toggleCategory(id: String) {}
    override fun onBrandInputChange(v: String) {}
    override fun commitBrandInput() {}
    override fun removeBrand(keyword: String) {}
    override fun onPlaceQueryChange(v: String) {}
    override fun searchPlace() {}
    override fun pickPlace(place: PickedPlace) {}
    override fun clearPlace() {}
    override fun save() {}
    override fun delete() {}
}

private val newRecord = EditorUiState(
    title = "건전지 사기",
    selectedCategoryIds = setOf("convenience"),
    brandKeywords = listOf("이마트24"),
    brandInput = "GS25",
)

private val editFailed = EditorUiState(
    editingId = 7,
    title = "셔츠 맡기기",
    memo = "흰 셔츠 2장",
    selectedCategoryIds = setOf("laundry"),
    place = PickedPlace("크린토피아 역삼점", "k1", GeoPoint(37.5, 127.03)),
    saveFailed = true,
)

@LightDarkPreviews
@Composable
private fun EditorNewPreview() {
    RecordOfPTheme { EditorContent(state = newRecord, actions = NoopEditorActions, onClose = {}) }
}

@LightDarkPreviews
@Composable
private fun EditorEditFailedPreview() {
    RecordOfPTheme { EditorContent(state = editFailed, actions = NoopEditorActions, onClose = {}) }
}

@Preview(name = "큰 글꼴", showBackground = true, fontScale = 2f)
@Composable
private fun EditorLargeFontPreview() {
    RecordOfPTheme { EditorContent(state = newRecord, actions = NoopEditorActions, onClose = {}) }
}
```

- [ ] **Step 8: 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: 테스트 147개 통과(새 테스트 없음, `StringResourcesTest`가 ko·en 키를 맞춰 본다), lint 오류 0·경고 25, 빌드 성공

Android Studio에서 `EditorPreviews.kt`의 미리보기(새 기록·수정 저장 실패 × 라이트·다크, 큰 글꼴)를 열어 개편안 2 목업 "에디터"와 비교한다. 큰 글꼴에서 칩·버튼 글자가 잘리지 않아야 한다.

- [ ] **Step 9: 커밋한다**

```bash
git add tools/design/material_symbols.py android/app/src/main/res/drawable/ic_alert_error.xml \
  android/app/src/main/java/com/recordofp/app/ui/editor/ \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml
git commit -m "feat: 에디터 개편안 2 — 카테고리 색 칩·카드 입력·브랜드 안내·저장 실패 면" -m "입력은 카드 바탕에 테두리, 카테고리 칩은 카테고리 색과 체크, 브랜드는 [추가]와 저장 시 함께 추가된다는 안내,
저장 실패는 본문 위 오류 면으로 알린다. 화면 본체를 EditorContent로 떼고 라이트·다크·큰 글꼴 미리보기를 둔다. (개편안 2 §2·§3, 디자인 시스템 §5)"
```

---

### Task 13: 홈 보호 배너 — 꺼진 것을 이름으로 알리고 고칠 곳으로

> 묶음 B 인계를 여기서 처리한다: 빠른 설정(알림창)에서 기기 위치를 켜면 액티비티가 멈추지 않아 `LifecycleResumeEffect`가 돌지 않는다. 그래서 배너·대시보드와 보호 복구 재배치(F1)가 다음 화면 이동까지 늦어진다. 재개된 동안 `LocationManager.MODE_CHANGED_ACTION`도 받아 스냅샷을 다시 읽는다(홈·설정).

원격 배너는 "알림이 꺼질 수 있는 상태예요 / 설정에서 확인" 한 줄이고, 누르면 앱 안 설정으로 간다. 무엇이 꺼졌는지, '항상 허용'이 왜 필요한지, 어떻게 켜는지 알려 주지 않는다(§4.2 4단계 업셀, §4.3 "왜 알림이 안 오지?"). 이 태스크는 꺼진 것 하나를 우선순위대로 골라 이름으로 알린다(알림 → 정확한 위치 → 항상 허용 → 기기 위치). [설정 열기]는 그것을 고칠 시스템 화면을 바로 연다. 모양은 개편안 2 §2 보호 배너다 — 앰버 면, 36dp 원 아이콘, 제목·본문, '항상 허용'의 단계 칩(설정 → 권한 → 위치 → 항상 허용, 마지막만 강조), 오른쪽 아래 [설정 열기] pill. F1 보고(`reportProtection`)는 그대로 둔다.

**Files:**
- Modify: `tools/design/material_symbols.py`(아이콘 세 줄), Create(스크립트): `android/app/src/main/res/drawable/ic_banner_notifications_off.xml`, `ic_banner_precise.xml`, `ic_banner_location_off.xml`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/permissions/PermissionStatus.kt`
- Create: `android/app/src/main/java/com/recordofp/app/ui/permissions/RememberPermissionSnapshot.kt`
- Create: `android/app/src/main/java/com/recordofp/app/ui/home/ProtectionBanner.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/home/HomeScreen.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`
- Test: `android/app/src/test/java/com/recordofp/app/ui/permissions/PermissionSnapshotTest.kt`

**Interfaces:**
- Consumes: Task 6의 `PermissionSnapshot.locationServicesOn`, `readPermissionSnapshot`, `appNotificationSettingsIntent`, `locationSourceSettingsIntent`, `appDetailsSettingsIntent`, `Context.openSettings`; Task 16의 `Spacing`, `PillShape`, `LightDarkPreviews`, `R.drawable.ic_trigger_place`
- Produces: `enum class ProtectionIssue { NOTIFICATIONS_OFF, PRECISE_LOCATION_OFF, BACKGROUND_LOCATION_OFF, LOCATION_SERVICES_OFF }`, `val PermissionSnapshot.topIssue: ProtectionIssue?`, `@Composable fun rememberPermissionSnapshot(onRead: (PermissionSnapshot) -> Unit = {}): PermissionSnapshot`, `@Composable fun ProtectionBanner(issue: ProtectionIssue, modifier: Modifier = Modifier)`

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

Run: `./gradlew testDebugUnitTest --tests "*.PermissionSnapshotTest"` → PASS

- [ ] **Step 4: 스냅샷을 재개 중에도 다시 읽게 한다 (묶음 B 인계)**

`ui/permissions/RememberPermissionSnapshot.kt`:

```kotlin
package com.recordofp.app.ui.permissions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * 화면이 보이는 동안의 보호 상태 (§4.3). 재개(onResume)할 때 다시 읽고, 재개된 동안 기기 위치 토글이 바뀌어도 다시 읽는다 —
 * 빠른 설정(알림창)에서 위치를 켜면 액티비티가 멈추지 않아 onResume이 돌지 않기 때문이다 (묶음 B 인계).
 * 다시 읽을 때마다 onRead를 부른다 — 보호 복구 전이 보고(F1)에 쓴다.
 */
@Composable
fun rememberPermissionSnapshot(onRead: (PermissionSnapshot) -> Unit = {}): PermissionSnapshot {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(readPermissionSnapshot(context)) }
    val currentOnRead by rememberUpdatedState(onRead)
    LifecycleResumeEffect(context) {
        fun refresh() {
            snapshot = readPermissionSnapshot(context)
            currentOnRead(snapshot)
        }
        refresh()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = refresh()
        }
        // 시스템 방송이라 내보내지 않아도 받는다
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(LocationManager.MODE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onPauseOrDispose { context.unregisterReceiver(receiver) }
    }
    return snapshot
}
```

`SettingsScreen`의 아래 블록을 바꾼다.

```kotlin
    var snapshot by remember { mutableStateOf(readPermissionSnapshot(context)) }
    LifecycleResumeEffect(Unit) { // 시스템 설정에서 돌아오면 갱신
        snapshot = readPermissionSnapshot(context)
        viewModel.reportProtection(snapshot.fullyProtected) // 보호 복구 전이 → 재배치 (F1)
        onPauseOrDispose { }
    }
```

↓

```kotlin
    // 시스템 설정·빠른 설정에서 돌아오면 갱신하고, 보호 복구 전이면 재배치한다 (F1)
    val snapshot = rememberPermissionSnapshot(onRead = { viewModel.reportProtection(it.fullyProtected) })
```

`SettingsScreen.kt` import에서 `androidx.lifecycle.compose.LifecycleResumeEffect`를 지우고, `com.recordofp.app.ui.permissions.readPermissionSnapshot`을 `com.recordofp.app.ui.permissions.rememberPermissionSnapshot`으로 바꾼다(`remember`·`mutableStateOf`·`setValue`는 방해금지 시각 선택에서 계속 쓴다).

- [ ] **Step 5: 배너 아이콘을 가져온다**

`tools/design/material_symbols.py`의 `ICONS`에서 `"ic_alert_error": "error",` 다음 줄에 넣는다. '항상 허용'은 핀 아이콘(`ic_trigger_place`)을 함께 쓴다.

```python
    "ic_banner_notifications_off": "notifications_off",
    "ic_banner_precise": "my_location",
    "ic_banner_location_off": "location_off",
```

Run(저장소 루트): `python3 -I tools/design/material_symbols.py` → 마지막 세 줄이 `ic_banner_notifications_off.xml ← notifications_off`, `ic_banner_precise.xml ← my_location`, `ic_banner_location_off.xml ← location_off`

- [ ] **Step 6: 배너를 만든다**

`ui/home/ProtectionBanner.kt`:

```kotlin
package com.recordofp.app.ui.home

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recordofp.app.R
import com.recordofp.app.ui.permissions.ProtectionIssue
import com.recordofp.app.ui.permissions.appDetailsSettingsIntent
import com.recordofp.app.ui.permissions.appNotificationSettingsIntent
import com.recordofp.app.ui.permissions.locationSourceSettingsIntent
import com.recordofp.app.ui.permissions.openSettings
import com.recordofp.app.ui.theme.LightDarkPreviews
import com.recordofp.app.ui.theme.PillShape
import com.recordofp.app.ui.theme.RecordOfPTheme
import com.recordofp.app.ui.theme.Spacing

/**
 * 근처 알림을 막고 있는 것 하나를 이름으로 알리고 고칠 곳으로 바로 보낸다 (§4.2 4단계 업셀, §4.3, 최종 리뷰 I3).
 * 여럿이면 우선순위가 높은 하나만 — 고치고 돌아오면 다음 것이 보인다. 앰버 면: 경고이지 오류가 아니다.
 * 모양은 개편안 2 §2 보호 배너 — 36dp 원 아이콘, 제목·본문, '항상 허용' 단계 칩, [설정 열기] pill.
 */
@Composable
fun ProtectionBanner(issue: ProtectionIssue, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val hasSteps = issue == ProtectionIssue.BACKGROUND_LOCATION_OFF
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(if (hasSteps) Spacing.s else 10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(36.dp).background(scheme.onTertiaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(issue.iconRes()),
                        contentDescription = null,
                        tint = scheme.tertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(issue.titleRes()), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(issue.bodyRes()), style = MaterialTheme.typography.bodySmall)
                }
            }
            // A11+ "항상 허용"은 시스템 설정에서만 켤 수 있다 — 단계를 적어 준다 (§4.2)
            if (hasSteps) BackgroundLocationSteps()
            Button(
                onClick = { context.openSettings(issue.settingsIntent(context)) },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.onTertiaryContainer,
                    contentColor = scheme.surface,
                ),
                contentPadding = PaddingValues(horizontal = Spacing.m),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.banner_action_open_settings), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 설정 → 권한 → 위치 → 항상 허용. 마지막 칩만 앰버 바탕. TalkBack은 한 줄로 읽는다 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundLocationSteps() {
    val scheme = MaterialTheme.colorScheme
    val steps = listOf(
        R.string.banner_background_step_settings,
        R.string.banner_background_step_permissions,
        R.string.banner_background_step_location,
        R.string.banner_background_step_always,
    )
    FlowRow(
        // 원 아이콘(36) + 간격(12) 만큼 들여 제목과 줄을 맞춘다
        modifier = Modifier.padding(start = 48.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        steps.forEachIndexed { index, res ->
            val last = index == steps.lastIndex
            Surface(
                shape = PillShape,
                color = if (last) scheme.onTertiaryContainer else scheme.surface,
                contentColor = if (last) scheme.surface else scheme.onTertiaryContainer,
            ) {
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (last) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = Spacing.xxs),
                )
            }
            if (!last) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp).align(Alignment.CenterVertically),
                )
            }
        }
    }
}

@DrawableRes
private fun ProtectionIssue.iconRes(): Int = when (this) {
    ProtectionIssue.NOTIFICATIONS_OFF -> R.drawable.ic_banner_notifications_off
    ProtectionIssue.PRECISE_LOCATION_OFF -> R.drawable.ic_banner_precise
    ProtectionIssue.BACKGROUND_LOCATION_OFF -> R.drawable.ic_trigger_place
    ProtectionIssue.LOCATION_SERVICES_OFF -> R.drawable.ic_banner_location_off
}

@StringRes
private fun ProtectionIssue.titleRes(): Int = when (this) {
    ProtectionIssue.NOTIFICATIONS_OFF -> R.string.banner_notifications_title
    ProtectionIssue.PRECISE_LOCATION_OFF -> R.string.banner_precise_title
    ProtectionIssue.BACKGROUND_LOCATION_OFF -> R.string.banner_background_title
    ProtectionIssue.LOCATION_SERVICES_OFF -> R.string.banner_location_off_title
}

@StringRes
private fun ProtectionIssue.bodyRes(): Int = when (this) {
    ProtectionIssue.NOTIFICATIONS_OFF -> R.string.banner_notifications_body
    ProtectionIssue.PRECISE_LOCATION_OFF -> R.string.banner_precise_body
    ProtectionIssue.BACKGROUND_LOCATION_OFF -> R.string.banner_background_body
    ProtectionIssue.LOCATION_SERVICES_OFF -> R.string.banner_location_off_body
}

/** 고칠 시스템 화면 — 없는 제조사 빌드에서는 openSettings가 앱 상세 설정으로 대신 연다 */
private fun ProtectionIssue.settingsIntent(context: Context): Intent = when (this) {
    ProtectionIssue.NOTIFICATIONS_OFF -> appNotificationSettingsIntent(context)
    ProtectionIssue.PRECISE_LOCATION_OFF, ProtectionIssue.BACKGROUND_LOCATION_OFF -> appDetailsSettingsIntent(context)
    ProtectionIssue.LOCATION_SERVICES_OFF -> locationSourceSettingsIntent()
}

@LightDarkPreviews
@Composable
private fun ProtectionBannerPreview() {
    RecordOfPTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ProtectionIssue.entries.forEach { ProtectionBanner(it) }
        }
    }
}

@Preview(name = "큰 글꼴", showBackground = true, fontScale = 2f)
@Composable
private fun ProtectionBannerLargeFontPreview() {
    RecordOfPTheme { ProtectionBanner(ProtectionIssue.BACKGROUND_LOCATION_OFF) }
}
```

- [ ] **Step 7: 홈에 배너를 단다**

`HomeScreen`의 `Scaffold` 내용 첫머리 블록을 바꾼다.

```kotlin
        val context = LocalContext.current
        var snapshot by remember { mutableStateOf(readPermissionSnapshot(context)) }
        LifecycleResumeEffect(Unit) { // 설정에서 돌아오면 갱신
            snapshot = readPermissionSnapshot(context)
            viewModel.reportProtection(snapshot.fullyProtected) // 보호 복구 전이 → 재배치 (F1)
            onPauseOrDispose { }
        }
```

↓

```kotlin
        // 설정·빠른 설정에서 돌아오면 갱신하고, 보호 복구 전이면 재배치한다 (F1)
        val snapshot = rememberPermissionSnapshot(onRead = { viewModel.reportProtection(it.fullyProtected) })
```

`if (!snapshot.fullyProtected) { Card(onClick = onSettingsClick, …) { … } }` 블록 전체를 바꾼다.

```kotlin
            snapshot.topIssue?.let { issue ->
                ProtectionBanner(
                    issue = issue,
                    modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.m, bottom = Spacing.xxs),
                )
            }
```

`HomeScreen.kt` import에서 `androidx.compose.ui.platform.LocalContext`, `androidx.lifecycle.compose.LifecycleResumeEffect`, `com.recordofp.app.ui.permissions.readPermissionSnapshot`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`를 지우고 `com.recordofp.app.ui.permissions.rememberPermissionSnapshot`, `com.recordofp.app.ui.theme.Spacing`을 더한다(`Card`·`CardDefaults`는 기록 카드가 계속 쓴다).

- [ ] **Step 8: 문자열을 바꾼다**

ko·en 양쪽에서 `banner_protection_title`, `banner_protection_action`을 지우고 그 자리에 넣는다.

ko:

```xml
    <string name="banner_notifications_title">알림이 꺼져 있어요</string>
    <string name="banner_notifications_body">근처를 지날 때 알려드리려면 \'근처 알림\'을 포함해 알림을 켜 주세요.</string>
    <string name="banner_precise_title">정확한 위치 권한이 필요해요</string>
    <string name="banner_precise_body">근처 가게를 찾으려면 위치 권한을 허용하고 \'정확한 위치 사용\'을 켜 주세요.</string>
    <string name="banner_background_title">앱을 닫아도 알려드리려면</string>
    <string name="banner_background_body">근처를 지날 때 알려드리려면 위치 권한을 \'항상 허용\'으로 바꿔 주세요.</string>
    <string name="banner_background_step_settings">설정</string>
    <string name="banner_background_step_permissions">권한</string>
    <string name="banner_background_step_location">위치</string>
    <string name="banner_background_step_always">항상 허용</string>
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
    <string name="banner_background_step_settings">Settings</string>
    <string name="banner_background_step_permissions">Permissions</string>
    <string name="banner_background_step_location">Location</string>
    <string name="banner_background_step_always">Allow all the time</string>
    <string name="banner_location_off_title">Location is turned off</string>
    <string name="banner_location_off_body">Turn on your device\'s location so nearby alerts can work.</string>
    <string name="banner_action_open_settings">Open settings</string>
```

- [ ] **Step 9: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.PermissionSnapshotTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → 테스트 150개 통과, lint 오류 0·경고 25

`ProtectionBanner.kt`의 미리보기(네 종류 × 라이트·다크, '항상 허용' 큰 글꼴)를 개편안 2 목업 "보호 배너"와 비교한다.

- [ ] **Step 10: 커밋한다**

```bash
git add tools/design/material_symbols.py android/app/src/main/res/drawable/ic_banner_*.xml \
  android/app/src/main/java/com/recordofp/app/ui/permissions/PermissionStatus.kt \
  android/app/src/main/java/com/recordofp/app/ui/permissions/RememberPermissionSnapshot.kt \
  android/app/src/main/java/com/recordofp/app/ui/home/ProtectionBanner.kt \
  android/app/src/main/java/com/recordofp/app/ui/home/HomeScreen.kt \
  android/app/src/main/java/com/recordofp/app/ui/settings/SettingsScreen.kt \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml \
  android/app/src/test/java/com/recordofp/app/ui/permissions/PermissionSnapshotTest.kt
git commit -m "feat: 홈 보호 배너를 구체적으로 - 항상 허용 업셀과 설정 바로 열기" -m "꺼진 것 하나를 우선순위대로 이름으로 알리고 고칠 시스템 화면을 바로 연다. '항상 허용'에는 단계 칩을 단다.
빠른 설정에서 기기 위치를 바꿔도 화면이 보이는 동안 바로 다시 읽는다. (§4.2, §4.3, 최종 리뷰 I3, 묶음 B 인계, 개편안 2 §2)"
```

---

### Task 14: 완료 실행 취소와 TalkBack 완료

완료를 되돌릴 방법이 없다. 스와이프는 양방향 모두 완료로 처리되어 실수하기 쉽다. 스와이프를 못 하는 TalkBack 사용자는 완료할 수 없다(§8). 이 태스크는 완료한 뒤 [실행 취소] 스낵바를 띄우고, 스와이프는 한 방향만 허용하고, 카드에 TalkBack 사용자 지정 동작 "완료"를 단다. 로컬 구현에서 나온 N1 결함(실행 취소 뒤 행이 스와이프된 상태로 남고, 완료가 두 번 불림)을 막기 위해 두 가지를 함께 넣는다. 행 키를 `id:updatedAt`으로 해서 되살아난 행이 새 스와이프 상태를 받게 하고, 완료는 확정된 상태 변화(`LaunchedEffect`)에서 한 번만 부른다. 모양은 개편안 2 §2다 — 스와이프 뒤는 성공 면에 32dp 원 체크와 "완료", 스낵바는 inverse 면에 성공 체크 원과 [실행 취소]. 떠 있는 동안 FAB은 Scaffold가 스낵바 위로 올린다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/data/repo/ReminderRepository.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/home/HomeViewModel.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/home/HomeScreen.kt`
- Create: `android/app/src/main/java/com/recordofp/app/ui/home/UndoSnackbar.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`
- Test: `RoomReminderRepositoryTest.kt`, `HomeViewModelTest.kt`, 페이크 4곳(`ReseedServiceTest`, `EditorViewModelTest`, `NearbyViewModelTest`, `HomeViewModelTest`)

**Interfaces:**
- Consumes: Task 13의 `HomeScreen`(배너), Task 16의 `successColor()`·`onSuccessColor()`·`inverseSuccessColor()`, `Spacing`, `LightDarkPreviews`
- Produces: `suspend fun ReminderRepository.reactivate(id: Long)`, `HomeViewModel.reactivate(id)`, `@Composable fun UndoSnackbar(message: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier)`

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

나머지 페이크 3곳(`ReseedServiceTest.FakeReminders`, `EditorViewModelTest.FakeRepo`, `NearbyViewModelTest.FakeRepo`)의 `override suspend fun complete(id: Long) {}` 다음 줄에 `override suspend fun reactivate(id: Long) {}`를 더한다.

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

Run: `./gradlew testDebugUnitTest --tests "*.RoomReminderRepositoryTest" --tests "*.HomeViewModelTest"` → PASS

- [ ] **Step 4: 실행 취소 스낵바를 만든다**

`ui/home/UndoSnackbar.kt`:

```kotlin
package com.recordofp.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recordofp.app.ui.theme.LightDarkPreviews
import com.recordofp.app.ui.theme.RecordOfPTheme
import com.recordofp.app.ui.theme.Spacing
import com.recordofp.app.ui.theme.inverseSuccessColor

/**
 * 완료 뒤 [실행 취소] 스낵바 — inverse 면 16dp 라운드, 성공 체크 원, 블루 액션 (개편안 2 §1.2·§2).
 * SnackbarHost 안에서 그린다 — 호스트가 TalkBack 안내(liveRegion)와 접근성 표시 시간을 맡는다.
 */
@Composable
fun UndoSnackbar(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(start = Spacing.m, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp).background(inverseSuccessColor(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.inverseSurface, modifier = Modifier.size(16.dp))
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.inversePrimary),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun UndoSnackbarPreview() {
    RecordOfPTheme { UndoSnackbar(message = "완료했어요", actionLabel = "실행 취소", onAction = {}) }
}
```

- [ ] **Step 5: 홈 화면에 실행 취소와 TalkBack 동작을 넣는다**

`HomeScreen` 함수의 `val items by ...` 다음에 추가한다.

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

`Scaffold(` 안 `containerColor = …` 다음에 추가한다.

```kotlin
        // 떠 있는 동안 FAB은 Scaffold가 스낵바 위로 올린다 (개편안 2 §2)
        snackbarHost = {
            SnackbarHost(snackbarHostState, Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)) { data ->
                UndoSnackbar(
                    message = data.visuals.message,
                    actionLabel = data.visuals.actionLabel.orEmpty(),
                    onAction = data::performAction,
                )
            }
        },
```

`LazyColumn`의 `items`를 바꾼다.

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

`ReminderRow`를 교체하고, 바로 아래에 스와이프 뒤 배경을 더한다(카드 내용은 Task 18이 다시 그린다).

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
        backgroundContent = { CompleteSwipeBackground() },
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

/** 완료 스와이프 뒤 — 성공 면, 32dp 원 체크, "완료" (개편안 2 §2) */
@Composable
private fun CompleteSwipeBackground() {
    val success = successColor()
    val onSuccess = onSuccessColor()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(success)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(onSuccess, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = success, modifier = Modifier.size(20.dp))
        }
        Text(
            stringResource(R.string.action_complete),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = onSuccess,
        )
    }
}
```

import에 `androidx.compose.foundation.layout.size`, `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.material3.SnackbarDuration`, `androidx.compose.material3.SnackbarHost`, `androidx.compose.material3.SnackbarHostState`, `androidx.compose.material3.SnackbarResult`, `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.rememberCoroutineScope`, `androidx.compose.ui.semantics.CustomAccessibilityAction`, `androidx.compose.ui.semantics.customActions`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.text.font.FontWeight`, `com.recordofp.app.ui.theme.onSuccessColor`, `kotlinx.coroutines.launch`를 더한다.

- [ ] **Step 6: 문자열을 더한다**

`action_mute_today` 다음 줄에 넣는다.

ko: `<string name="action_undo">실행 취소</string>`
en: `<string name="action_undo">Undo</string>`

- [ ] **Step 7: 테스트 통과와 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.RoomReminderRepositoryTest" --tests "*.HomeViewModelTest"` → PASS
Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → 테스트 152개 통과, lint 오류 0·경고 25

- [ ] **Step 8: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/data/repo/ReminderRepository.kt \
  android/app/src/main/java/com/recordofp/app/ui/home/HomeViewModel.kt \
  android/app/src/main/java/com/recordofp/app/ui/home/HomeScreen.kt \
  android/app/src/main/java/com/recordofp/app/ui/home/UndoSnackbar.kt \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml \
  android/app/src/test/java/com/recordofp/app/data/repo/RoomReminderRepositoryTest.kt \
  android/app/src/test/java/com/recordofp/app/ui/home/HomeViewModelTest.kt \
  android/app/src/test/java/com/recordofp/app/ui/editor/EditorViewModelTest.kt \
  android/app/src/test/java/com/recordofp/app/ui/nearby/NearbyViewModelTest.kt \
  android/app/src/test/java/com/recordofp/app/data/engine/ReseedServiceTest.kt
git commit -m "feat: 완료 실행 취소 스낵바와 TalkBack 완료 동작" -m "완료를 되돌릴 수 있고, 스와이프는 한 방향만 받으며, TalkBack 사용자도 카드 동작으로 완료한다.
행 키에 updatedAt을 넣고 완료를 확정 상태에서 한 번만 불러 실행 취소 뒤 행이 끼지 않는다.
스와이프 뒤·스낵바는 개편안 2 모양(성공 면 원 체크, inverse 스낵바). (§4.1, §8, 최종 리뷰 I5·I7, 개편안 2 §2)"
```

---

### Task 18: 홈 카드·개수 pill·빈 상태 — 방향 B "카테고리 타일"

개편안 2의 핵심 결정(방향 B)을 홈에 그린다. 기록 카드는 왼쪽 48dp 트리거 타일 + 제목 + 트리거 이름을 " · "로 이은 줄이고, 회색 트리거 칩 줄은 없앤다. 타일은 대표 트리거 하나를 따른다 — 카테고리가 있으면 첫 카테고리, 없으면 특정 지점, 그것도 없으면 첫 브랜드(프리셋 포함). 큰 타이틀 옆에 개수 pill을 두고(0개면 숨김), 빈 상태는 연블루 원 안의 P-핀과 둘레의 카테고리 타일 셋, 제목과 두 줄 본문이다. 카드 등장·제거는 표준 모션(250ms)이다. 홈 본체를 `HomeContent`로 떼고 미리보기를 둔다. 기록 한 줄(`ReminderRow`, Task 14의 스와이프·TalkBack 포함)은 `ReminderCard.kt`로 옮긴다.

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/ui/common/TriggerTile.kt`
- Create: `android/app/src/main/java/com/recordofp/app/ui/home/ReminderCard.kt`, `HomeEmptyState.kt`, `HomePreviews.kt`
- Modify(전체 교체): `android/app/src/main/java/com/recordofp/app/ui/home/HomeScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`
- Test: `android/app/src/test/java/com/recordofp/app/ui/home/ReminderCardTest.kt` (생성)

**Interfaces:**
- Consumes: Task 16의 `TriggerVisual`·`visual()`·`categoryVisual`·`iconRes()`·`label()`·`tileColors()`, `Spacing`, `Motion`, `AppTextStyles.emptyTitle`, `PillShape`, `LightDarkPreviews`, `onSuccessColor()`; Task 13의 `ProtectionBanner`, `ProtectionIssue`, `rememberPermissionSnapshot`; Task 14의 `UndoSnackbar`, `HomeViewModel.reactivate`, `ReminderRow`·`CompleteSwipeBackground`(옮긴다)
- Produces: `@Composable fun TriggerTile(visual: TriggerVisual, size: Dp, cornerRadius: Dp, iconSize: Dp, modifier: Modifier = Modifier)`(ui/common), `internal fun List<TriggerSpec>.leadVisual(): TriggerVisual?`, `internal fun ReminderRow(item, onComplete, onClick, modifier)`, `internal fun HomeEmptyState(modifier)`, `@Composable fun HomeContent(items: List<Reminder>, issue: ProtectionIssue?, snackbarHostState: SnackbarHostState, onAddClick, onItemClick: (Long) -> Unit, onNearbyClick, onSettingsClick, onComplete: (Long) -> Unit)`

- [ ] **Step 1: 실패 테스트를 쓴다**

`android/app/src/test/java/com/recordofp/app/ui/home/ReminderCardTest.kt`:

```kotlin
package com.recordofp.app.ui.home

import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import com.recordofp.app.ui.common.TriggerVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderCardTest {

    private val brand = TriggerSpec(type = TriggerType.BRAND, brandKeyword = "GS25")
    private val daiso = TriggerSpec(type = TriggerType.CATEGORY, categoryId = "daiso")
    private val laundry = TriggerSpec(type = TriggerType.CATEGORY, categoryId = "laundry")
    private val place = TriggerSpec(type = TriggerType.PLACE, placeName = "크린토피아 역삼점")

    @Test
    fun `카드 타일은 카테고리가 있으면 첫 카테고리를 따른다`() {
        assertEquals(TriggerVisual.Category("laundry"), listOf(brand, place, laundry).leadVisual())
    }

    @Test
    fun `카테고리가 없으면 특정 지점, 그것도 없으면 첫 브랜드를 따른다 - 프리셋은 브랜드다`() {
        assertEquals(TriggerVisual.Place("크린토피아 역삼점"), listOf(daiso, brand, place).leadVisual())
        assertEquals(TriggerVisual.BrandPreset("daiso"), listOf(daiso, brand).leadVisual())
        assertEquals(TriggerVisual.BrandKeyword("GS25"), listOf(brand, daiso).leadVisual())
    }

    @Test
    fun `트리거가 없으면 타일이 없다`() {
        assertNull(emptyList<TriggerSpec>().leadVisual())
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.ReminderCardTest"`
Expected: 컴파일 실패 — `leadVisual`이 없다.

- [ ] **Step 3: 트리거 타일을 만든다**

`ui/common/TriggerTile.kt`(홈 카드·빈 상태·주변 보기가 함께 쓴다):

```kotlin
package com.recordofp.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * 트리거 타일 — 둥근 사각 tint 위에 ink 아이콘 (개편안 2 §2). 장식이라 설명이 없다 — 이름은 옆 글자가 말한다.
 * 크기: 홈 카드 48/16/24, 빈 상태 44/14/22, 주변 보기 그룹 머리 32/10/18 (타일/모서리/아이콘 dp)
 */
@Composable
fun TriggerTile(
    visual: TriggerVisual,
    size: Dp,
    cornerRadius: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = visual.tileColors()
    Box(
        modifier = modifier.size(size).background(colors.tint, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(visual.iconRes()),
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(iconSize),
        )
    }
}
```

- [ ] **Step 4: 기록 카드를 만든다**

`ui/home/ReminderCard.kt`. `ReminderRow`와 `CompleteSwipeBackground`는 Task 14에서 `HomeScreen.kt`에 넣은 것을 옮겨 오고, 카드 내용만 새로 그린다.

```kotlin
package com.recordofp.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recordofp.app.R
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.ui.common.TriggerTile
import com.recordofp.app.ui.common.TriggerVisual
import com.recordofp.app.ui.common.label
import com.recordofp.app.ui.common.visual
import com.recordofp.app.ui.theme.Spacing
import com.recordofp.app.ui.theme.onSuccessColor
import com.recordofp.app.ui.theme.successColor

/** 카드 타일이 따를 트리거: 카테고리 → 특정 지점 → 브랜드(프리셋 포함) 순으로 첫 것 (개편안 2 §2 기록 카드) */
internal fun List<TriggerSpec>.leadVisual(): TriggerVisual? {
    val visuals = map { it.visual() }
    return visuals.firstOrNull { it is TriggerVisual.Category }
        ?: visuals.firstOrNull { it is TriggerVisual.Place }
        ?: visuals.firstOrNull()
}

/**
 * 기록 한 줄 — 시작→끝 스와이프로 완료하고, TalkBack은 카드의 사용자 지정 동작으로 완료한다 (§4.1, §8).
 * 카드: 왼쪽 48dp 트리거 타일 + 제목 + 트리거 이름을 " · "로 이은 줄 (개편안 2 §2)
 */
@Composable
internal fun ReminderRow(
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
        backgroundContent = { CompleteSwipeBackground() },
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
            ReminderCardContent(item)
        }
    }
}

@Composable
private fun ReminderCardContent(item: Reminder) {
    Row(
        modifier = Modifier.padding(start = 14.dp, end = Spacing.m, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item.triggers.leadVisual()?.let { TriggerTile(it, size = 48.dp, cornerRadius = 16.dp, iconSize = 24.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.triggers.isNotEmpty()) {
                Text(
                    item.triggers.map { it.visual().label() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 완료 스와이프 뒤 — 성공 면, 32dp 원 체크, "완료" (개편안 2 §2) */
@Composable
private fun CompleteSwipeBackground() {
    val success = successColor()
    val onSuccess = onSuccessColor()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(success)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(onSuccess, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = success, modifier = Modifier.size(20.dp))
        }
        Text(
            stringResource(R.string.action_complete),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = onSuccess,
        )
    }
}
```

Run: `./gradlew testDebugUnitTest --tests "*.ReminderCardTest"` → PASS

- [ ] **Step 5: 빈 상태를 만든다**

`ui/home/HomeEmptyState.kt`(위치·각도는 개편안 2 목업 "홈 — 빈 상태"의 값이다):

```kotlin
package com.recordofp.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recordofp.app.R
import com.recordofp.app.ui.common.TriggerTile
import com.recordofp.app.ui.common.categoryVisual
import com.recordofp.app.ui.theme.AppTextStyles
import com.recordofp.app.ui.theme.Spacing

/** 빈 상태 — 연블루 원 안의 P-핀, 둘레에 카테고리 타일 셋, 제목과 두 줄 본문 (개편안 2 §2, 디자인 시스템 §1.3 브랜드 그래픽) */
@Composable
internal fun HomeEmptyState(modifier: Modifier = Modifier) {
    Column(
        // 아래 여백은 FAB를 피해 그래픽을 조금 위로 올린다
        modifier = modifier.padding(start = Spacing.xxl, end = Spacing.xxl, bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        EmptyGraphic()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(stringResource(R.string.home_empty_title), style = AppTextStyles.emptyTitle, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 장식 그래픽 — TalkBack은 건너뛴다 */
@Composable
private fun EmptyGraphic() {
    Box(Modifier.size(width = 220.dp, height = 196.dp).clearAndSetSemantics {}) {
        Box(
            modifier = Modifier
                .offset(x = 42.dp, y = 20.dp)
                .size(136.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_pin_mark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(84.dp),
            )
        }
        TriggerTile(
            categoryVisual("convenience"), size = 44.dp, cornerRadius = 14.dp, iconSize = 22.dp,
            modifier = Modifier.offset(x = 0.dp, y = 8.dp).rotate(-8f),
        )
        TriggerTile(
            categoryVisual("pharmacy"), size = 44.dp, cornerRadius = 14.dp, iconSize = 22.dp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 52.dp).rotate(10f),
        )
        TriggerTile(
            categoryVisual("cafe"), size = 44.dp, cornerRadius = 14.dp, iconSize = 22.dp,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = 22.dp, y = (-6).dp).rotate(6f),
        )
    }
}
```

- [ ] **Step 6: 홈 화면을 다시 쓴다**

`ui/home/HomeScreen.kt` 전체를 바꾼다. Task 14의 `ReminderRow`·`CompleteSwipeBackground`와 회색 칩(`TriggerChips`·`chipLabel`)은 이 파일에서 사라진다(앞의 둘은 `ReminderCard.kt`로 옮겼고, 칩은 없앤다).

```kotlin
package com.recordofp.app.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.ui.common.tabularNums
import com.recordofp.app.ui.permissions.ProtectionIssue
import com.recordofp.app.ui.permissions.rememberPermissionSnapshot
import com.recordofp.app.ui.theme.Motion
import com.recordofp.app.ui.theme.PillShape
import com.recordofp.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 활성 항목 목록과 완료 (설계 §4.1). 모양은 개편안 2 §2 홈 — 큰 타이틀 옆 개수 pill, 보호 배너,
 * 카테고리 타일 카드, 빈 상태 그래픽, 완료 스와이프와 [실행 취소] 스낵바, 확장 FAB "＋ 기록".
 */
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onNearbyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
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
    // 설정·빠른 설정에서 돌아오면 갱신하고, 보호 복구 전이면 재배치한다 (F1)
    val snapshot = rememberPermissionSnapshot(onRead = { viewModel.reportProtection(it.fullyProtected) })
    HomeContent(
        items = items,
        issue = snapshot.topIssue,
        snackbarHostState = snackbarHostState,
        onAddClick = onAddClick,
        onItemClick = onItemClick,
        onNearbyClick = onNearbyClick,
        onSettingsClick = onSettingsClick,
        onComplete = completeWithUndo,
    )
}

/** 홈 본체 — 상태와 동작만 받는다(미리보기는 HomePreviews.kt) */
@Composable
fun HomeContent(
    items: List<Reminder>,
    issue: ProtectionIssue?,
    snackbarHostState: SnackbarHostState,
    onAddClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onNearbyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onComplete: (Long) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 떠 있는 동안 FAB은 Scaffold가 스낵바 위로 올린다 (개편안 2 §2)
        snackbarHost = {
            SnackbarHost(snackbarHostState, Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)) { data ->
                UndoSnackbar(
                    message = data.visuals.message,
                    actionLabel = data.visuals.actionLabel.orEmpty(),
                    onAction = data::performAction,
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_fab)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 앱바 없이 종이 위에 바로 — 오른쪽 위 아이콘, 큰 타이틀 (개편안 §2)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onNearbyClick) {
                    Icon(Icons.Outlined.Place, stringResource(R.string.title_nearby))
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Outlined.Settings, stringResource(R.string.title_settings))
                }
            }
            HomeTitle(count = items.size)
            issue?.let {
                ProtectionBanner(
                    issue = it,
                    modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.m),
                )
            }
            if (items.isEmpty()) {
                HomeEmptyState(Modifier.fillMaxSize())
            } else {
                val motion = tween<Float>(Motion.STANDARD_MS, easing = Motion.easing)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = if (issue == null) Spacing.m else Spacing.s,
                        bottom = 100.dp, // 확장 FAB(56) 아래로 마지막 카드가 숨지 않게
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 키에 updatedAt을 넣는다 — 실행 취소로 되살아난 행이 스와이프된 옛 상태를 물려받지 않게 (N1)
                    items(items, key = { "${it.id}:${it.updatedAt}" }) { item ->
                        ReminderRow(
                            item = item,
                            onComplete = { onComplete(item.id) },
                            onClick = { onItemClick(item.id) },
                            // 등장·제거·재배열은 표준 모션 (개편안 2 §1.4)
                            modifier = Modifier.animateItem(
                                fadeInSpec = motion,
                                placementSpec = tween(Motion.STANDARD_MS, easing = Motion.easing),
                                fadeOutSpec = motion,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 큰 타이틀 + 개수 pill. 0개면 pill을 숨긴다 (개편안 2 §2, 디자인 시스템 §1.3 숫자 강조) */
@Composable
private fun HomeTitle(count: Int) {
    Row(
        modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.title_home),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (count > 0) CountPill(count)
    }
}

@Composable
private fun CountPill(count: Int) {
    val description = pluralStringResource(R.plurals.home_count, count, count)
    Box(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .widthIn(min = 28.dp)
            .background(MaterialTheme.colorScheme.onSurface, PillShape)
            .padding(horizontal = 9.dp)
            // TalkBack은 숫자만이 아니라 "5개"로 읽는다
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            style = tabularNums(MaterialTheme.typography.titleSmall).copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.background,
        )
    }
}
```

- [ ] **Step 7: 문자열을 바꾼다**

ko·en 양쪽에서 `home_empty` 줄을 지우고 그 자리에 넣는다.

ko:

```xml
    <string name="home_empty_title">아직 기록이 없어요</string>
    <string name="home_empty_body">생각난 순간, 여기에 적어두세요.\n근처를 지날 때 알려드릴게요.</string>
    <plurals name="home_count">
        <item quantity="other">%d개</item>
    </plurals>
```

en(앱 영어 용어 "note"에 맞춘다 — "구현에서 개편안 2와 다른 점" 5):

```xml
    <string name="home_empty_title">No notes yet</string>
    <string name="home_empty_body">Jot it down the moment it comes to mind.\nWe\'ll remind you when you pass by.</string>
    <plurals name="home_count">
        <item quantity="one">%d note</item>
        <item quantity="other">%d notes</item>
    </plurals>
```

(`home_count`는 개수 pill의 TalkBack 설명이다 — 숫자만 읽지 않고 "5개"로 읽는다.)

- [ ] **Step 8: 미리보기를 둔다**

`ui/home/HomePreviews.kt`(목록+'항상 허용' 배너, 빈 상태, 긴 제목·많은 트리거의 큰 글꼴):

```kotlin
package com.recordofp.app.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import com.recordofp.app.ui.permissions.ProtectionIssue
import com.recordofp.app.ui.theme.LightDarkPreviews
import com.recordofp.app.ui.theme.RecordOfPTheme

// 홈 미리보기 — 라이트·다크 두 벌과 큰 글꼴 (디자인 시스템 §5, 개편안 2 목업 "홈")

private fun reminder(id: Long, title: String, vararg triggers: TriggerSpec) =
    Reminder(id = id, title = title, createdAt = 0, updatedAt = 0, triggers = triggers.toList())

private fun category(id: String) = TriggerSpec(type = TriggerType.CATEGORY, categoryId = id)
private fun place(name: String) = TriggerSpec(type = TriggerType.PLACE, placeName = name)

private val sample = listOf(
    reminder(1, "건전지 사기", category("convenience")),
    reminder(2, "감기약 사기", category("pharmacy")),
    reminder(3, "셔츠 맡기기", category("laundry"), place("크린토피아 역삼점")),
    reminder(4, "택배 찾기", place("역삼동 무인택배함")),
    reminder(5, "수납함 사기", category("daiso")),
)

/** 긴 제목·트리거가 많은 카드 — 제목 두 줄, 트리거 줄 한 줄에서 말줄임 */
private val crowded = reminder(
    6, "주말 집들이 선물로 디퓨저와 향초, 예쁜 컵 두 개를 한꺼번에 사기",
    category("mart"), category("convenience"), category("cafe"), category("daiso"),
    TriggerSpec(type = TriggerType.BRAND, brandKeyword = "GS25"),
)

@Composable
private fun Home(items: List<Reminder>, issue: ProtectionIssue?) {
    RecordOfPTheme {
        HomeContent(
            items = items,
            issue = issue,
            snackbarHostState = remember { SnackbarHostState() },
            onAddClick = {},
            onItemClick = {},
            onNearbyClick = {},
            onSettingsClick = {},
            onComplete = {},
        )
    }
}

@LightDarkPreviews
@Composable
private fun HomeListPreview() = Home(sample, ProtectionIssue.BACKGROUND_LOCATION_OFF)

@LightDarkPreviews
@Composable
private fun HomeEmptyPreview() = Home(emptyList(), issue = null)

@Preview(name = "큰 글꼴", showBackground = true, fontScale = 2f)
@Composable
private fun HomeLargeFontPreview() = Home(listOf(crowded) + sample.take(2), issue = null)
```

- [ ] **Step 9: 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: 테스트 155개 통과, lint 오류 0·경고 25, 빌드 성공

`HomePreviews.kt`의 미리보기를 개편안 2 목업 "홈 — 목록과 보호 배너", "홈 — 빈 상태"와 비교한다. 큰 글꼴에서 제목은 두 줄, 트리거 줄은 한 줄에서 말줄임되고 개수 pill이 잘리지 않아야 한다.

- [ ] **Step 10: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/ui/common/TriggerTile.kt \
  android/app/src/main/java/com/recordofp/app/ui/home/ \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml \
  android/app/src/test/java/com/recordofp/app/ui/home/ReminderCardTest.kt
git commit -m "feat: 홈 개편안 2 — 카테고리 타일 카드·개수 pill·빈 상태" -m "카드 왼쪽에 대표 트리거 타일(카테고리 → 특정 지점 → 브랜드)을 두고 트리거 이름을 한 줄로 잇는다.
큰 타이틀 옆 개수 pill, P-핀과 카테고리 타일의 빈 상태, 표준 모션의 카드 등장·제거. 홈 본체를 HomeContent로 떼고 미리보기를 둔다. (개편안 2 §1.4·§2, 디자인 시스템 §1.3)"
```

---

### Task 19: 주변 보기 — 그룹 타일, 지점 카드, 큰 거리 숫자

개편안 2 §2 주변 보기를 그린다. 그룹 머리는 32dp 트리거 타일 + 이름(`titleMedium`) + 오른쪽 "N곳"이다. 그룹마다 카드 하나에 지점 행을 헤어라인으로 나눈다. 행은 64dp — 장소 이름과 "카카오맵에서 보기", 오른쪽에 거리 숫자(22 Bold tabular, 블루 글자)와 작은 단위다. 1km를 넘으면 숫자를 보조 글자색으로 낮춘다. 지금 그룹 머리는 이모지와 matchKey를 그대로 써서, 브랜드가 소문자("gs25")로 보인다. 뷰모델이 그룹마다 그릴 트리거(`TriggerVisual`)를 함께 내게 해 입력한 대소문자를 살린다. 화면 본체를 `NearbyContent`로 떼고 미리보기를 둔다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/ui/nearby/NearbyViewModel.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/common/DistanceBadge.kt`
- Modify(전체 교체): `android/app/src/main/java/com/recordofp/app/ui/nearby/NearbyScreen.kt`
- Create: `android/app/src/main/java/com/recordofp/app/ui/nearby/NearbyPreviews.kt`
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`
- Test: `android/app/src/test/java/com/recordofp/app/ui/nearby/NearbyViewModelTest.kt`, `android/app/src/test/java/com/recordofp/app/ui/common/DistanceFormatTest.kt` (생성)

**Interfaces:**
- Consumes: Task 16의 `TriggerVisual`·`categoryVisual`·`label()`, `AppTextStyles.numberLarge`, `Spacing`, `LightDarkPreviews`; Task 18의 `TriggerTile`; Task 15의 `BackButton`
- Produces: `data class NearbyGroup(val matchKey: String, val visual: TriggerVisual, val pois: List<PoiCandidate>)`, `fun distanceParts(distanceM: Int): Pair<String, String>`, `fun isFarDistance(distanceM: Int): Boolean`, `@Composable fun NearbyContent(state: NearbyUiState, onBack: () -> Unit, onRefresh: () -> Unit, onPoiClick: (PoiCandidate) -> Unit)`

- [ ] **Step 1: 실패 테스트를 쓴다**

`NearbyViewModelTest`에 import `com.recordofp.app.ui.common.TriggerVisual`을 더하고 테스트를 추가한다.

```kotlin
    @Test
    fun `그룹은 머리에 그릴 트리거를 함께 낸다 - 브랜드는 입력한 대소문자 그대로`() = runTest {
        val vm = vm(
            listOf(
                TriggerSpec(id = 1, reminderId = 1, type = TriggerType.BRAND, brandKeyword = "GS25"),
                TriggerSpec(id = 2, reminderId = 1, type = TriggerType.CATEGORY, categoryId = "daiso"),
                TriggerSpec(id = 3, reminderId = 1, type = TriggerType.CATEGORY, categoryId = "pharmacy"),
                TriggerSpec(
                    id = 4, reminderId = 1, type = TriggerType.PLACE,
                    placeName = "회사 우체국", placePoint = GeoPoint(37.509, 127.0),
                ),
            ),
            location = GeoPoint(37.5, 127.0),
        )
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        val visuals = vm.state.value.groups.associate { it.matchKey to it.visual }
        assertEquals(TriggerVisual.BrandKeyword("GS25"), visuals["brand:gs25"])
        assertEquals(TriggerVisual.BrandPreset("daiso"), visuals["cat:daiso"])
        assertEquals(TriggerVisual.Category("pharmacy"), visuals["cat:pharmacy"])
        assertEquals(TriggerVisual.Place("회사 우체국"), visuals["place:4"])
    }
```

`android/app/src/test/java/com/recordofp/app/ui/common/DistanceFormatTest.kt`:

```kotlin
package com.recordofp.app.ui.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceFormatTest {

    @Test
    fun `1km 미만은 m, 이상은 km 한 자리로 나눈다`() {
        assertEquals("999" to "m", distanceParts(999))
        assertEquals("1.0" to "km", distanceParts(1000))
        assertEquals("1.1" to "km", distanceParts(1100))
        assertEquals("180m", formatDistance(180))
        assertEquals("2.4km", formatDistance(2400))
    }

    @Test
    fun `1km를 넘어야 멀다`() {
        assertFalse(isFarDistance(1000))
        assertTrue(isFarDistance(1001))
    }

    @Test
    fun `기기 언어가 쉼표 소수점이어도 점으로 쓴다`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.1" to "km", distanceParts(1100))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.NearbyViewModelTest" --tests "*.DistanceFormatTest"`
Expected: 컴파일 실패 — `NearbyGroup.visual`, `distanceParts`, `isFarDistance`가 없다.

- [ ] **Step 3: 그룹에 그릴 트리거를 싣는다**

`NearbyViewModel.kt`를 고친다(import `com.recordofp.app.ui.common.TriggerVisual`, `com.recordofp.app.ui.common.categoryVisual`).

```kotlin
/** 주변 보기 그룹 — 트리거 하나(matchKey)와 그 근처 지점들. visual은 그룹 머리에 그릴 타일·이름 (개편안 2 §2) */
data class NearbyGroup(val matchKey: String, val visual: TriggerVisual, val pois: List<PoiCandidate>)
```

`load()` 안의 두 군데를 바꾼다.

```kotlin
                        if (pois.isEmpty()) null else NearbyGroup(req.matchKey, req.visual(), pois)
```

```kotlin
                    is PlaceRequest -> NearbyGroup(
                        req.matchKey,
                        TriggerVisual.Place(req.name.orEmpty()),
                        listOf(
```

파일 끝에 더한다.

```kotlin

/** 그룹 머리에 그릴 트리거 — 브랜드는 matchKey(소문자)가 아니라 입력한 검색어 그대로 보인다 */
private fun QueryRequest.visual(): TriggerVisual =
    if (matchKey.startsWith("cat:")) categoryVisual(matchKey.removePrefix("cat:")) else TriggerVisual.BrandKeyword(query)
```

(`TriggerResolver`는 카테고리 요청의 `query`에 카카오 코드(CS2 등)를, 브랜드 요청에는 앞뒤 공백만 뺀 입력 그대로를 싣는다. 그래서 카테고리는 matchKey에서 id를, 브랜드는 `query`를 쓴다.)

- [ ] **Step 4: 거리를 숫자와 단위로 나눈다**

`ui/common/DistanceBadge.kt`를 고친다(import `java.util.Locale`). KDoc 둘째 줄을 `숫자는 onPrimaryContainer — 연블루 위 4.5:1 (개편안 2 §1.1). 에디터 장소 검색 결과가 쓴다.`로 바꾼다(주변 보기는 이제 큰 숫자를 쓴다). `formatDistance`를 바꾸고 두 함수를 더한다.

```kotlin
/** 999m까지는 m, 그 위는 km 한 자리 — 배지 폭을 짧게 유지한다 */
fun formatDistance(distanceM: Int): String = distanceParts(distanceM).let { (number, unit) -> number + unit }

/**
 * 거리를 숫자와 단위로 나눈다 — 주변 보기는 숫자를 크게, 단위를 작게 그린다 (개편안 2 §2).
 * 소수점은 기기 언어와 상관없이 점으로 쓴다(앱 문자열은 ko·en뿐이다).
 */
fun distanceParts(distanceM: Int): Pair<String, String> =
    if (distanceM < 1000) "$distanceM" to "m" else "%.1f".format(Locale.ROOT, distanceM / 1000.0) to "km"

/** 1km를 넘으면 걸어서 들르기엔 멀다 — 주변 보기는 숫자를 보조 글자색으로 낮춘다 (개편안 2 §2) */
fun isFarDistance(distanceM: Int): Boolean = distanceM > FAR_DISTANCE_M

private const val FAR_DISTANCE_M = 1000
```

Run: `./gradlew testDebugUnitTest --tests "*.NearbyViewModelTest" --tests "*.DistanceFormatTest"` → PASS

- [ ] **Step 5: 문자열을 더한다**

`nearby_open_map` 다음 줄에 넣는다.

ko:

```xml
    <plurals name="nearby_place_count">
        <item quantity="other">%d곳</item>
    </plurals>
```

en:

```xml
    <plurals name="nearby_place_count">
        <item quantity="one">%d place</item>
        <item quantity="other">%d places</item>
    </plurals>
```

- [ ] **Step 6: 주변 보기 화면을 다시 쓴다**

`ui/nearby/NearbyScreen.kt` 전체를 바꾼다. 그룹 머리의 이모지(`TriggerCatalog.emoji`)와 `DistanceBadge`는 더 쓰지 않는다.

```kotlin
package com.recordofp.app.ui.nearby

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.ui.common.BackButton
import com.recordofp.app.ui.common.TriggerTile
import com.recordofp.app.ui.common.distanceParts
import com.recordofp.app.ui.common.isFarDistance
import com.recordofp.app.ui.common.label
import com.recordofp.app.ui.theme.AppTextStyles
import com.recordofp.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 백그라운드 권한 없이도(위치 '사용 중'만으로) 앱을 열면 지금 주변에서 처리할 수 있는 일이
 * 보이는 화면 — 열화 모드의 핵심 (설계 §3.1, §4.2). 지도 SDK는 v1.1 — 카카오맵 앱 딥링크로
 * 대체한다(§3.2). 모양은 개편안 2 §2 주변 보기 — 그룹 머리 타일, 그룹당 카드 하나, 큰 거리 숫자.
 */
@Composable
fun NearbyScreen(
    onBack: () -> Unit = {},
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load() }
    NearbyContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::load,
        onPoiClick = { openInKakaoMap(context, it) },
    )
}

/** 주변 보기 본체 — 상태와 동작만 받는다(미리보기는 NearbyPreviews.kt) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyContent(
    state: NearbyUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPoiClick: (PoiCandidate) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onClick = onBack) },
                title = { Text(stringResource(R.string.title_nearby)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.locationUnavailable -> CenteredNote(stringResource(R.string.nearby_no_location))
                state.groups.isEmpty() -> CenteredNote(stringResource(R.string.nearby_empty))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    items(state.groups, key = { it.matchKey }) { group ->
                        NearbyGroupSection(group = group, onPoiClick = onPoiClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CenteredNote(text: String) {
    Text(
        text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
    )
}

/** 그룹 — 머리(32dp 타일 + 이름 + "N곳") 아래 카드 하나에 지점 행을 헤어라인으로 나눈다 */
@Composable
private fun NearbyGroupSection(group: NearbyGroup, onPoiClick: (PoiCandidate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TriggerTile(group.visual, size = 32.dp, cornerRadius = 10.dp, iconSize = 18.dp)
            Text(group.visual.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.nearby_place_count, group.pois.size, group.pois.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column {
                group.pois.forEachIndexed { index, poi ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    NearbyPoiRow(poi = poi, onClick = { onPoiClick(poi) })
                }
            }
        }
    }
}

/** 지점 행 64dp — 이름 + "카카오맵에서 보기", 오른쪽에 거리 숫자 22 Bold + 단위. 1km를 넘으면 숫자를 보조 글자색으로 */
@Composable
private fun NearbyPoiRow(poi: PoiCandidate, onClick: () -> Unit) {
    val distanceM = poi.distanceM.roundToInt()
    val (number, unit) = distanceParts(distanceM)
    val numberColor = if (isFarDistance(distanceM)) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val openMapLabel = stringResource(R.string.nearby_open_map)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = openMapLabel, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(poi.name, style = MaterialTheme.typography.titleSmall)
            Text(openMapLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row {
            Text(number, style = AppTextStyles.numberLarge, color = numberColor, modifier = Modifier.alignByBaseline())
            Text(
                unit,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = numberColor,
                modifier = Modifier.alignByBaseline().padding(start = 1.dp),
            )
        }
    }
}

/** 카카오맵 앱으로 지점 위치를 연다. 미설치 등으로 실패하면 웹 지도로 대체한다 (설계 §3.2) */
private fun openInKakaoMap(context: Context, poi: PoiCandidate) {
    val uri = "kakaomap://look?p=${poi.point.lat},${poi.point.lng}"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
        .onFailure { // 카카오맵 미설치 → 웹 지도
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://map.kakao.com/link/map/${Uri.encode(poi.name)},${poi.point.lat},${poi.point.lng}"),
                ),
            )
        }
}
```

- [ ] **Step 7: 미리보기를 둔다**

`ui/nearby/NearbyPreviews.kt`(목록 — 1.1km 다이소 행은 보조색, 위치 없음, 큰 글꼴):

```kotlin
package com.recordofp.app.ui.nearby

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.ui.common.TriggerVisual
import com.recordofp.app.ui.theme.LightDarkPreviews
import com.recordofp.app.ui.theme.RecordOfPTheme

// 주변 보기 미리보기 — 라이트·다크 두 벌과 큰 글꼴 (디자인 시스템 §5, 개편안 2 목업 "주변 보기")

private fun poi(name: String, distanceM: Double) = PoiCandidate(name, name, GeoPoint(37.5, 127.03), distanceM)

private val groups = listOf(
    NearbyGroup(
        "cat:convenience", TriggerVisual.Category("convenience"),
        listOf(poi("CU 역삼점", 180.0), poi("GS25 역삼힐스점", 240.0), poi("세븐일레븐 역삼중앙점", 410.0)),
    ),
    NearbyGroup("cat:pharmacy", TriggerVisual.Category("pharmacy"), listOf(poi("온누리약국", 650.0), poi("역삼365약국", 720.0))),
    NearbyGroup("cat:daiso", TriggerVisual.BrandPreset("daiso"), listOf(poi("다이소 역삼점", 1100.0))),
)

@Composable
private fun Nearby(state: NearbyUiState) {
    RecordOfPTheme { NearbyContent(state = state, onBack = {}, onRefresh = {}, onPoiClick = {}) }
}

@LightDarkPreviews
@Composable
private fun NearbyListPreview() = Nearby(NearbyUiState(loading = false, groups = groups))

@LightDarkPreviews
@Composable
private fun NearbyNoLocationPreview() = Nearby(NearbyUiState(loading = false, locationUnavailable = true))

@Preview(name = "큰 글꼴", showBackground = true, fontScale = 2f)
@Composable
private fun NearbyLargeFontPreview() = Nearby(NearbyUiState(loading = false, groups = groups.take(1)))
```

- [ ] **Step 8: 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: 테스트 159개 통과, lint 오류 0·경고 25, 빌드 성공

미리보기를 개편안 2 목업 "주변 보기"와 비교한다.

- [ ] **Step 9: 커밋한다**

```bash
git add android/app/src/main/java/com/recordofp/app/ui/nearby/ \
  android/app/src/main/java/com/recordofp/app/ui/common/DistanceBadge.kt \
  android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml \
  android/app/src/test/java/com/recordofp/app/ui/nearby/NearbyViewModelTest.kt \
  android/app/src/test/java/com/recordofp/app/ui/common/DistanceFormatTest.kt
git commit -m "feat: 주변 보기 개편안 2 — 그룹 타일·지점 카드·큰 거리 숫자" -m "그룹 머리에 트리거 타일과 'N곳', 그룹마다 카드 하나에 지점 행, 거리 숫자를 크게(1km를 넘으면 보조색) 보인다.
브랜드 그룹은 입력한 대소문자 그대로, 소수점은 기기 언어와 상관없이 점으로 쓴다. 본체를 NearbyContent로 떼고 미리보기를 둔다. (§3.1, 개편안 2 §2)"
```

---

### Task 20: 근처 알림 — 제목의 이모지를 빼고 브랜드 블루를 입힌다 (+ 묶음 D 문서 마감)

개편안 2 §2·§3 근처 알림. 알림은 시스템 템플릿을 그대로 쓰므로 바꿀 것은 둘이다. 제목 앞의 📍를 뺀다(작은 아이콘 P-핀과 강조색이 이미 "근처 알림"임을 알린다). `setColor`로 작은 아이콘·앱 이름에 브랜드 블루를 입힌다. platform은 `ui/theme`을 import하지 않으므로 강조색은 색 리소스로 둔다. 여러 항목 알림(InboxStyle 목록, [오늘 그만])은 Task 11 그대로다. 묶음 D가 바꾼 동작과 모양을 설계 문서·디자인 시스템에 반영하는 문서 단계도 이 태스크에 둔다.

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-en/strings.xml`
- Modify: `android/app/src/main/res/values/colors.xml`, `android/app/src/main/res/values-night/colors.xml`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/notify/NearbyNotifier.kt`
- Test: `android/app/src/test/java/com/recordofp/app/ui/StringResourcesTest.kt`
- Modify(문서 단계): `docs/design/design-system.md`(1.3), `docs/superpowers/specs/2026-08-31-record-of-p-design.md`(1.4), `CLAUDE.md`, 이 계획의 머리말

**Interfaces:**
- Consumes: Task 12의 `StringResourcesTest`, Task 11의 `NearbyNotifier.show(group)`
- Produces: 색 리소스 `R.color.notification_accent`(라이트 `#1B6EF3`, 다크 `#5B95F8`)

- [ ] **Step 1: 실패 테스트를 쓴다**

`StringResourcesTest`에 import `org.junit.Assert.assertTrue`를 더하고 테스트를 추가한다.

```kotlin
    @Test
    fun `근처 알림 제목에는 이모지가 없다`() {
        // 개편안 2 §3 — 작은 아이콘(P-핀)과 강조색이 이미 "근처 알림"임을 알린다
        for (path in listOf("src/main/res/values/strings.xml", "src/main/res/values-en/strings.xml")) {
            val text = File(path).readText()
            for (key in listOf("notif_nearby_title", "notif_nearby_title_dist")) {
                val value = Regex("""<string name="$key">([^<]*)</string>""").find(text)!!.groupValues[1]
                assertTrue("$path $key = $value", value.codePoints().noneMatch { it >= 0x1F000 })
            }
        }
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew testDebugUnitTest --tests "*.StringResourcesTest"`
Expected: FAIL — `notif_nearby_title = 📍 %1$s 근처예요`

- [ ] **Step 3: 제목에서 이모지를 뺀다**

ko:

```xml
    <string name="notif_nearby_title">%1$s 근처예요</string>
    <string name="notif_nearby_title_dist">%1$s 근처예요 (약 %2$dm)</string>
```

en:

```xml
    <string name="notif_nearby_title">Near %1$s</string>
    <string name="notif_nearby_title_dist">Near %1$s (~%2$dm)</string>
```

Run: `./gradlew testDebugUnitTest --tests "*.StringResourcesTest"` → PASS

- [ ] **Step 4: 알림에 브랜드 블루를 입힌다**

`res/values/colors.xml`의 `</resources>` 앞에 넣는다.

```xml
    <!-- 근처 알림 강조색(setColor) = primary. platform은 ui/theme을 쓰지 않으므로 리소스로 둔다 (개편안 2 §2) -->
    <color name="notification_accent">#1B6EF3</color>
```

`res/values-night/colors.xml`의 `</resources>` 앞에 넣는다.

```xml
    <!-- 근처 알림 강조색 다크 = 다크 primary -->
    <color name="notification_accent">#5B95F8</color>
```

`NearbyNotifier.show()`의 `.setSmallIcon(R.drawable.ic_stat_pin)` 다음 줄에 넣는다(import `androidx.core.content.ContextCompat`).

```kotlin
            // 작은 아이콘·앱 이름에 브랜드 블루 — 제목의 이모지 대신 앱을 알아보게 한다 (개편안 2 §2)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
```

- [ ] **Step 5: 전체 검증을 확인한다**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: 테스트 160개 통과, lint 오류 0·경고 25, 빌드 성공

- [ ] **Step 6: 커밋한다**

```bash
git add android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml \
  android/app/src/main/res/values/colors.xml android/app/src/main/res/values-night/colors.xml \
  android/app/src/main/java/com/recordofp/app/platform/notify/NearbyNotifier.kt \
  android/app/src/test/java/com/recordofp/app/ui/StringResourcesTest.kt
git commit -m "feat: 근처 알림 제목의 이모지를 빼고 브랜드 블루를 입힌다" -m "작은 아이콘 P-핀과 강조색(setColor)으로 앱을 알아보게 하고, 제목은 장소 이름으로 시작한다. (§4.1, 개편안 2 §2·§3)"
```

- [ ] **Step 7: 설계 문서 1.4, 디자인 시스템 1.3, CLAUDE.md, 계획 머리말을 갱신하고 커밋한다 (묶음 D 전체 반영)**

설계 문서와 디자인 시스템은 살아있는 문서다(CLAUDE.md 문서 규칙). 묶음 D(Task 12~20)가 바꾼 동작과 모양을 반영한다. 설계 문서의 §번호는 바꾸지 않는다.

`docs/superpowers/specs/2026-08-31-record-of-p-design.md`:

1. §4.1 1번(`1. **기록**: …마찰이 낮아야 "생각난 순간 기록" 습관이 생긴다.`) 끝에 이어 쓴다.

```text
 브랜드는 입력만 하고 [추가]를 누르지 않아도 저장할 때 함께 추가된다. 저장을 연타해도 한 번만 저장되고, 저장이 실패하면 입력을 그대로 둔 채 알린다.
```

2. §4.1 2번의 `"📍 CU 역삼점 근처예요 (약 80m) · '건전지 사기' 외 1건"`을 `"CU 역삼점 근처예요 (약 80m) · '건전지 사기' 외 1건"(작은 아이콘 P-핀과 브랜드 블루로 앱을 알아본다)`으로 바꾼다.

3. §4.1 3번 전체를 바꾼다.

```text
3. **처리**: 알림에서 즉시 완료 처리하거나, 앱에서 시작→끝 스와이프로 완료한다(TalkBack은 카드의 "완료" 동작). 완료하면 [실행 취소] 스낵바가 잠시 뜬다. 완료 항목은 보관함으로.
```

4. §4.2 표 아래 첫 항목(`- Android 11+에서 백그라운드 위치는 시스템 설정에서만 부여 가능 → 업셀 카드는 설정 화면 딥링크 + 단계별 안내 이미지로 구성한다.`)을 바꾼다.

```text
- Android 11+에서 백그라운드 위치는 시스템 설정에서만 부여 가능 → 업셀은 홈 보호 배너로 한다: [설정 열기](앱 상세 설정 딥링크)와 단계 칩(설정 → 권한 → 위치 → 항상 허용).
```

5. §4.3 문단의 `배터리 최적화 예외를 뺀 항목 중 하나라도 꺼지면 홈 상단에 배너로 노출한다(배터리 예외는 권장 사항이라 배너에 넣지 않는다).` 다음에 이어 쓴다.

```text
 배너는 꺼진 것 하나를 우선순위(알림 → 정확한 위치 → 항상 허용 → 기기 위치)대로 골라 이름으로 알리고, 그것을 고칠 시스템 화면을 바로 연다. 빠른 설정에서 기기 위치를 켜고 끄는 것도 화면이 보이는 동안 바로 반영한다.
```

6. §8 둘째 항목(`- TalkBack 콘텐츠 설명, 48dp 터치 타깃, 폰트 스케일 대응(모든 텍스트 sp).`) 끝에 이어 쓴다.

```text
 스와이프 동작(완료)은 사용자 지정 동작으로도 제공한다. 색은 아이콘·이름과 함께 쓰고(색만으로 구분하지 않는다), 글자 대비는 WCAG 4.5:1을 넘긴다(디자인 시스템 §2.1).
```

7. 머리말 표: `| 버전 | 1.3 |` → `| 버전 | 1.4 |`, `최종 수정`을 커밋하는 날짜로 바꾼다.
8. 끝의 변경 이력 표에 한 행을 더한다(날짜는 커밋하는 날짜).

```text
| 1.4 | <날짜> | §4.1, §4.2, §4.3, §8 | 에디터 저장 가드(입력 중 브랜드 보존·연타 방지·실패 안내), 근처 알림 제목의 이모지 제거와 브랜드 블루, 한 방향 스와이프 완료와 [실행 취소]·TalkBack 완료, 보호 배너(꺼진 것 하나·설정 바로 열기·단계 칩·빠른 설정 반영), 색 대비와 색만으로 구분하지 않기 | 보강 계획 묶음 D(T12~T20), 디자인 개편안 2, 최종 리뷰 I3·I5·I6·I7, 묶음 B 인계 |
```

`docs/design/design-system.md`:

1. 머리말 표: `| 버전 | 1.2 |` → `| 버전 | 1.3 |`, `최종 수정`을 커밋하는 날짜로 바꾼다. `구현` 칸의 아이콘 부분을 아래로 바꾼다.

```text
아이콘 `res/drawable/ic_cat_*`·`ic_trigger_*`·`ic_banner_*`·`ic_alert_error`
```
2. §2.1 아래 항목들 끝에 한 줄을 더한다.

```text
- 근처 알림 강조색(`setColor`)은 `res/values{,-night}/colors.xml`의 `notification_accent`다. primary와 같은 값이고, platform 코드가 `ui/theme`을 쓰지 않도록 리소스로 둔다.
```

3. §2.7 표 끝에 두 행을 더한다.

```text
| 보호 배너: 알림 꺼짐 · 정확한 위치 · 기기 위치 꺼짐 ('항상 허용'은 `ic_trigger_place`) | `ic_banner_notifications_off` · `ic_banner_precise` · `ic_banner_location_off` | notifications_off · my_location · location_off |
| 저장 실패 | `ic_alert_error` | error |
```

4. §3 첫 줄(화면마다 private으로 두고 쓰는 것이 많다로 시작하는 문장)은 그대로 두고, 표 전체를 바꾼다.

```text
| 컴포넌트 | 위치 | 쓰임 |
|---|---|---|
| 트리거 시각 `TriggerVisual` · 트리거 타일 `TriggerTile` | `ui/common` | 트리거 → 타일 색·아이콘·이름. 타일은 홈 카드 48dp, 빈 상태 44dp, 주변 보기 그룹 머리 32dp |
| 기록 카드 `ReminderRow` | home(`ReminderCard.kt`) | 대표 트리거 타일(카테고리 → 특정 지점 → 브랜드) + 제목(두 줄) + 트리거 이름 줄(한 줄). 시작→끝 스와이프 완료(성공 면·원 체크), TalkBack 사용자 지정 동작 "완료" |
| 개수 pill | home | 큰 타이틀 옆, 잉크 바탕·종이 글자 15 Bold tabular. 0개면 숨김, TalkBack은 "N개" |
| 빈 상태 `HomeEmptyState` | home | 연블루 원 안 P-핀 + 카테고리 타일 셋(편의점·약국·카페), 제목 20 Bold + 본문 두 줄 |
| 보호 배너 `ProtectionBanner` | home | 앰버 면 20dp, 36dp 원 아이콘, 제목·본문, '항상 허용' 단계 칩(마지막만 강조), [설정 열기] pill |
| 실행 취소 스낵바 `UndoSnackbar` | home | inverse 면 16dp, 성공 체크 원, [실행 취소](inversePrimary) |
| 확장 FAB "＋ 기록" | home | 새 기록. 스낵바가 뜨면 위로 올라간다 |
| 거리 배지 `DistanceBadge` | `ui/common` | 에디터 장소 결과 — 연블루 pill + onPrimaryContainer 숫자, 999m 넘으면 km 한 자리 |
| 주변 보기 그룹 `NearbyGroupSection` · 지점 행 `NearbyPoiRow` | nearby | 32dp 타일 + 이름 + "N곳", 그룹마다 카드 하나에 헤어라인 행(64dp). 거리 숫자 22 Bold(1km 넘으면 보조색) + 단위 |
| TopAppBar · 뒤로 `BackButton` | editor, settings, nearby, diagnostics · `ui/common` | 상태바 인셋을 처리하는 상단 바. 에디터는 ✕ · 제목 · 저장(onPrimaryContainer), 나머지는 뒤로(TalkBack "뒤로") · 제목 |
| 에디터 `EditorSection` · `EditorField` · `CategoryChip` · `RemovableChip` · `AddBrandButton` · `SearchPlaceButton` · `SaveFailedNotice` · `PlaceResultCard` | editor(`EditorComponents.kt`) | 섹션 제목 15 SemiBold, 카드 바탕 입력(1dp 테두리·포커스 2dp 블루, 56dp), 카테고리 칩(선택 = tint + ink 테두리 + 체크), 브랜드 [추가]·안내, 고른 브랜드(회색)·지점(연블루) 칩, 저장 실패 오류 면 |
| 섹션 레이블 `SectionLabel` | settings | labelMedium. 에디터는 섹션 제목으로 바뀌어 설정에만 남았다 |
| 그룹 카드 `GroupCard` · 상태 pill `StatusPill` | settings | 보호 상태, 알림 정책, 문제 해결 묶음. pill은 "켜짐"/"꺼짐" |
| 진단 행 `DiagnosticsRow` | diagnostics | 결과별 8dp 컬러 도트: APPLIED·PASS 성공색, BLOCK·FAILED·ERROR 오류색, 나머지(STOOD_DOWN·STALE 등) 테두리 회색 |
| 핀 그래픽 `PinMarkGraphic` · 왜 카드 `WhyCard` · 페이지 도트 `PageDots` · 하단 버튼 `PrimaryButton` | onboarding | 단계별 권한 안내 |
```

5. 끝의 변경 이력 표 맨 위에 한 행을 더한다.

```text
| 1.3 | <날짜> | §2.1, §2.7, §3 | 개편안 2 컴포넌트 반영 — 카테고리 타일 기록 카드·개수 pill·빈 상태, 보호 배너, 실행 취소 스낵바, 에디터 컴포넌트, 주변 보기 그룹·행, 트리거 타일·뒤로 버튼 공통화, 알림 강조색, 배너·오류 아이콘 | 개편안 2 §2, 보강 계획 Task 13·14·17~20 |
```

`CLAUDE.md`:

- "문서 목록"의 "디자인 개편안 2" 줄 끝 문장(Task 16에서 고친 "토큰은 디자인 시스템 1.2로 옮겼고, 화면은 묶음 D(…)에서 바꾼다.")을 바꾼다.

```text
토큰과 컴포넌트는 디자인 시스템 1.2·1.3으로 옮겼다(묶음 D). 실기기 라이트·다크 스크린샷 확인이 남았다.
```

- "두 개의 핵심 파이프라인"의 2번(이벤트) 줄에서 `펜스 단위로 묶어 알림 1건 발행(알림 id도 펜스 기준).`을 아래로 바꾼다.

```text
펜스 단위로 묶어 알림 1건 발행(알림 id도 펜스 기준, 강조색은 `R.color.notification_accent`).
```

이 계획 파일 머리말의 `상태`를 바꾼다.

```text
진행 중 — 묶음 A·B·C 병합(PR #3·#4·#5), 묶음 D(Task 12~20) 구현 완료(`feat/hardening-ui`, 최종 리뷰 전)
```

```bash
git add docs/superpowers/specs/2026-08-31-record-of-p-design.md docs/design/design-system.md CLAUDE.md \
  docs/superpowers/plans/2026-10-03-v1-hardening-port.md
git commit -m "docs: 설계 문서 1.4·디자인 시스템 1.3 — 묶음 D 반영" -m "에디터 저장 가드, 알림 제목·강조색, 한 방향 스와이프와 실행 취소, 보호 배너, 접근성(사용자 지정 동작·색 대비).
개편안 2 컴포넌트를 디자인 시스템 §3에 옮긴다. (§4.1, §4.2, §4.3, §8)"
```

### 묶음 D 인계 (2026-10-10 최종 리뷰)

묶음 D(`feat/hardening-ui`) 최종 리뷰에서 나왔지만 이 묶음에서 고치지 않은 것이다.

- **사용자 확인**: 개편안 2 §2는 "실행 취소 스낵바가 떠 있는 동안 FAB은 스낵바 위로 올라간다"고 정했지만, Material 3 `Scaffold`는 스낵바를 FAB 위에 띄운다(겹치지 않음). 이 묶음은 Material 기본 동작을 따랐다. 목업대로 FAB을 올리려면 스낵바를 Scaffold 슬롯 밖에 두고 높이를 재서 FAB 여백을 애니메이션해야 한다.
- **후속(태스크 미정)**: 에디터가 카탈로그에서 빠진 옛 카테고리 id를 칩으로 보이지 않아, 그 id는 선택에 남은 채 뺄 수 없다. 홈 카드처럼 회색 칩(id 그대로)으로 보이고 뺄 수 있게 한다.
- **후속(태스크 미정)**: `RoomReminderRepository.upsert`가 트랜잭션이 아니다(트리거 삭제 → 삽입). 저장 중 화면을 나가 작업이 취소되면 기록만 남고 트리거가 없어질 수 있다. `withTransaction`으로 묶는다(스키마 변경 아님).
- **후속(태스크 미정)**: TalkBack 사용자는 실행 취소 시간이 짧을 수 있다(Short, "조치를 취할 시간" 기본값이면 약 4초). 완료를 되살리는 다른 UI가 없으므로 터치 탐색이 켜져 있으면 `Long`을 검토한다.
- **후속(태스크 미정)**: 테마 헬퍼(`successColor()` 등, `categoryTileColors`)가 `isSystemInDarkTheme()`를 직접 읽어 `RecordOfPTheme(darkTheme = …)`를 따르지 않는다. 지금은 넘기는 호출이 없다. CompositionLocal로 준다.
- **후속 후보(작음)**: 삭제 실패에도 "변경 내용을 저장하지 못했어요"가 뜬다(`editor_delete_failed` 추가). 브랜드·장소 입력의 [완료]·[검색] 뒤 키보드가 닫히지 않는다. 주변 보기 지점 행을 TalkBack이 숫자·단위를 따로, "카카오맵에서 보기"를 두 번 읽는다(행 의미를 버튼 하나로).
- **다시 그릴 때**: 설정·온보딩은 종이 위 `primary` 글자(4.32:1)를 쓴다. 디자인 시스템 §2.1의 대비 문장과 맞추려면 그 화면을 다시 그릴 때 `onPrimaryContainer`로 바꾼다(개편안 2 §6 범위 밖).
- **출시 전**: Material Symbols(Apache 2.0) 오픈소스 고지를 앱 안에 둔다.

---

## 마무리 — 사용자 기기 확인 목록

각 묶음 PR 본문에는 아래 목록 중 그 묶음과 관련된 항목 번호를 적는다(묶음 D는 4·8~12·16~20). 네 묶음이 모두 병합되면 사용자가 실기기에서 한꺼번에 확인한다. `tools/device/e2e.sh`(macOS는 `ADB=~/Library/Android/sdk/platform-tools/adb`)와 진단 화면(설정 → 문제 해결)을 쓴다. 확인한 결과는 `docs/superpowers/notes/`에 2차 검증 노트로 남긴다.

1. **재부팅**: `adb reboot` → 잠금 해제 → 진단에 `BOOT → APPLIED`가 남고 note가 `full-resync`인지 확인한다.
2. **위치 껐다 켜기**: 기기 위치를 끄면 진단에 `FENCE_LOST`가 생기고, 홈 배너는 "기기 위치가 꺼져 있어요"가 된다. 위치를 켜는 것만으로는 재배치가 바로 돌지 않는다. 다시 켠 뒤 앱을 열면 `APP_OPEN → APPLIED`(full-resync)가, 열지 않으면 백오프 재시도(15분·30분·1시간…) 때 `FENCE_LOST → APPLIED`(full-resync)가 남는다.
3. **부팅 직후 오프라인**: 비행기 모드로 재부팅 → `BOOT → FAILED`, note가 `restored-from-mirror`로 시작하는지 확인한다. 네트워크를 켜면 재시도 후 APPLIED가 된다.
4. **'항상 허용' 해제와 재허용**: 해제하면 진단에 `STOOD_DOWN · ACCESS_BACKGROUND_LOCATION`이 남고, 홈 배너가 단계 칩(설정 → 권한 → 위치 → 항상 허용)을 담은 업셀 카드가 된다. [설정 열기]로 다시 허용하고 돌아오면 곧바로 `APP_OPEN → APPLIED`가 남는다.
5. **근처 알림 채널만 끄기**: 알림을 길게 눌러 채널을 끄면 대시보드의 알림 행이 꺼짐이 된다. 이벤트가 오면 `BLOCK_NOTIFICATIONS_OFF`가 남고, 하루 상한은 줄지 않는다.
6. **같은 항목이 두 펜스에 걸릴 때**: (a) 한 가게를 PLACE로도, 카테고리로도 걸어 두고 그 가게에 가면 PLACE 진입 알림이 한 번 뜨고, 1분 남짓 뒤의 카테고리 DWELL은 진단에 `BLOCK_ITEM_COOLDOWN`으로 남는다. (b) 같은 카테고리 지점 두 곳의 120m 원이 겹치는 자리에 1분 넘게 머물면 알림은 한 번(가까운 지점 이름)이고, 두 DWELL이 한 이벤트로 오면 `BLOCK_SAME_EVENT`, 따로 오면 `BLOCK_ITEM_COOLDOWN`이 남는다.
7. **여러 항목 알림**: 펼치면 모든 제목이 보이고, [오늘 그만]을 누르면 묶음 전체가 다음 날 05:00까지 억제된다.
8. **온보딩(Android 12+)**: 위치 허용을 누르면 "정확한 위치/대략적 위치" 선택 대화상자가 뜬다. 대략적 위치만 고르면 홈 배너가 "정확한 위치 권한이 필요해요"가 된다.
9. **에디터**: 브랜드를 입력만 하고 [추가] 없이 저장해도 홈 카드의 트리거 줄에 브랜드가 보인다. 저장을 빠르게 두 번 눌러도 항목은 하나다.
10. **완료 실행 취소**: 스와이프 완료 → [실행 취소] → 행이 정상 모양으로 돌아온다(스와이프된 채로 끼지 않는다). 끝→시작 방향으로는 밀리지 않는다. TalkBack에서 카드의 "작업" 메뉴로도 완료할 수 있다.
11. **다크 모드 + 키보드**: 에디터에서 장소 검색란을 누르면 키보드가 입력란과 결과를 가리지 않는다.
12. **뒤로 연타**: 설정·주변 보기·에디터에서 뒤로를 빠르게 여러 번 눌러도 홈에서 멈춘다.
13. **백업 차단**: `adb shell dumpsys package com.recordofp.app | grep -i backup`에서 `ALLOW_BACKUP` 플래그가 없는지 확인한다.
14. **권한 없이 재부팅**: 위치 권한을 끈 채 `adb reboot` → 진단에 `BOOT → STOOD_DOWN`이 한 줄 남고 `FAILED`가 반복되지 않는다(권한 없이 PendingIntent 단위 해제가 되는지 확인).
15. **센티널 즉시 이탈**(에뮬레이터): 재배치 직후 `adb emu geo fix`로 2km 이상 떨어진 곳으로 옮긴다 → `SENTINEL_EXIT → SKIPPED_DEBOUNCE` → 10분 뒤 `SENTINEL_EXIT → APPLIED`가 남는다.
16. **개편안 2 라이트·다크 스크린샷**: 홈(목록·보호 배너·빈 상태·완료 스와이프·실행 취소 스낵바), 에디터(새 기록·수정·저장 실패), 주변 보기, 근처 알림(단건·여러 항목)을 라이트·다크로 찍어 개편안 2 목업과 비교한다. 다르면 개편안 2 문서에 차이를 적고, 맞으면 개편안 2 머리말 상태를 "완료"로 바꾼다. 실행 취소 스낵바가 FAB 위에 뜨는지(겹치지 않는지)도 본다.
17. **큰 글꼴**: 시스템 글꼴 크기를 최대로 올리고 홈(개수 pill·카드·배너·스낵바)·에디터(칩·[추가])·주변 보기(거리 숫자)를 본다. 글자가 잘리거나 겹치지 않아야 한다. 특히 기록 0개 + '항상 허용' 배너인 첫 화면을 360×780dp급 기기에서 기본·최대 글꼴로 본다 — 빈 상태 본문이 잘리지 않고 스크롤된다.
18. **빠른 설정으로 기기 위치 바꾸기**: 홈을 연 채 알림창에서 위치를 끄면 앱을 떠나지 않아도 배너가 "기기 위치가 꺼져 있어요"로 바뀌고, 다시 켜면 사라진다(설정 화면의 보호 상태도 같다). 켜자마자 진단에 `APP_OPEN`이 남는다(보호 복구 재배치, F1).
19. **TalkBack**: 개수 pill은 "5개", 카테고리 칩은 "편의점, 선택됨", 고른 브랜드 칩은 "GS25 삭제" 버튼(체크박스로 읽지 않는다), '항상 허용' 단계 칩은 쉼표로 끊어 읽는다, 주변 보기 그룹 머리는 제목으로 읽는다. 저장 실패 문구는 나타날 때 자동으로 읽고, 빈 상태 그래픽은 건너뛴다.
20. **근처 알림 모양**: 제목에 📍가 없고("CU 역삼점 근처예요"), 작은 아이콘이 블루다(Android 11 이하는 앱 이름도). 다크 모드에서도 블루가 보인다.
