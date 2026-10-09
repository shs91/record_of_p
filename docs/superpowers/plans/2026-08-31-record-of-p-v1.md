# P의기록 (Record of P) Android v1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설계 문서 v1.0의 범위(§3.1)를 전부 구현해 필드 테스트 가능한 P의기록 Android 앱을 완성한다.

**Architecture:** 순수 Kotlin 엔진(`domain/engine` — 이미 구현·테스트 완료된 ReseedPlanner/NotificationGate)을 중심에 두고, 이번 계획은 그 주변을 채운다: 트리거 해석 → POI 조회 → 차분 적용의 재배치 오케스트레이션(data/engine glue), 지오펜스 이벤트 → 필터 → 그룹 알림 파이프라인(platform), 그리고 Compose UI(기록/목록/주변/설정/온보딩/진단). 새 로직은 전부 "순수 로직 클래스 + JVM 테스트 → 얇은 플랫폼 접착" 패턴을 따른다.

**Tech Stack:** Kotlin 2.1.21, Compose(M3), Hilt, Room, WorkManager, DataStore, play-services-location(GeofencingClient), Retrofit+kotlinx-serialization(카카오 로컬), JUnit4 + kotlinx-coroutines-test + MockWebServer.

**Spec:** `docs/superpowers/specs/2026-08-31-record-of-p-design.md` — 모든 태스크는 이 문서의 §번호를 근거로 한다. 실행자는 스펙을 먼저 읽을 것.

## Global Constraints

- 빌드/테스트 실행은 항상 `android/` 디렉토리에서: `./gradlew testDebugUnitTest` (Windows는 `.\gradlew.bat`). 전체 검증: `./gradlew testDebugUnitTest assembleDebug`.
- `domain/` 아래에는 어떤 Android/GMS import도 금지 (스펙 §5.2). 시간은 `java.time.Clock`/`Instant` 주입.
- 의존 방향: `ui → data/repo → domain`, `platform → domain + data`. ui가 platform을 직접 import하지 않는다 (예외: MainActivity/Receiver 등록 같은 조립 지점).
- 튜닝 상수는 반드시 `EngineParams`에서 참조 — 리터럴 중복 금지 (스펙 §10.2).
- 모든 사용자 노출 문자열은 `res/values/strings.xml`(ko) + `res/values-en/strings.xml`(en) 양쪽에 추가. 하드코딩 금지 (스펙 §8).
- 새 라이브러리 추가는 이 계획에 명시된 것(coroutines-test, mockwebserver, turbine, room-testing 없음 — 아래 Task 1 참조)만. 그 외 추가 금지 (YAGNI).
- 커밋 메시지는 한글 요약 + 타입 프리픽스(`feat:`/`test:`/`refactor:`), 본문에 스펙 §번호 인용. 각 태스크 종료 시 반드시 커밋.
- 위치 데이터를 기기 밖으로 보내는 코드 금지 — 유일한 외부 호출은 `KakaoLocalApi` (스펙 §9).
- 기존 파일의 `TODO(v1): §…` 마커는 해당 태스크에서 구현 후 반드시 제거한다.

## File Structure (이번 계획에서 생성/수정되는 파일)

```
android/app/src/main/java/com/recordofp/app/
├─ domain/engine/
│  ├─ TriggerResolver.kt        [T1 생성] TriggerSpec → TriggerRequest 해석·중복 제거 (순수)
│  ├─ DiffCalculator.kt         [T4 생성] 현재 등록 vs 계획 차분 (순수)
│  └─ ReseedGovernor.kt         [T3 생성] 디바운스·기회적 재배치 판정 (순수)
├─ data/
│  ├─ engine/
│  │  ├─ EngineStateStore.kt    [T3 생성] DataStore: 마지막 재배치 스탬프
│  │  ├─ ReseedService.kt       [T5 생성] 재배치 오케스트레이터
│  │  └─ GeofenceEventHandler.kt[T7 생성] 이벤트→필터→AlertGroup
│  ├─ location/LocationProvider.kt [T6 생성] 위치 취득 포트 (인터페이스)
│  ├─ repo/
│  │  ├─ ReminderRepository.kt  [T8 수정] 트리거 포함 조회 + ReseedRequester 포트
│  │  └─ SettingsStore.kt       [T10 생성, T11 확장] DataStore: 온보딩 상태·알림 정책
│  └─ db/Daos.kt                [T8 수정] ReminderWithTriggers 관계 쿼리
├─ platform/
│  ├─ location/FusedLocationProvider.kt [T6 생성] current→last 폴백 구현
│  ├─ geofence/GeofenceController.kt [T5 수정] 차분 적용 실구현
│  ├─ geofence/GeofenceBroadcastReceiver.kt [T7 수정] goAsync 파이프라인
│  ├─ work/ReseedWorker.kt      [T6 수정] doWork 실구현
│  └─ notify/
│     ├─ NearbyNotifier.kt      [T7 생성] 그룹 알림 빌드·발행
│     └─ NotificationActionReceiver.kt [T7 생성] [완료][오늘 그만] 액션
├─ ui/
│  ├─ permissions/PermissionStatus.kt [T10 생성] 권한 스냅샷 리더
│  ├─ onboarding/OnboardingScreen.kt  [T10 생성] 4단계 온보딩
│  ├─ home/HomeScreen.kt + HomeViewModel.kt [T9 수정/생성]
│  ├─ editor/EditorScreen.kt + EditorViewModel.kt [T9 수정/생성]
│  ├─ nearby/NearbyScreen.kt + NearbyViewModel.kt [T12 수정/생성]
│  ├─ settings/SettingsScreen.kt + SettingsViewModel.kt [T11 수정/생성]
│  ├─ settings/DiagnosticsScreen.kt [T13 생성]
│  └─ AppNavHost.kt             [T9~13 수정] 라우트 추가
└─ (테스트) android/app/src/test/java/com/recordofp/app/… 각 태스크에 명시
```

**태스크 순서 (의존 순):**
- Phase A — 엔진 완성: T1 트리거 해석 → T2 카카오 클라이언트 검증 → T3 거버너/상태 → T4 차분 → T5 재배치 서비스+컨트롤러 → T6 워커/위치
- Phase B — 알림 파이프라인: T7 이벤트 처리·알림·액션
- Phase C — UI: T8 저장소 확장 → T9 홈/에디터 → T10 온보딩/권한 → T11 설정/정책 → T12 주변 보기 → T13 진단 + 마무리 배선

---

### Task 1: TriggerResolver — 활성 트리거를 조회 요청으로 해석 (스펙 §3.3, §6.3)

여러 리마인더가 같은 카테고리를 쓰면 POI 조회는 1번만 해야 한다(쿼터 방어 §6.4). 이 태스크는 `List<TriggerSpec>` → matchKey로 중복 제거된 요청 목록으로 바꾸는 순수 로직을 만든다. 테스트 인프라(coroutines-test, mockwebserver, turbine)도 이 태스크에서 함께 추가한다 — 이후 태스크 전부가 쓴다.

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/domain/engine/TriggerResolver.kt`
- Test: `android/app/src/test/java/com/recordofp/app/domain/engine/TriggerResolverTest.kt`
- Modify: `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts` (테스트 의존성)

**Interfaces:**
- Consumes: `TriggerSpec`, `TriggerType`, `TriggerCatalog`, `PoiResolution`, `GeoPoint` (기존 domain/model)
- Produces (이후 T5, T12가 사용):
  ```kotlin
  sealed interface TriggerRequest { val matchKey: String }
  data class QueryRequest(override val matchKey: String, val resolution: PoiResolution, val query: String) : TriggerRequest
  data class PlaceRequest(override val matchKey: String, val point: GeoPoint, val name: String?) : TriggerRequest
  class TriggerResolver { fun resolve(triggers: List<TriggerSpec>): List<TriggerRequest> }
  ```

- [ ] **Step 1: 테스트 의존성 추가**

`android/gradle/libs.versions.toml`의 `[versions]`에 `coroutinesTest = "1.10.2"`, `mockwebserver = "4.12.0"`, `turbine = "1.2.0"` 추가하고 `[libraries]`에:

```toml
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "mockwebserver" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

`android/app/build.gradle.kts`의 dependencies에:

```kotlin
testImplementation(libs.coroutines.test)
testImplementation(libs.mockwebserver)
testImplementation(libs.turbine)
```

Run: `.\gradlew.bat :app:dependencies --configuration testDebugRuntimeClasspath | Select-String "mockwebserver"` → 해석 성공 확인.

- [ ] **Step 2: 실패하는 테스트 작성**

`TriggerResolverTest.kt`:

```kotlin
package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerResolverTest {

    private val resolver = TriggerResolver()

    private fun cat(id: String, reminderId: Long = 1, specId: Long = reminderId * 10) =
        TriggerSpec(id = specId, reminderId = reminderId, type = TriggerType.CATEGORY, categoryId = id)

    @Test
    fun `카테고리는 카탈로그의 해석 방식으로 변환된다`() {
        val out = resolver.resolve(listOf(cat("convenience"), cat("post", reminderId = 2)))
        val byKey = out.filterIsInstance<QueryRequest>().associateBy { it.matchKey }
        assertEquals(PoiResolution.KAKAO_CODE, byKey.getValue("cat:convenience").resolution)
        assertEquals("CS2", byKey.getValue("cat:convenience").query)
        assertEquals(PoiResolution.KEYWORD, byKey.getValue("cat:post").resolution)
        assertEquals("우체국", byKey.getValue("cat:post").query)
    }

    @Test
    fun `여러 리마인더가 같은 카테고리를 쓰면 요청은 1개다`() {
        val out = resolver.resolve(listOf(cat("convenience", 1), cat("convenience", 2), cat("convenience", 3)))
        assertEquals(1, out.size)
    }

    @Test
    fun `브랜드는 트림·소문자 정규화된 matchKey의 키워드 요청이 된다`() {
        val spec = TriggerSpec(id = 5, reminderId = 1, type = TriggerType.BRAND, brandKeyword = " GS25 ")
        val req = resolver.resolve(listOf(spec)).single() as QueryRequest
        assertEquals("brand:gs25", req.matchKey)
        assertEquals(PoiResolution.KEYWORD, req.resolution)
        assertEquals("GS25", req.query) // 검색어는 원문 트림만
    }

    @Test
    fun `PLACE는 좌표 그대로 PlaceRequest가 된다`() {
        val spec = TriggerSpec(
            id = 7, reminderId = 1, type = TriggerType.PLACE,
            placeName = "우리집 앞 GS25", placePoint = GeoPoint(37.5, 127.0),
        )
        val req = resolver.resolve(listOf(spec)).single() as PlaceRequest
        assertEquals("place:7", req.matchKey)
        assertEquals(GeoPoint(37.5, 127.0), req.point)
        assertEquals("우리집 앞 GS25", req.name)
    }

    @Test
    fun `알 수 없는 카테고리 id와 좌표 없는 PLACE는 조용히 제외된다`() {
        val out = resolver.resolve(
            listOf(
                cat("no-such-id"),
                TriggerSpec(id = 9, reminderId = 1, type = TriggerType.PLACE, placePoint = null),
            ),
        )
        assertTrue(out.isEmpty())
    }
}
```

- [ ] **Step 3: 실패 확인** — Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.recordofp.app.domain.engine.TriggerResolverTest"` → Expected: 컴파일 실패 "Unresolved reference: TriggerResolver".

- [ ] **Step 4: 최소 구현**

`TriggerResolver.kt`:

```kotlin
package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType

/** 재배치·주변 보기의 조회 단위. matchKey는 TriggerSpec.matchKey와 동일 규칙 (스펙 §5.3) */
sealed interface TriggerRequest { val matchKey: String }

data class QueryRequest(
    override val matchKey: String,
    val resolution: PoiResolution,
    val query: String,
) : TriggerRequest

data class PlaceRequest(
    override val matchKey: String,
    val point: GeoPoint,
    val name: String?,
) : TriggerRequest

/** 활성 TriggerSpec 목록을 matchKey 중복 제거된 조회 요청으로 해석한다 (스펙 §3.3, §6.4 쿼터 방어) */
class TriggerResolver {

    fun resolve(triggers: List<TriggerSpec>): List<TriggerRequest> =
        triggers.mapNotNull { toRequest(it) }.distinctBy { it.matchKey }

    private fun toRequest(spec: TriggerSpec): TriggerRequest? = when (spec.type) {
        TriggerType.CATEGORY -> {
            val entry = spec.categoryId?.let { TriggerCatalog.byId(it) } ?: return null
            QueryRequest(spec.matchKey, entry.resolution, entry.query)
        }
        TriggerType.BRAND -> {
            val keyword = spec.brandKeyword?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
            QueryRequest(spec.matchKey, PoiResolution.KEYWORD, keyword)
        }
        TriggerType.PLACE -> {
            val point = spec.placePoint ?: return null
            PlaceRequest(spec.matchKey, point, spec.placeName)
        }
    }
}
```

- [ ] **Step 5: 통과 확인** — Run: 위와 같은 명령 → Expected: 5 tests PASS.

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/java/com/recordofp/app/domain/engine/TriggerResolver.kt android/app/src/test/java/com/recordofp/app/domain/engine/TriggerResolverTest.kt android/gradle/libs.versions.toml android/app/build.gradle.kts
git commit -m "feat: TriggerResolver - 트리거를 중복 제거된 POI 조회 요청으로 해석 (§3.3)"
```

---

### Task 2: KakaoPoiRepository 검증 — MockWebServer 테스트 (스펙 §7, §10.1)

구현은 이미 있다(`data/poi/PoiRepository.kt`). 이 태스크는 실 API 계약(문자열 좌표, is_end, 페이지 병합)에 대한 회귀 방어벽을 세운다. 테스트가 버그를 드러내면 이 태스크 안에서 고친다.

**Files:**
- Test: `android/app/src/test/java/com/recordofp/app/data/poi/KakaoPoiRepositoryTest.kt`
- Modify(버그 발견 시에만): `android/app/src/main/java/com/recordofp/app/data/poi/PoiRepository.kt`

**Interfaces:**
- Consumes: `KakaoLocalApi`, `KakaoPoiRepository.search(resolution, query, center, radiusM, maxResults): List<PoiCandidate>` (T1의 `PoiResolution`, 기존 `GeoPoint`/`PoiCandidate`)
- Produces: 없음 (검증 태스크). 이후 태스크는 `PoiRepository` 인터페이스로만 접근.

- [ ] **Step 1: 실패(또는 통과)하는 테스트 작성**

`KakaoPoiRepositoryTest.kt`:

```kotlin
package com.recordofp.app.data.poi

import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class KakaoPoiRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: KakaoPoiRepository
    private val here = GeoPoint(37.5, 127.0)

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(KakaoLocalApi::class.java)
        repo = KakaoPoiRepository(api)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun body(ids: List<String>, isEnd: Boolean) = """
        {"documents":[${ids.joinToString(",") { doc(it) }}],
         "meta":{"total_count":30,"is_end":$isEnd,"unknown_field":1}}
    """.trimIndent()

    private fun doc(id: String) =
        """{"id":"$id","place_name":"CU $id","category_group_code":"CS2",
            "road_address_name":"테헤란로 1","x":"127.0301","y":"37.4979",
            "distance":"120","place_url":"https://place.map.kakao.com/$id"}"""

    @Test
    fun `문자열 좌표와 거리가 숫자로 파싱된다`() = runTest {
        server.enqueue(MockResponse().setBody(body(listOf("1"), isEnd = true)))
        val out = repo.search(PoiResolution.KAKAO_CODE, "CS2", here)
        val poi = out.single()
        assertEquals(37.4979, poi.point.lat, 1e-9)
        assertEquals(127.0301, poi.point.lng, 1e-9)
        assertEquals(120.0, poi.distanceM, 1e-9)
        // 요청 검증: x=경도, y=위도로 나갔는지
        val path = server.takeRequest().path!!
        assert(path.contains("category_group_code=CS2"))
        assert(path.contains("x=127.0") && path.contains("y=37.5"))
        assert(path.contains("sort=distance"))
    }

    @Test
    fun `is_end=false면 2페이지까지 병합하고 멈춘다`() = runTest {
        server.enqueue(MockResponse().setBody(body((1..15).map { "$it" }, isEnd = false)))
        server.enqueue(MockResponse().setBody(body((16..30).map { "$it" }, isEnd = false)))
        val out = repo.search(PoiResolution.KAKAO_CODE, "CS2", here, maxResults = 30)
        assertEquals(30, out.size) // MAX_QUERY_PAGES=2 — 3페이지 요청 없음
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `is_end=true면 1페이지에서 멈춘다`() = runTest {
        server.enqueue(MockResponse().setBody(body(listOf("1", "2"), isEnd = true)))
        val out = repo.search(PoiResolution.KEYWORD, "다이소", here, maxResults = 20)
        assertEquals(2, out.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `maxResults를 초과한 결과는 잘린다`() = runTest {
        server.enqueue(MockResponse().setBody(body((1..15).map { "$it" }, isEnd = true)))
        assertEquals(5, repo.search(PoiResolution.KAKAO_CODE, "CS2", here, maxResults = 5).size)
    }

    @Test
    fun `좌표가 깨진 문서는 건너뛴다`() = runTest {
        val broken = """{"id":"b","place_name":"x","x":"","y":"","distance":""}"""
        server.enqueue(
            MockResponse().setBody(
                """{"documents":[${doc("1")},$broken],"meta":{"total_count":2,"is_end":true}}""",
            ),
        )
        assertEquals(1, repo.search(PoiResolution.KAKAO_CODE, "CS2", here).size)
    }

    @Test
    fun `HTTP 오류는 예외로 전파된다 - 호출부가 기존 등록 유지 처리 (§6_4)`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        assertThrows(HttpException::class.java) {
            kotlinx.coroutines.runBlocking { repo.search(PoiResolution.KAKAO_CODE, "CS2", here) }
        }
    }
}
```

- [ ] **Step 2: 실행** — Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.recordofp.app.data.poi.KakaoPoiRepositoryTest"` → Expected: 전부 PASS가 정상. 실패하면 테스트가 아니라 `KakaoPoiRepository` 구현을 스펙(§7) 방향으로 수정한다 (예: 페이지 루프가 `out.size < maxResults` 조건 때문에 두 번째 페이지를 안 가져오는 경우 — 루프 조건이 맞는지 확인).

- [ ] **Step 3: 커밋**

```bash
git add android/app/src/test/java/com/recordofp/app/data/poi/KakaoPoiRepositoryTest.kt
git commit -m "test: 카카오 로컬 클라이언트 계약 테스트 - 페이지 병합·좌표 파싱·오류 전파 (§7)"
```

---

### Task 3: ReseedGovernor + EngineStateStore — 디바운스·기회적 실행 판정 (스펙 §6.2)

"언제 재배치하는가"의 판정 로직(순수)과 마지막 재배치 스탬프의 영속화(DataStore)를 만든다.

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/domain/engine/ReseedGovernor.kt`
- Create: `android/app/src/main/java/com/recordofp/app/data/engine/EngineStateStore.kt`
- Test: `android/app/src/test/java/com/recordofp/app/domain/engine/ReseedGovernorTest.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/di/AppModule.kt` (DataStore 제공)

**Interfaces:**
- Produces (T5, T6이 사용):
  ```kotlin
  enum class ReseedCause { BOOT, SENTINEL_EXIT, PERIODIC, APP_OPEN, ITEM_CHANGE, RETRY }
  data class ReseedStamp(val atMs: Long, val point: GeoPoint)
  class ReseedGovernor { fun shouldReseed(cause: ReseedCause, nowMs: Long, last: ReseedStamp?, current: GeoPoint): Boolean }
  class EngineStateStore @Inject constructor(dataStore: DataStore<Preferences>) {
      suspend fun lastReseed(): ReseedStamp?
      suspend fun recordReseed(stamp: ReseedStamp)
  }
  ```

- [ ] **Step 1: 실패하는 테스트 작성**

`ReseedGovernorTest.kt`:

```kotlin
package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReseedGovernorTest {

    private val governor = ReseedGovernor()
    private val origin = GeoPoint(37.5000, 127.0000)
    private val moved600m = GeoPoint(37.5054, 127.0000) // ~600m 북쪽
    private val now = 1_000_000_000_000L
    private fun stamp(ageMs: Long, at: GeoPoint = origin) = ReseedStamp(now - ageMs, at)
    private val min = 60_000L

    @Test
    fun `첫 실행(스탬프 없음)은 원인과 무관하게 허용된다`() {
        ReseedCause.entries.forEach { cause ->
            assertTrue("$cause", governor.shouldReseed(cause, now, last = null, current = origin))
        }
    }

    @Test
    fun `BOOT와 ITEM_CHANGE는 디바운스를 무시한다`() {
        val fresh = stamp(ageMs = 1 * min) // 1분 전 — 10분 미만
        assertTrue(governor.shouldReseed(ReseedCause.BOOT, now, fresh, origin))
        assertTrue(governor.shouldReseed(ReseedCause.ITEM_CHANGE, now, fresh, origin))
    }

    @Test
    fun `SENTINEL_EXIT·PERIODIC·RETRY는 10분 디바운스를 따른다`() {
        listOf(ReseedCause.SENTINEL_EXIT, ReseedCause.PERIODIC, ReseedCause.RETRY).forEach { c ->
            assertFalse("$c 9분", governor.shouldReseed(c, now, stamp(9 * min), origin))
            assertTrue("$c 10분", governor.shouldReseed(c, now, stamp(10 * min), origin))
        }
    }

    @Test
    fun `APP_OPEN은 500m 이상 이동 또는 6시간 경과 시에만, 그리고 10분 디바운스 안에서 허용된다`() {
        // 12분 전, 이동 없음 → 조건 불충족
        assertFalse(governor.shouldReseed(ReseedCause.APP_OPEN, now, stamp(12 * min), origin))
        // 12분 전, 600m 이동 → 허용
        assertTrue(governor.shouldReseed(ReseedCause.APP_OPEN, now, stamp(12 * min), moved600m))
        // 5분 전, 600m 이동 → 디바운스에 걸림
        assertFalse(governor.shouldReseed(ReseedCause.APP_OPEN, now, stamp(5 * min), moved600m))
        // 7시간 전, 이동 없음 → 허용
        assertTrue(governor.shouldReseed(ReseedCause.APP_OPEN, now, stamp(7 * 60 * min), origin))
    }
}
```

- [ ] **Step 2: 실패 확인** — Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.ReseedGovernorTest"` → Expected: "Unresolved reference: ReseedGovernor".

- [ ] **Step 3: 구현**

`ReseedGovernor.kt`:

```kotlin
package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.distanceMeters

enum class ReseedCause { BOOT, SENTINEL_EXIT, PERIODIC, APP_OPEN, ITEM_CHANGE, RETRY }

data class ReseedStamp(val atMs: Long, val point: GeoPoint)

/** 재배치 실행 여부 판정 (스펙 §6.2). current는 판정 시점의 위치. */
class ReseedGovernor {

    fun shouldReseed(cause: ReseedCause, nowMs: Long, last: ReseedStamp?, current: GeoPoint): Boolean {
        if (last == null) return true
        val age = nowMs - last.atMs
        return when (cause) {
            // 지오펜스 소멸(BOOT)·항목 변경은 즉시 반영 — 코얼레싱은 WorkManager 큐가 담당
            ReseedCause.BOOT, ReseedCause.ITEM_CHANGE -> true
            ReseedCause.APP_OPEN -> age >= EngineParams.RESEED_MIN_INTERVAL_MS && (
                distanceMeters(last.point, current) >= EngineParams.APP_OPEN_RESEED_DISTANCE_M ||
                    age >= EngineParams.APP_OPEN_RESEED_AGE_MS
                )
            ReseedCause.SENTINEL_EXIT, ReseedCause.PERIODIC, ReseedCause.RETRY ->
                age >= EngineParams.RESEED_MIN_INTERVAL_MS
        }
    }
}
```

- [ ] **Step 4: 통과 확인** — Run: 위 명령 → Expected: 4 tests PASS.

- [ ] **Step 5: EngineStateStore 구현 + DI**

`EngineStateStore.kt`:

```kotlin
package com.recordofp.app.data.engine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.recordofp.app.domain.engine.ReseedStamp
import com.recordofp.app.domain.model.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class EngineStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val atKey = longPreferencesKey("last_reseed_at")
    private val latKey = doublePreferencesKey("last_reseed_lat")
    private val lngKey = doublePreferencesKey("last_reseed_lng")

    suspend fun lastReseed(): ReseedStamp? {
        val p = dataStore.data.first()
        val at = p[atKey] ?: return null
        val lat = p[latKey] ?: return null
        val lng = p[lngKey] ?: return null
        return ReseedStamp(at, GeoPoint(lat, lng))
    }

    suspend fun recordReseed(stamp: ReseedStamp) {
        dataStore.edit {
            it[atKey] = stamp.atMs
            it[latKey] = stamp.point.lat
            it[lngKey] = stamp.point.lng
        }
    }
}
```

`AppModule.kt`의 object AppModule에 추가 (파일 상단 import: `androidx.datastore.preferences.core.Preferences`, `androidx.datastore.core.DataStore`, `androidx.datastore.preferences.preferencesDataStore`; 파일 최상위에 delegate):

```kotlin
// AppModule.kt 최상위 (클래스 밖)
private val Context.appDataStore by preferencesDataStore(name = "record_of_p_prefs")

// object AppModule 안
@Provides
@Singleton
fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.appDataStore
```

- [ ] **Step 6: 전체 테스트 + 커밋**

Run: `.\gradlew.bat testDebugUnitTest` → Expected: 전부 PASS (기존 19 + T1 5 + T2 6 + T3 4).

```bash
git add android/app/src/main/java/com/recordofp/app/domain/engine/ReseedGovernor.kt android/app/src/main/java/com/recordofp/app/data/engine/EngineStateStore.kt android/app/src/test/java/com/recordofp/app/domain/engine/ReseedGovernorTest.kt android/app/src/main/java/com/recordofp/app/di/AppModule.kt
git commit -m "feat: 재배치 판정 거버너와 엔진 상태 저장 (§6.2)"
```

---

### Task 4: DiffCalculator — 등록 상태와 계획의 차분 (스펙 §6.3.6)

전체 교체 대신 차분 적용으로 이벤트 유실 창을 줄인다. OS 지오펜스는 수정이 불가능하므로 "같은 키인데 좌표/반경이 달라진" 펜스는 제거+추가로 교체한다.

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/domain/engine/DiffCalculator.kt`
- Test: `android/app/src/test/java/com/recordofp/app/domain/engine/DiffCalculatorTest.kt`

**Interfaces:**
- Consumes: `PlannedFence` (기존)
- Produces (T5가 사용):
  ```kotlin
  data class ExistingFence(val key: String, val center: GeoPoint, val radiusM: Float)
  data class FenceDiff(val removeIds: List<String>, val add: List<PlannedFence>) { val isEmpty: Boolean }
  class DiffCalculator { fun diff(current: List<ExistingFence>, planned: List<PlannedFence>): FenceDiff }
  ```
  주의: `ExistingFence.key`는 DB의 `GeofenceRegEntity.geofenceId`와 동일 값이며, `PlannedFence.key`를 그대로 geofenceId로 쓴다 (uuid 발급 안 함 — 안정 키가 차분의 전제).

- [ ] **Step 1: 실패하는 테스트 작성**

`DiffCalculatorTest.kt`:

```kotlin
package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffCalculatorTest {

    private val calc = DiffCalculator()

    private fun planned(key: String, lat: Double = 37.5, radius: Float = 120f) = PlannedFence(
        key = key, kind = FenceKind.POI, center = GeoPoint(lat, 127.0), radiusM = radius,
        transition = FenceTransition.DWELL, loiteringDelayMs = 60_000, matchKeys = setOf("cat:a"),
    )

    private fun existing(key: String, lat: Double = 37.5, radius: Float = 120f) =
        ExistingFence(key, GeoPoint(lat, 127.0), radius)

    @Test
    fun `동일한 키·좌표·반경은 건드리지 않는다`() {
        val diff = calc.diff(listOf(existing("poi:1")), listOf(planned("poi:1")))
        assertTrue(diff.isEmpty)
    }

    @Test
    fun `새 펜스는 add, 사라진 펜스는 remove로 분류된다`() {
        val diff = calc.diff(listOf(existing("poi:old")), listOf(planned("poi:new")))
        assertEquals(listOf("poi:old"), diff.removeIds)
        assertEquals(listOf("poi:new"), diff.add.map { it.key })
    }

    @Test
    fun `같은 키인데 좌표가 달라지면 제거+추가로 교체된다`() {
        val diff = calc.diff(listOf(existing("sentinel", lat = 37.5)), listOf(planned("sentinel", lat = 37.6)))
        assertEquals(listOf("sentinel"), diff.removeIds)
        assertEquals(listOf("sentinel"), diff.add.map { it.key })
    }

    @Test
    fun `같은 키인데 반경이 달라져도 교체된다 - EngineParams 튜닝 반영 경로`() {
        val diff = calc.diff(listOf(existing("poi:1", radius = 120f)), listOf(planned("poi:1", radius = 150f)))
        assertEquals(listOf("poi:1"), diff.removeIds)
        assertEquals(1, diff.add.size)
    }

    @Test
    fun `혼합 시나리오 - 유지 1, 교체 1, 추가 1, 제거 1`() {
        val current = listOf(existing("keep"), existing("move", lat = 37.5), existing("gone"))
        val plannedList = listOf(planned("keep"), planned("move", lat = 37.7), planned("fresh"))
        val diff = calc.diff(current, plannedList)
        assertEquals(setOf("move", "gone"), diff.removeIds.toSet())
        assertEquals(setOf("move", "fresh"), diff.add.map { it.key }.toSet())
    }
}
```

- [ ] **Step 2: 실패 확인** — Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.DiffCalculatorTest"` → Expected: "Unresolved reference: DiffCalculator".

- [ ] **Step 3: 구현**

`DiffCalculator.kt`:

```kotlin
package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint

/** 현재 OS에 등록된 펜스의 도메인 표현 — data 계층이 GeofenceRegEntity에서 변환한다 */
data class ExistingFence(val key: String, val center: GeoPoint, val radiusM: Float)

data class FenceDiff(val removeIds: List<String>, val add: List<PlannedFence>) {
    val isEmpty: Boolean get() = removeIds.isEmpty() && add.isEmpty()
}

/**
 * 차분 적용 계산 (스펙 §6.3.6). 지오펜스는 수정 불가이므로
 * 같은 키의 좌표/반경 변화는 제거+추가로 교체한다.
 * 좌표는 같은 소스(카카오 응답·재배치 좌표)에서 오므로 완전 일치 비교로 충분하다.
 */
class DiffCalculator {

    fun diff(current: List<ExistingFence>, planned: List<PlannedFence>): FenceDiff {
        val currentByKey = current.associateBy { it.key }
        val plannedByKey = planned.associateBy { it.key }

        val toAdd = planned.filter { p ->
            val cur = currentByKey[p.key]
            cur == null || !cur.matches(p)
        }
        val toRemove = current.filter { c ->
            val plan = plannedByKey[c.key]
            plan == null || !c.matches(plan)
        }.map { it.key }

        return FenceDiff(removeIds = toRemove, add = toAdd)
    }

    private fun ExistingFence.matches(p: PlannedFence): Boolean =
        center == p.center && radiusM == p.radiusM
}
```

- [ ] **Step 4: 통과 확인** — Run: 위 명령 → Expected: 5 tests PASS.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/com/recordofp/app/domain/engine/DiffCalculator.kt android/app/src/test/java/com/recordofp/app/domain/engine/DiffCalculatorTest.kt
git commit -m "feat: 지오펜스 차분 계산기 - 유지/추가/제거/교체 분류 (§6.3.6)"
```

---

### Task 5: ReseedService 오케스트레이터 + GeofenceController 실구현 (스펙 §6.3~6.4)

거버너 판정 → 트리거 해석 → POI 조회 → 플랜 → 차분 → OS 적용 → DB 미러 → 스탬프/로그. 실패 시 기존 등록 유지가 핵심 규칙(§6.4). GMS 의존은 `FenceApplier` 인터페이스 뒤로 격리해 서비스를 JVM 테스트한다.

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/data/engine/ReseedService.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceController.kt` (FenceApplier 구현)
- Modify: `android/app/src/main/java/com/recordofp/app/data/db/Daos.kt` (applyReseed 트랜잭션)
- Modify: `android/app/src/main/java/com/recordofp/app/di/AppModule.kt` (순수 클래스 제공 + 바인딩)
- Test: `android/app/src/test/java/com/recordofp/app/data/engine/ReseedServiceTest.kt`

**Interfaces:**
- Consumes: T1 `TriggerResolver/QueryRequest/PlaceRequest`, T3 `ReseedGovernor/ReseedCause/ReseedStamp/EngineStateStore`, T4 `DiffCalculator/ExistingFence/FenceDiff`, 기존 `ReseedPlanner`, `PoiRepository`, `ReminderRepository.activeTriggers()`
- Produces (T6, T7, T12가 사용):
  ```kotlin
  interface FenceApplier { suspend fun apply(diff: FenceDiff) }   // data/engine/ReseedService.kt에 선언
  enum class ReseedResult { APPLIED, SKIPPED_DEBOUNCE, CLEARED_NO_TRIGGERS, FAILED }
  class ReseedService {
      suspend fun reseed(cause: ReseedCause, current: GeoPoint): ReseedResult
  }
  ```
- DAO 추가 (기존 applyDiff는 삭제하고 교체):
  ```kotlin
  @Transaction suspend fun applyReseed(removeIds: List<String>, addRegs: List<GeofenceRegEntity>,
      linkFenceIds: List<String>, links: List<RegTriggerEntity>)
  // 구현: deleteRegTriggers(removeIds + linkFenceIds) → deleteRegs(removeIds) → insertRegs(addRegs) → insertRegTriggers(links)
  ```
  링크는 **계획된 모든 펜스에 대해 매 재배치마다 재계산**한다 — 펜스는 안 변해도 그 펜스를 쓰는 리마인더 집합은 변할 수 있다.

- [ ] **Step 1: DAO 트랜잭션 교체**

`Daos.kt`의 `GeofenceRegDao.applyDiff`를 삭제하고 위 Interfaces 블록의 `applyReseed`로 교체 (본문 4줄, 빈 목록은 각 단계에서 스킵). `deleteRegTriggers(removeIds + linkFenceIds)`는 중복 id가 있어도 무해하다.

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`ReseedServiceTest.kt` — 페이크는 인터페이스 직접 구현:

```kotlin
package com.recordofp.app.data.engine

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import com.recordofp.app.data.db.GeofenceRegDao
import com.recordofp.app.data.db.GeofenceRegEntity
import com.recordofp.app.data.db.RegTriggerEntity
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.DiffCalculator
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.FenceDiff
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.domain.engine.ReseedGovernor
import com.recordofp.app.domain.engine.ReseedPlanner
import com.recordofp.app.domain.engine.TriggerResolver
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeReminders(var triggers: List<TriggerSpec>) : ReminderRepository {
    override fun observeActive(): Flow<List<Reminder>> = emptyFlow()
    override suspend fun upsert(reminder: Reminder) = 0L
    override suspend fun complete(id: Long) {}
    override suspend fun muteUntil(id: Long, untilEpochMs: Long) {}
    override suspend fun delete(id: Long) {}
    override suspend fun activeTriggers() = triggers
}

private class FakePoi(
    var byQuery: Map<String, List<PoiCandidate>> = emptyMap(),
    var throwOn: String? = null,
) : PoiRepository {
    override suspend fun search(
        resolution: PoiResolution, query: String, center: GeoPoint, radiusM: Int, maxResults: Int,
    ): List<PoiCandidate> {
        if (query == throwOn) throw RuntimeException("poi down")
        return byQuery[query].orEmpty()
    }
}

private class FakeRegDao : GeofenceRegDao {
    val regs = mutableMapOf<String, GeofenceRegEntity>()
    val links = mutableListOf<RegTriggerEntity>()
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
        deleteRegTriggers(removeIds + linkFenceIds); deleteRegs(removeIds)
        insertRegs(addRegs); insertRegTriggers(links)
    }
}

private class FakeRunLog : EngineRunLogDao {
    val entries = mutableListOf<EngineRunLogEntity>()
    override suspend fun insert(entity: EngineRunLogEntity) { entries += entity }
    override fun observeRecent(limit: Int): Flow<List<EngineRunLogEntity>> = emptyFlow()
    override suspend fun pruneOlderThan(before: Long) {}
}

private class FakeApplier : FenceApplier {
    val applied = mutableListOf<FenceDiff>()
    override suspend fun apply(diff: FenceDiff) { applied += diff }
}

class ReseedServiceTest {

    private val here = GeoPoint(37.5, 127.0)
    private val convenience = TriggerSpec(id = 10, reminderId = 1, type = TriggerType.CATEGORY, categoryId = "convenience")
    private val place = TriggerSpec(
        id = 20, reminderId = 2, type = TriggerType.PLACE,
        placeName = "회사 우체국", placePoint = GeoPoint(37.51, 127.0),
    )
    private fun poi(id: String, lat: Double) =
        PoiCandidate(id, "CU $id", GeoPoint(lat, 127.0), 100.0)

    private fun build(
        reminders: FakeReminders,
        poiRepo: FakePoi,
        regDao: FakeRegDao = FakeRegDao(),
        runLog: FakeRunLog = FakeRunLog(),
        applier: FakeApplier = FakeApplier(),
        stateStore: FakeStateStore = FakeStateStore(),
    ) = ReseedService(
        reminderRepository = reminders, poiRepository = poiRepo, regDao = regDao,
        runLogDao = runLog, applier = applier, stateStore = stateStore,
        governor = ReseedGovernor(), resolver = TriggerResolver(),
        planner = ReseedPlanner(), differ = DiffCalculator(),
        clock = Clock.fixed(Instant.ofEpochMilli(1_000_000_000_000), ZoneOffset.UTC),
    )

    @Test
    fun `해피 패스 - 펜스 적용, 미러·링크 갱신, 스탬프 기록`() = runTest {
        val regDao = FakeRegDao(); val applier = FakeApplier(); val state = FakeStateStore()
        val service = build(
            FakeReminders(listOf(convenience, place)),
            FakePoi(byQuery = mapOf("CS2" to listOf(poi("1", 37.501), poi("2", 37.503)))),
            regDao = regDao, applier = applier, stateStore = state,
        )
        val result = service.reseed(ReseedCause.BOOT, here)
        assertEquals(ReseedResult.APPLIED, result)
        // 센티널 1 + PLACE 1 + POI 2
        assertEquals(4, regDao.regs.size)
        assertTrue(regDao.regs.containsKey("sentinel"))
        // 링크: POI 펜스 2개는 트리거 10에, PLACE 펜스는 트리거 20에
        assertEquals(setOf(10L), regDao.links.filter { it.geofenceId == "poi:1" }.map { it.triggerId }.toSet())
        assertEquals(setOf(20L), regDao.links.filter { it.geofenceId == "place:20" }.map { it.triggerId }.toSet())
        assertEquals(1, applier.applied.size)
        assertEquals(1_000_000_000_000, state.stamp?.atMs)
    }

    @Test
    fun `POI 조회 실패 시 기존 등록을 유지하고 FAILED를 반환한다`() = runTest {
        val regDao = FakeRegDao().apply {
            regs["poi:old"] = GeofenceRegEntity("poi:old", "POI", 37.5, 127.0, 120f, "CU", "old", "cat:convenience", "b0", 0)
        }
        val applier = FakeApplier()
        val service = build(
            FakeReminders(listOf(convenience)), FakePoi(throwOn = "CS2"),
            regDao = regDao, applier = applier,
        )
        assertEquals(ReseedResult.FAILED, service.reseed(ReseedCause.SENTINEL_EXIT, here))
        assertTrue(applier.applied.isEmpty())          // OS 호출 없음
        assertTrue(regDao.regs.containsKey("poi:old")) // 미러 보존 (§6.4)
    }

    @Test
    fun `디바운스에 걸리면 아무것도 하지 않는다`() = runTest {
        val state = FakeStateStore().apply {
            stamp = com.recordofp.app.domain.engine.ReseedStamp(1_000_000_000_000 - 60_000, here) // 1분 전
        }
        val applier = FakeApplier()
        val service = build(FakeReminders(listOf(convenience)), FakePoi(), stateStore = state, applier = applier)
        assertEquals(ReseedResult.SKIPPED_DEBOUNCE, service.reseed(ReseedCause.PERIODIC, here))
        assertTrue(applier.applied.isEmpty())
    }

    @Test
    fun `활성 트리거가 없으면 등록 전부(센티널 포함)를 걷어낸다`() = runTest {
        val regDao = FakeRegDao().apply {
            regs["sentinel"] = GeofenceRegEntity("sentinel", "SENTINEL", 37.5, 127.0, 1000f, null, null, null, "b0", 0)
            regs["poi:1"] = GeofenceRegEntity("poi:1", "POI", 37.501, 127.0, 120f, "CU", "1", "cat:convenience", "b0", 0)
        }
        val applier = FakeApplier()
        val service = build(FakeReminders(emptyList()), FakePoi(), regDao = regDao, applier = applier)
        assertEquals(ReseedResult.CLEARED_NO_TRIGGERS, service.reseed(ReseedCause.ITEM_CHANGE, here))
        assertTrue(regDao.regs.isEmpty())
        assertEquals(setOf("sentinel", "poi:1"), applier.applied.single().removeIds.toSet())
    }
}

class FakeStateStore : ReseedStateStore {
    var stamp: com.recordofp.app.domain.engine.ReseedStamp? = null
    override suspend fun lastReseed() = stamp
    override suspend fun recordReseed(stamp: com.recordofp.app.domain.engine.ReseedStamp) { this.stamp = stamp }
}
```

주의: 테스트가 `ReseedStateStore` 인터페이스를 요구한다 — Step 3에서 `EngineStateStore`가 구현할 인터페이스로 추출한다(테스트 가능성). 또한 `GeofenceRegEntity` 생성자 순서는 Entities.kt 정의 그대로: `(geofenceId, kind, lat, lng, radiusM, poiName, poiKakaoId, matchKey, reseedBatchId, registeredAt)`.

- [ ] **Step 3: 실패 확인 후 구현**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.ReseedServiceTest"` → 컴파일 실패 확인.

`ReseedService.kt`:

```kotlin
package com.recordofp.app.data.engine

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import com.recordofp.app.data.db.GeofenceRegDao
import com.recordofp.app.data.db.GeofenceRegEntity
import com.recordofp.app.data.db.RegTriggerEntity
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.DiffCalculator
import com.recordofp.app.domain.engine.ExistingFence
import com.recordofp.app.domain.engine.FenceDiff
import com.recordofp.app.domain.engine.FenceKind
import com.recordofp.app.domain.engine.PlaceRequest
import com.recordofp.app.domain.engine.PlannedFence
import com.recordofp.app.domain.engine.QueryRequest
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.domain.engine.ReseedGovernor
import com.recordofp.app.domain.engine.ReseedPlanner
import com.recordofp.app.domain.engine.ReseedStamp
import com.recordofp.app.domain.engine.TriggerCandidates
import com.recordofp.app.domain.engine.TriggerResolver
import com.recordofp.app.domain.model.GeoPoint
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** OS 지오펜스 반영 지점 — GMS 의존을 이 인터페이스 뒤로 격리한다 */
interface FenceApplier {
    suspend fun apply(diff: FenceDiff)
}

/** EngineStateStore가 구현 — 테스트에서 페이크 주입용 */
interface ReseedStateStore {
    suspend fun lastReseed(): ReseedStamp?
    suspend fun recordReseed(stamp: ReseedStamp)
}

enum class ReseedResult { APPLIED, SKIPPED_DEBOUNCE, CLEARED_NO_TRIGGERS, FAILED }

/** 재배치 오케스트레이터 (스펙 §6.2~6.4) */
@Singleton
class ReseedService @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val poiRepository: PoiRepository,
    private val regDao: GeofenceRegDao,
    private val runLogDao: EngineRunLogDao,
    private val applier: FenceApplier,
    private val stateStore: ReseedStateStore,
    private val governor: ReseedGovernor,
    private val resolver: TriggerResolver,
    private val planner: ReseedPlanner,
    private val differ: DiffCalculator,
    private val clock: Clock,
) {

    suspend fun reseed(cause: ReseedCause, current: GeoPoint): ReseedResult {
        val now = clock.millis()
        if (!governor.shouldReseed(cause, now, stateStore.lastReseed(), current)) {
            return log(cause, ReseedResult.SKIPPED_DEBOUNCE, 0, now, null)
        }

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
                            candidates = poiRepository.search(req.resolution, req.query, current),
                        )
                    }
                }
            } catch (e: Exception) {
                // §6.4: 기존 등록 유지, 아무것도 바꾸지 않는다. 재시도는 호출부(Worker) 몫.
                return log(cause, ReseedResult.FAILED, 0, now, e.message)
            }
            planned = planner.plan(current, candidates)
        }

        val existing = regDao.all().map { ExistingFence(it.geofenceId, GeoPoint(it.lat, it.lng), it.radiusM) }
        val diff = differ.diff(existing, planned)
        try {
            applier.apply(diff)
        } catch (e: Exception) {
            return log(cause, ReseedResult.FAILED, 0, now, e.message)
        }

        val batchId = UUID.randomUUID().toString()
        val addRegs = diff.add.map { it.toEntity(batchId, now) }
        val links = planned.flatMap { fence ->
            fence.matchKeys.flatMap { key ->
                triggerIdsByMatchKey[key].orEmpty().map { RegTriggerEntity(fence.key, it) }
            }
        }
        regDao.applyReseed(diff.removeIds, addRegs, linkFenceIds = planned.map { it.key }, links = links)
        stateStore.recordReseed(ReseedStamp(now, current))

        val result = if (planned.isEmpty()) ReseedResult.CLEARED_NO_TRIGGERS else ReseedResult.APPLIED
        return log(cause, result, planned.size, now, null)
    }

    private suspend fun log(
        cause: ReseedCause, result: ReseedResult, count: Int, at: Long, note: String?,
    ): ReseedResult {
        runLogDao.insert(EngineRunLogEntity(at = at, cause = cause.name, result = result.name, registeredCount = count, note = note))
        return result
    }

    private fun PlannedFence.toEntity(batchId: String, at: Long) = GeofenceRegEntity(
        geofenceId = key, kind = kind.name, lat = center.lat, lng = center.lng, radiusM = radiusM,
        poiName = poiName, poiKakaoId = poiId, matchKey = matchKeys.joinToString(","),
        reseedBatchId = batchId, registeredAt = at,
    )
}
```

`EngineStateStore`에 `: ReseedStateStore` 구현 표시 + 메서드에 `override` 추가. `AppModule`에 제공자 추가:

```kotlin
@Provides fun triggerResolver() = TriggerResolver()
@Provides fun reseedPlanner() = ReseedPlanner()
@Provides fun diffCalculator() = DiffCalculator()
@Provides fun reseedGovernor() = ReseedGovernor()
```

`BindsModule`에:

```kotlin
@Binds abstract fun fenceApplier(impl: GeofenceController): FenceApplier
@Binds abstract fun reseedStateStore(impl: EngineStateStore): ReseedStateStore
```

- [ ] **Step 4: 통과 확인** — Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.ReseedServiceTest"` → Expected: 4 tests PASS.

- [ ] **Step 5: GeofenceController 실구현** (JVM 테스트 불가 — 얇게 유지, 로직은 전부 위에서 검증됨)

`GeofenceController.kt`의 `applyPlan` 스텁을 삭제하고 `FenceApplier` 구현으로 교체:

```kotlin
@Singleton
class GeofenceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : FenceApplier {
    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)

    @SuppressLint("MissingPermission") // 호출부(Worker)가 권한 확인 후 진입 (§4.3)
    override suspend fun apply(diff: FenceDiff) {
        if (diff.removeIds.isNotEmpty()) client.removeGeofences(diff.removeIds).await()
        if (diff.add.isEmpty()) return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // 재배치 순간 이미 영역 안이어도 즉발 금지 — 자연 전이만 (§6.5 스팸 방지)
            .addGeofences(diff.add.map { it.toGeofence() })
            .build()
        client.addGeofences(request, geofencePendingIntent()).await()
    }

    private fun PlannedFence.toGeofence(): Geofence = Geofence.Builder()
        .setRequestId(key)
        .setCircularRegion(center.lat, center.lng, radiusM)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .apply {
            when (transition) {
                FenceTransition.ENTER -> setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                FenceTransition.EXIT -> setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                FenceTransition.DWELL -> {
                    setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                    setLoiteringDelay(loiteringDelayMs ?: EngineParams.LOITERING_DELAY_MS)
                }
            }
        }
        .build()

    private fun geofencePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, GeofenceBroadcastReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}
```

필요 import 추가: `com.google.android.gms.location.Geofence`, `GeofencingRequest`, `kotlinx.coroutines.tasks.await`, `com.recordofp.app.data.engine.FenceApplier`, `com.recordofp.app.domain.engine.*`. 기존 `TODO(v1)` 주석 제거.

- [ ] **Step 6: 전체 검증 + 커밋**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug` → Expected: BUILD SUCCESSFUL.

```bash
git add -A android/app/src
git commit -m "feat: 재배치 오케스트레이터와 지오펜스 적용 - 실패 시 기존 등록 유지 (§6.3-6.4)"
```

---

### Task 6: LocationProvider + ReseedWorker 실구현 (스펙 §6.2, §6.4)

위치 취득 폴백 체인(현재→마지막)과 워커의 실행 골격. 워커는 얇은 접착 코드라 JVM 테스트 대상이 아니다 — 판정·오케스트레이션 로직은 T3/T5에서 이미 검증됐다. 이 태스크의 검증은 컴파일 + 전체 테스트 + 에뮬레이터 수동 확인이다.

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/data/location/LocationProvider.kt` (인터페이스 — T9의 ViewModel도 사용하므로 data에 둔다)
- Create: `android/app/src/main/java/com/recordofp/app/platform/location/FusedLocationProvider.kt` (구현)
- Modify: `android/app/src/main/java/com/recordofp/app/platform/work/ReseedWorker.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/geofence/BootReceiver.kt` (ReseedCause 사용)
- Modify: `android/app/src/main/java/com/recordofp/app/di/AppModule.kt` (BindsModule에 LocationProvider 바인딩)

**Interfaces:**
- Produces (T12가 재사용):
  ```kotlin
  interface LocationProvider { suspend fun currentOrLast(): GeoPoint? }
  // ReseedWorker.Companion:
  fun runNow(context: Context, cause: ReseedCause, delayMs: Long = 0L)
  fun schedulePeriodic(context: Context)  // 기존 유지, cause만 enum으로
  ```

- [ ] **Step 1: LocationProvider 구현**

`data/location/LocationProvider.kt` (인터페이스):

```kotlin
package com.recordofp.app.data.location

import com.recordofp.app.domain.model.GeoPoint

interface LocationProvider {
    /** 현재 위치(BALANCED) → 실패 시 마지막 위치 → 둘 다 없으면 null (§6.4) */
    suspend fun currentOrLast(): GeoPoint?
}
```

`platform/location/FusedLocationProvider.kt` (구현):

```kotlin
package com.recordofp.app.platform.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.recordofp.app.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import com.recordofp.app.data.location.LocationProvider

@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext context: Context,
) : LocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // 호출부(Worker)가 권한 확인 후 진입
    override suspend fun currentOrLast(): GeoPoint? {
        val current = runCatching {
            client.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setDurationMillis(15_000)
                    .build(),
                CancellationTokenSource().token,
            ).await()
        }.getOrNull()
        val location = current ?: runCatching { client.lastLocation.await() }.getOrNull() ?: return null
        return GeoPoint(location.latitude, location.longitude)
    }
}
```

`BindsModule`에 `@Binds abstract fun locationProvider(impl: FusedLocationProvider): LocationProvider` 추가.

- [ ] **Step 2: ReseedWorker doWork 구현**

`ReseedWorker.kt` 전체를 다음으로 교체 (기존 TODO 제거):

```kotlin
package com.recordofp.app.platform.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import com.recordofp.app.data.engine.ReseedResult
import com.recordofp.app.data.engine.ReseedService
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.data.location.LocationProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.util.concurrent.TimeUnit

/** 재배치 실행자 (스펙 §6.2). 판정·오케스트레이션은 ReseedService — 여기는 위치·권한·재시도 접착만. */
@HiltWorker
class ReseedWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reseedService: ReseedService,
    private val locationProvider: LocationProvider,
    private val runLogDao: EngineRunLogDao,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cause = inputData.getString(KEY_CAUSE)
            ?.let { runCatching { ReseedCause.valueOf(it) }.getOrNull() }
            ?: ReseedCause.PERIODIC

        val fineGranted = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            // 재시도 무의미 — 권한은 대시보드(§4.3)가 사용자에게 알린다
            runLogDao.insert(
                EngineRunLogEntity(at = clock.millis(), cause = cause.name, result = "NO_PERMISSION", registeredCount = 0, note = null),
            )
            return Result.success()
        }

        val here = locationProvider.currentOrLast()
        if (here == null) {
            runLogDao.insert(
                EngineRunLogEntity(at = clock.millis(), cause = cause.name, result = "NO_LOCATION", registeredCount = 0, note = null),
            )
            return Result.retry() // §6.4 위치 미취득 → 백오프 재시도
        }

        return when (reseedService.reseed(cause, here)) {
            ReseedResult.FAILED -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_ONESHOT = "reseed_now"
        private const val UNIQUE_PERIODIC = "reseed_health_check"
        const val KEY_CAUSE = "cause"

        /** delayMs: ITEM_CHANGE 코얼레싱(§6.2)에 EngineParams.ITEM_CHANGE_COALESCE_MS 전달 */
        fun runNow(context: Context, cause: ReseedCause, delayMs: Long = 0L) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONESHOT,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReseedWorker>()
                    .setInputData(workDataOf(KEY_CAUSE to cause.name))
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    // §6.2 RETRY: 15분 시작 지수 백오프 (WorkManager 표준 — 15m/30m/1h/… 스펙 취지 충족)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build(),
            )
        }

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReseedWorker>(EngineParams.HEALTH_CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                    .setInputData(workDataOf(KEY_CAUSE to ReseedCause.PERIODIC.name))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build(),
            )
        }
    }
}
```

`BootReceiver.kt`의 호출을 `ReseedWorker.runNow(context, cause = ReseedCause.BOOT)`로 바꾸고 import `com.recordofp.app.domain.engine.ReseedCause` 추가. `RecordOfPApp`의 `schedulePeriodic` 호출은 그대로 유효.

- [ ] **Step 3: 전체 검증** — Run: `.\gradlew.bat testDebugUnitTest assembleDebug` → Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 에뮬레이터 수동 확인 (스펙 §10.2 도구 기반)**

Android Studio에서 에뮬레이터 실행 → `local.properties`에 카카오 키 설정 → 앱 설치 후:
1. adb로 위치 지정: `adb emu geo fix 127.0276 37.4979` (강남역 인근)
2. 앱을 열어(아직 UI가 없으므로 T9 이후 이 단계 재수행 예정) Extended controls > Location에서 좌표 이동
3. `adb shell dumpsys activity service com.google.android.gms/.location.persistent.LocationPersistentService | Select-String recordofp` 또는 진단 화면(T13 이후)으로 등록 확인
이 단계에서 완전 검증이 안 되면 "빌드+테스트 통과" 기준으로 커밋하고 T13 후 재검증한다.

- [ ] **Step 5: 커밋**

```bash
git add -A android/app/src
git commit -m "feat: 위치 폴백 체인과 재배치 워커 실구현 - 권한/위치 부재 처리 (§6.2, §6.4)"
```

---

### Task 7: 이벤트 파이프라인 — 핸들러·그룹 알림·액션 (스펙 §6.5, §4.1)

지오펜스 이벤트 → 검증 → NotificationGate 필터(사유 로깅) → POI 그룹핑 → 알림 1건 발행 → [완료][오늘 그만] 액션. "오늘 그만"의 해제 시각 계산은 순수 함수로 분리한다.

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/domain/engine/MuteToday.kt`
- Create: `android/app/src/main/java/com/recordofp/app/data/engine/GeofenceEventHandler.kt`
- Create: `android/app/src/main/java/com/recordofp/app/platform/notify/NearbyNotifier.kt`
- Create: `android/app/src/main/java/com/recordofp/app/platform/notify/NotificationActionReceiver.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/platform/geofence/GeofenceBroadcastReceiver.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/data/db/Daos.kt` (TriggerSpecDao.byIds), `AndroidManifest.xml`(액션 리시버), `di/AppModule.kt`(zone·정책 바인딩), strings.xml(ko/en)
- Test: `android/app/src/test/java/com/recordofp/app/domain/engine/MuteTodayTest.kt`, `android/app/src/test/java/com/recordofp/app/data/engine/GeofenceEventHandlerTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  // domain/engine/MuteToday.kt
  object MuteToday { fun releaseInstant(now: Instant, zone: ZoneId): Instant } // 다음 05:00 (§4.5)
  // data/engine/GeofenceEventHandler.kt
  data class AlertGroup(val poiId: String?, val poiName: String?, val reminders: List<Reminder>)
  data class EventOutcome(val sentinelExited: Boolean, val groups: List<AlertGroup>)
  interface GatePolicyProvider { suspend fun policy(): NotificationGate.Policy } // T11이 SettingsStore 구현으로 교체
  class GeofenceEventHandler { suspend fun onFenceEvent(fenceIds: List<String>): EventOutcome }
  // platform/notify/NearbyNotifier.kt
  class NearbyNotifier { fun show(group: AlertGroup) }
  ```
- DAO 추가: `TriggerSpecDao`에 `@Query("SELECT * FROM trigger_spec WHERE id IN (:ids)") suspend fun byIds(ids: List<Long>): List<TriggerSpecEntity>`

- [ ] **Step 1: MuteToday 테스트 작성 → 실패 확인**

`MuteTodayTest.kt`:

```kotlin
package com.recordofp.app.domain.engine

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MuteTodayTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun local(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant()

    @Test
    fun `새벽 5시 이전이면 오늘 5시에 해제된다`() {
        assertEquals(local(2026, 8, 31, 5, 0), MuteToday.releaseInstant(local(2026, 8, 31, 2, 30), zone))
    }

    @Test
    fun `5시 이후면 다음날 5시에 해제된다`() {
        assertEquals(local(2026, 9, 1, 5, 0), MuteToday.releaseInstant(local(2026, 8, 31, 14, 0), zone))
        assertEquals(local(2026, 9, 1, 5, 0), MuteToday.releaseInstant(local(2026, 8, 31, 5, 0), zone))
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.MuteTodayTest"` → Expected: Unresolved reference.

- [ ] **Step 2: MuteToday 구현 → 통과 확인**

`MuteToday.kt`:

```kotlin
package com.recordofp.app.domain.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** "오늘 그만" 해제 시각: 다음 05:00 (스펙 §4.5) */
object MuteToday {
    fun releaseInstant(now: Instant, zone: ZoneId): Instant {
        val local = now.atZone(zone)
        val todayFive = local.toLocalDate().atTime(LocalTime.of(EngineParams.MUTE_TODAY_RELEASE_HOUR, 0)).atZone(zone)
        val release = if (local.toInstant() < todayFive.toInstant()) todayFive else todayFive.plusDays(1)
        return release.toInstant()
    }
}
```

- [ ] **Step 3: 핸들러 테스트 작성 → 실패 확인**

`GeofenceEventHandlerTest.kt` (페이크는 T5 패턴 — 이 파일 안에 새로 정의):

```kotlin
package com.recordofp.app.data.engine

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import com.recordofp.app.data.db.GeofenceRegDao
import com.recordofp.app.data.db.GeofenceRegEntity
import com.recordofp.app.data.db.NotificationLogDao
import com.recordofp.app.data.db.NotificationLogEntity
import com.recordofp.app.data.db.RegTriggerEntity
import com.recordofp.app.data.db.ReminderDao
import com.recordofp.app.data.db.ReminderEntity
import com.recordofp.app.data.db.TriggerSpecDao
import com.recordofp.app.data.db.TriggerSpecEntity
import com.recordofp.app.domain.engine.NotificationGate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ── 페이크 (필요 메서드만 실동작, 나머지는 최소 구현)
private class FakeRegs : GeofenceRegDao {
    val regs = mutableMapOf<String, GeofenceRegEntity>()
    val links = mutableListOf<RegTriggerEntity>()
    override suspend fun all() = regs.values.toList()
    override suspend fun byId(geofenceId: String) = regs[geofenceId]
    override suspend fun triggerIdsFor(geofenceId: String) =
        links.filter { it.geofenceId == geofenceId }.map { it.triggerId }
    override suspend fun insertRegs(regs: List<GeofenceRegEntity>) = regs.forEach { this.regs[it.geofenceId] = it }
    override suspend fun insertRegTriggers(links: List<RegTriggerEntity>) { this.links += links }
    override suspend fun deleteRegs(ids: List<String>) = ids.forEach { regs.remove(it) }
    override suspend fun deleteRegTriggers(ids: List<String>) { links.removeAll { it.geofenceId in ids } }
    override suspend fun applyReseed(removeIds: List<String>, addRegs: List<GeofenceRegEntity>, linkFenceIds: List<String>, links: List<RegTriggerEntity>) {}
}
private class FakeSpecs(val specs: Map<Long, TriggerSpecEntity>) : TriggerSpecDao {
    override suspend fun byReminder(reminderId: Long) = specs.values.filter { it.reminderId == reminderId }
    override suspend fun allActive() = specs.values.toList()
    override suspend fun byIds(ids: List<Long>) = ids.mapNotNull { specs[it] }
    override suspend fun upsertAll(entities: List<TriggerSpecEntity>) {}
    override suspend fun deleteByReminder(reminderId: Long) {}
}
private class FakeReminderDao(val rows: Map<Long, ReminderEntity>) : ReminderDao {
    override fun observeActive(): Flow<List<ReminderEntity>> = emptyFlow()
    override suspend fun byId(id: Long) = rows[id]
    override suspend fun upsert(entity: ReminderEntity) = 0L
    override suspend fun setStatus(id: Long, status: String, completedAt: Long?, updatedAt: Long) {}
    override suspend fun setSnooze(id: Long, until: Long?, updatedAt: Long) {}
    override suspend fun delete(id: Long) {}
}
private class FakeNotifLog : NotificationLogDao {
    val rows = mutableListOf<NotificationLogEntity>()
    override suspend fun insert(entity: NotificationLogEntity) { rows += entity }
    override suspend fun lastShownForItem(reminderId: Long) =
        rows.filter { it.reminderId == reminderId }.maxOfOrNull { it.shownAt }
    override suspend fun lastShownForItemAtPoi(reminderId: Long, poiKakaoId: String) =
        rows.filter { it.reminderId == reminderId && it.poiKakaoId == poiKakaoId }.maxOfOrNull { it.shownAt }
    override suspend fun countForItemSince(reminderId: Long, since: Long) =
        rows.count { it.reminderId == reminderId && it.shownAt >= since }
    override suspend fun countTotalSince(since: Long) = rows.count { it.shownAt >= since }
}
private class FakeRuns : EngineRunLogDao {
    val entries = mutableListOf<EngineRunLogEntity>()
    override suspend fun insert(entity: EngineRunLogEntity) { entries += entity }
    override fun observeRecent(limit: Int): Flow<List<EngineRunLogEntity>> = emptyFlow()
    override suspend fun pruneOlderThan(before: Long) {}
}

class GeofenceEventHandlerTest {

    private val zone = ZoneId.of("Asia/Seoul")
    // 2026-08-31 낮 12시(KST) — 방해금지 밖
    private val noon = Instant.parse("2026-08-31T03:00:00Z")

    private fun reminder(id: Long, status: String = "ACTIVE") = ReminderEntity(
        id = id, title = "할일$id", memo = null, status = status, snoozeUntil = null,
        createdAt = 0, updatedAt = 0, completedAt = null,
    )
    private fun poiReg(fenceId: String, poiId: String, name: String) = GeofenceRegEntity(
        geofenceId = fenceId, kind = "POI", lat = 37.5, lng = 127.0, radiusM = 120f,
        poiName = name, poiKakaoId = poiId, matchKey = "cat:convenience", reseedBatchId = "b", registeredAt = 0,
    )
    private fun spec(id: Long, reminderId: Long) = TriggerSpecEntity(
        id = id, reminderId = reminderId, type = "CATEGORY", categoryId = "convenience",
        brandKeyword = null, placeName = null, placeKakaoId = null, placeLat = null, placeLng = null,
    )

    private fun build(
        regs: FakeRegs, specs: FakeSpecs, reminders: FakeReminderDao,
        notifLog: FakeNotifLog = FakeNotifLog(), runs: FakeRuns = FakeRuns(),
    ) = GeofenceEventHandler(
        regDao = regs, triggerSpecDao = specs, reminderDao = reminders,
        notificationLogDao = notifLog, runLogDao = runs,
        policyProvider = object : GatePolicyProvider {
            override suspend fun policy() = NotificationGate.Policy()
        },
        gate = NotificationGate(zone), zone = zone,
        clock = Clock.fixed(noon, zone),
    )

    @Test
    fun `통과한 리마인더는 POI 그룹으로 묶이고 NotificationLog가 기록된다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            links += listOf(RegTriggerEntity("poi:100", 11), RegTriggerEntity("poi:100", 12))
        }
        val notifLog = FakeNotifLog()
        val handler = build(
            regs,
            FakeSpecs(mapOf(11L to spec(11, 1), 12L to spec(12, 2))),
            FakeReminderDao(mapOf(1L to reminder(1), 2L to reminder(2))),
            notifLog = notifLog,
        )
        val out = handler.onFenceEvent(listOf("poi:100"))
        val group = out.groups.single()
        assertEquals("CU 역삼점", group.poiName)
        assertEquals(listOf(1L, 2L), group.reminders.map { it.id })
        assertEquals(2, notifLog.rows.size) // 리마인더별 1행 (쿨다운 원본)
    }

    @Test
    fun `차단된 리마인더는 그룹에서 빠지고 사유가 EngineRunLog에 남는다`() = runTest {
        val regs = FakeRegs().apply {
            regs["poi:100"] = poiReg("poi:100", "100", "CU 역삼점")
            links += RegTriggerEntity("poi:100", 11)
        }
        val notifLog = FakeNotifLog().apply {
            rows += NotificationLogEntity(reminderId = 1, poiKakaoId = "999", shownAt = noon.toEpochMilli() - 60_000)
        } // 1분 전 알림 → 항목 쿨다운 4h 차단
        val runs = FakeRuns()
        val handler = build(regs, FakeSpecs(mapOf(11L to spec(11, 1))), FakeReminderDao(mapOf(1L to reminder(1))), notifLog, runs)
        val out = handler.onFenceEvent(listOf("poi:100"))
        assertTrue(out.groups.isEmpty())
        assertTrue(runs.entries.any { it.result == "BLOCK_ITEM_COOLDOWN" })
    }

    @Test
    fun `등록에 없는 stale 이벤트는 무시된다`() = runTest {
        val handler = build(FakeRegs(), FakeSpecs(emptyMap()), FakeReminderDao(emptyMap()))
        val out = handler.onFenceEvent(listOf("poi:ghost"))
        assertTrue(out.groups.isEmpty())
        assertTrue(!out.sentinelExited)
    }

    @Test
    fun `센티널 이탈은 플래그로 보고된다`() = runTest {
        val regs = FakeRegs().apply {
            regs["sentinel"] = GeofenceRegEntity("sentinel", "SENTINEL", 37.5, 127.0, 1000f, null, null, null, "b", 0)
        }
        val out = build(regs, FakeSpecs(emptyMap()), FakeReminderDao(emptyMap())).onFenceEvent(listOf("sentinel"))
        assertTrue(out.sentinelExited)
    }
}
```

Run → Expected: 컴파일 실패 (byIds, GeofenceEventHandler 미존재).

- [ ] **Step 4: 구현**

`TriggerSpecDao`에 `byIds` 쿼리 추가(위 Interfaces 블록 그대로). `GeofenceEventHandler.kt`:

```kotlin
package com.recordofp.app.data.engine

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import com.recordofp.app.data.db.GeofenceRegDao
import com.recordofp.app.data.db.NotificationLogDao
import com.recordofp.app.data.db.NotificationLogEntity
import com.recordofp.app.data.db.ReminderDao
import com.recordofp.app.data.db.TriggerSpecDao
import com.recordofp.app.domain.engine.FenceKind
import com.recordofp.app.domain.engine.NotificationGate
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.ReminderStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class AlertGroup(val poiId: String?, val poiName: String?, val reminders: List<Reminder>)

data class EventOutcome(val sentinelExited: Boolean, val groups: List<AlertGroup>)

/** 알림 정책 공급 — v1 기본값. T11에서 SettingsStore 기반 구현으로 교체된다. */
interface GatePolicyProvider { suspend fun policy(): NotificationGate.Policy }

class DefaultGatePolicyProvider @Inject constructor() : GatePolicyProvider {
    override suspend fun policy() = NotificationGate.Policy()
}

/** 지오펜스 이벤트 → 필터 체인 → POI 그룹 (스펙 §6.5). 발행은 NearbyNotifier 몫. */
@Singleton
class GeofenceEventHandler @Inject constructor(
    private val regDao: GeofenceRegDao,
    private val triggerSpecDao: TriggerSpecDao,
    private val reminderDao: ReminderDao,
    private val notificationLogDao: NotificationLogDao,
    private val runLogDao: EngineRunLogDao,
    private val policyProvider: GatePolicyProvider,
    private val gate: NotificationGate,
    private val zone: ZoneId,
    private val clock: Clock,
) {

    suspend fun onFenceEvent(fenceIds: List<String>): EventOutcome {
        val now = clock.instant()
        val startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val policy = policyProvider.policy()
        var sentinelExited = false
        val groups = mutableListOf<AlertGroup>()

        for (fenceId in fenceIds) {
            val reg = regDao.byId(fenceId) ?: continue // stale 이벤트 폐기 (§6.5.1)
            if (reg.kind == FenceKind.SENTINEL.name) { sentinelExited = true; continue }

            val reminderIds = triggerSpecDao.byIds(regDao.triggerIdsFor(fenceId))
                .map { it.reminderId }.distinct()
            val passed = mutableListOf<Reminder>()

            for (reminderId in reminderIds) {
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
                    notificationLogDao.insert(
                        NotificationLogEntity(reminderId = reminderId, poiKakaoId = reg.poiKakaoId, shownAt = now.toEpochMilli()),
                    )
                } else {
                    runLogDao.insert(
                        EngineRunLogEntity(
                            at = now.toEpochMilli(), cause = "FENCE_EVENT", result = decision.name,
                            registeredCount = 0, note = "reminder=$reminderId poi=${reg.poiName}",
                        ),
                    )
                }
            }
            if (passed.isNotEmpty()) groups += AlertGroup(reg.poiKakaoId, reg.poiName, passed)
        }
        return EventOutcome(sentinelExited, groups)
    }
}
```

`AppModule`에 `@Provides fun notificationGate(zone: ZoneId) = NotificationGate(zone)` 추가, `BindsModule`에 `@Binds abstract fun gatePolicyProvider(impl: DefaultGatePolicyProvider): GatePolicyProvider` 추가.

- [ ] **Step 5: 통과 확인** — Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.GeofenceEventHandlerTest"` → 4 tests PASS.

- [ ] **Step 6: NearbyNotifier + 액션 리시버 + 리시버 배선**

strings.xml(ko)에 추가 — values-en에도 대응 번역(`%1$s is nearby (~%2$sm)` 등) 필수:

```xml
<string name="notif_nearby_title">📍 %1$s 근처예요</string>
<string name="notif_more_items">'%1$s' 외 %2$d건</string>
<string name="action_complete">완료</string>
<string name="action_mute_today">오늘 그만</string>
```

`NearbyNotifier.kt`:

```kotlin
package com.recordofp.app.platform.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.recordofp.app.MainActivity
import com.recordofp.app.R
import com.recordofp.app.data.engine.AlertGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun show(group: AlertGroup) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notificationId = (group.poiId ?: group.poiName ?: "poi").hashCode()
        val first = group.reminders.first()
        val text = if (group.reminders.size == 1) first.title
        else context.getString(R.string.notif_more_items, first.title, group.reminders.size - 1)

        val builder = NotificationCompat.Builder(context, Notifier.CHANNEL_NEARBY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_nearby_title, group.poiName ?: ""))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, notificationId, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        if (group.reminders.size == 1) {
            builder
                .addAction(0, context.getString(R.string.action_complete),
                    NotificationActionReceiver.pendingIntent(context, NotificationActionReceiver.ACTION_COMPLETE, first.id, notificationId))
                .addAction(0, context.getString(R.string.action_mute_today),
                    NotificationActionReceiver.pendingIntent(context, NotificationActionReceiver.ACTION_MUTE_TODAY, first.id, notificationId))
        }
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
```

`NotificationActionReceiver.kt`:

```kotlin
package com.recordofp.app.platform.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.MuteToday
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 알림 액션 [완료][오늘 그만] (스펙 §4.1.2) */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var clock: Clock
    @Inject lateinit var zone: ZoneId

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_COMPLETE -> repository.complete(reminderId)
                    ACTION_MUTE_TODAY -> repository.muteUntil(
                        reminderId, MuteToday.releaseInstant(clock.instant(), zone).toEpochMilli(),
                    )
                }
                NotificationManagerCompat.from(context).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.recordofp.app.action.COMPLETE"
        const val ACTION_MUTE_TODAY = "com.recordofp.app.action.MUTE_TODAY"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pendingIntent(context: Context, action: String, reminderId: Long, notificationId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                (action + reminderId).hashCode(),
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_REMINDER_ID, reminderId)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
```

`GeofenceBroadcastReceiver.kt` 교체 (TODO 제거):

```kotlin
package com.recordofp.app.platform.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.GeofencingEvent
import com.recordofp.app.data.engine.GeofenceEventHandler
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.platform.notify.NearbyNotifier
import com.recordofp.app.platform.work.ReseedWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 지오펜스 전이 이벤트 진입점 (스펙 §6.5) */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: GeofenceEventHandler
    @Inject lateinit var notifier: NearbyNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val ids = event.triggeringGeofences?.map { it.requestId } ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outcome = handler.onFenceEvent(ids)
                outcome.groups.forEach { notifier.show(it) }
                if (outcome.sentinelExited) ReseedWorker.runNow(context, ReseedCause.SENTINEL_EXIT)
            } finally {
                pending.finish()
            }
        }
    }
}
```

`AndroidManifest.xml`의 application 안에 추가:

```xml
<receiver android:name=".platform.notify.NotificationActionReceiver" android:exported="false" />
```

- [ ] **Step 7: 전체 검증 + 커밋**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL.

```bash
git add -A android/app/src
git commit -m "feat: 지오펜스 이벤트 파이프라인 - 필터 사유 로깅·그룹 알림·완료/오늘그만 액션 (§6.5)"
```

---

### Task 8: 저장소 확장 — 트리거 포함 조회 + ITEM_CHANGE 재배치 포트 (스펙 §4.1, §6.2)

홈 화면은 항목마다 트리거 칩을 보여줘야 하고(§4.1), 항목이 바뀌면 재배치가 예약되어야 한다(§6.2 ITEM_CHANGE). data 계층이 platform(WorkManager)을 모르도록 `ReseedRequester` 포트를 data에 두고 platform이 구현한다.

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/data/db/Entities.kt` (ReminderWithTriggers), `Daos.kt` (관계 쿼리)
- Modify: `android/app/src/main/java/com/recordofp/app/data/repo/ReminderRepository.kt`
- Create: `android/app/src/main/java/com/recordofp/app/platform/work/WorkManagerReseedRequester.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/di/AppModule.kt` (바인딩)
- Test: `android/app/src/test/java/com/recordofp/app/data/repo/RoomReminderRepositoryTest.kt`

**Interfaces:**
- Produces (T9~T12가 사용):
  ```kotlin
  // data/repo/ReminderRepository.kt에 추가
  interface ReseedRequester { fun requestItemChange() }
  // ReminderRepository.observeActive(): Flow<List<Reminder>> — 이제 triggers 필드가 채워져 온다
  // db: ReminderDao에 추가
  @Transaction @Query("SELECT * FROM reminder WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
  fun observeActiveWithTriggers(): Flow<List<ReminderWithTriggers>>
  ```

- [ ] **Step 1: 실패하는 테스트 작성**

`RoomReminderRepositoryTest.kt`:

```kotlin
package com.recordofp.app.data.repo

import com.recordofp.app.data.db.ReminderDao
import com.recordofp.app.data.db.ReminderEntity
import com.recordofp.app.data.db.ReminderWithTriggers
import com.recordofp.app.data.db.TriggerSpecDao
import com.recordofp.app.data.db.TriggerSpecEntity
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeDao : ReminderDao {
    val flow = MutableStateFlow<List<ReminderWithTriggers>>(emptyList())
    val statusCalls = mutableListOf<Pair<Long, String>>()
    override fun observeActive(): Flow<List<ReminderEntity>> = MutableStateFlow(emptyList())
    override fun observeActiveWithTriggers(): Flow<List<ReminderWithTriggers>> = flow
    override suspend fun byId(id: Long): ReminderEntity? = null
    override suspend fun upsert(entity: ReminderEntity): Long = 42L
    override suspend fun setStatus(id: Long, status: String, completedAt: Long?, updatedAt: Long) {
        statusCalls += id to status
    }
    override suspend fun setSnooze(id: Long, until: Long?, updatedAt: Long) {}
    override suspend fun delete(id: Long) {}
}

private class FakeSpecDao : TriggerSpecDao {
    val upserted = mutableListOf<TriggerSpecEntity>()
    override suspend fun byReminder(reminderId: Long) = emptyList<TriggerSpecEntity>()
    override suspend fun allActive() = emptyList<TriggerSpecEntity>()
    override suspend fun byIds(ids: List<Long>) = emptyList<TriggerSpecEntity>()
    override suspend fun upsertAll(entities: List<TriggerSpecEntity>) { upserted += entities }
    override suspend fun deleteByReminder(reminderId: Long) {}
}

private class RecordingRequester : ReseedRequester {
    var count = 0
    override fun requestItemChange() { count++ }
}

class RoomReminderRepositoryTest {

    private val dao = FakeDao()
    private val specDao = FakeSpecDao()
    private val requester = RecordingRequester()
    private val repo = RoomReminderRepository(
        dao, specDao, requester, Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC),
    )

    @Test
    fun `observeActive는 트리거가 채워진 도메인 모델을 낸다`() = runTest {
        dao.flow.value = listOf(
            ReminderWithTriggers(
                reminder = ReminderEntity(1, "건전지", null, "ACTIVE", null, 0, 0, null),
                triggers = listOf(
                    TriggerSpecEntity(10, 1, "CATEGORY", "convenience", null, null, null, null, null),
                ),
            ),
        )
        val item = repo.observeActive().first().single()
        assertEquals("건전지", item.title)
        assertEquals("cat:convenience", item.triggers.single().matchKey)
    }

    @Test
    fun `upsert는 트리거를 새 reminderId로 저장하고 재배치를 요청한다`() = runTest {
        val id = repo.upsert(
            Reminder(
                id = 0, title = "휴지", createdAt = 0, updatedAt = 0,
                triggers = listOf(TriggerSpec(type = TriggerType.CATEGORY, categoryId = "mart")),
            ),
        )
        assertEquals(42L, id)
        assertEquals(42L, specDao.upserted.single().reminderId)
        assertEquals(1, requester.count)
    }

    @Test
    fun `complete와 delete도 재배치를 요청한다`() = runTest {
        repo.complete(1)
        repo.delete(2)
        assertEquals(listOf(1L to "DONE"), dao.statusCalls)
        assertEquals(2, requester.count)
    }
}
```

- [ ] **Step 2: 실패 확인** — Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.RoomReminderRepositoryTest"` → Expected: 컴파일 실패 (ReminderWithTriggers, ReseedRequester, 생성자 시그니처).

- [ ] **Step 3: 구현**

`Entities.kt` 끝에 추가:

```kotlin
// (파일 상단 import에 androidx.room.Embedded, androidx.room.Relation 추가)
data class ReminderWithTriggers(
    @Embedded val reminder: ReminderEntity,
    @Relation(parentColumn = "id", entityColumn = "reminderId")
    val triggers: List<TriggerSpecEntity>,
)
```

`ReminderDao`에 Interfaces 블록의 `observeActiveWithTriggers` 추가 (`androidx.room.Transaction` import).

`ReminderRepository.kt` 수정 — 인터페이스 위에 포트 선언, 구현 교체:

```kotlin
/** 항목 변경 → 재배치 예약 포트. platform의 WorkManagerReseedRequester가 구현 (§6.2 ITEM_CHANGE) */
interface ReseedRequester { fun requestItemChange() }
```

`RoomReminderRepository` 변경점:
- 생성자: `(reminderDao, triggerSpecDao, reseedRequester: ReseedRequester, clock)`
- `observeActive()`:
  ```kotlin
  override fun observeActive(): Flow<List<Reminder>> =
      reminderDao.observeActiveWithTriggers().map { list ->
          list.map { row -> row.reminder.toDomain().copy(triggers = row.triggers.map { it.toDomain() }) }
      }
  ```
  (기존 "트리거 조인 쿼리로 확장한다" 주석 삭제)
- `upsert` 끝에 `reseedRequester.requestItemChange()` 후 `return reminderId`, `complete`·`delete` 끝에도 `reseedRequester.requestItemChange()`. `muteUntil`은 펜스 구성이 안 변하므로 호출하지 않는다.

`WorkManagerReseedRequester.kt`:

```kotlin
package com.recordofp.app.platform.work

import android.content.Context
import com.recordofp.app.data.repo.ReseedRequester
import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.ReseedCause
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 30초 지연으로 연속 편집을 코얼레싱 — 유니크 큐(REPLACE)가 마지막 요청만 남긴다 (§6.2) */
@Singleton
class WorkManagerReseedRequester @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReseedRequester {
    override fun requestItemChange() =
        ReseedWorker.runNow(context, ReseedCause.ITEM_CHANGE, delayMs = EngineParams.ITEM_CHANGE_COALESCE_MS)
}
```

`BindsModule`에 `@Binds abstract fun reseedRequester(impl: WorkManagerReseedRequester): ReseedRequester` 추가.

- [ ] **Step 4: 통과 확인** — Run: `.\gradlew.bat testDebugUnitTest` → 전부 PASS (T5의 FakeReminders는 인터페이스 직접 구현이라 영향 없음).

- [ ] **Step 5: 커밋**

```bash
git add -A android/app/src
git commit -m "feat: 트리거 포함 조회와 항목 변경 재배치 포트 (§4.1, §6.2)"
```

---

### Task 9: 홈 목록 + 에디터 — 기록 핵심 플로우 (스펙 §4.1)

"3탭 + 타이핑 이내" 기록 플로우. 에디터는 카테고리 칩 다중 선택 + 브랜드 자유 입력 + 특정 장소 검색(키워드)을 지원한다. 홈은 트리거 칩이 달린 활성 목록 + 스와이프 완료. 앱 진입 시 APP_OPEN 재배치도 이 태스크에서 건다.

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/ui/common/CatalogLabels.kt`
- Create: `android/app/src/main/java/com/recordofp/app/ui/home/HomeViewModel.kt`
- Create: `android/app/src/main/java/com/recordofp/app/ui/editor/EditorViewModel.kt`
- Modify: `android/app/src/main/java/com/recordofp/app/ui/home/HomeScreen.kt`, `ui/editor/EditorScreen.kt`, `MainActivity.kt`, strings.xml(ko/en)
- Test: `android/app/src/test/java/com/recordofp/app/ui/editor/EditorViewModelTest.kt`, `android/app/src/test/java/com/recordofp/app/ui/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `ReminderRepository`(T8 — triggers 포함), `PoiRepository`, `LocationProvider`(T6, data/location), `TriggerCatalog`
- Produces:
  ```kotlin
  // ui/editor/EditorViewModel.kt
  data class PickedPlace(val name: String, val kakaoId: String, val point: GeoPoint)
  data class EditorUiState(
      val title: String = "", val memo: String = "",
      val selectedCategoryIds: Set<String> = emptySet(),
      val brandKeywords: List<String> = emptyList(),
      val place: PickedPlace? = null,
      val placeQuery: String = "", val placeResults: List<PoiCandidate> = emptyList(),
      val placeSearchFailed: Boolean = false, val saved: Boolean = false,
  ) { val canSave: Boolean get() = title.isNotBlank() &&
        (selectedCategoryIds.isNotEmpty() || brandKeywords.isNotEmpty() || place != null) }
  ```

- [ ] **Step 1: 카탈로그 라벨 + 문자열 리소스**

strings.xml(ko)에 (values-en에는 영문 대응 — Convenience store, Supermarket, Pharmacy, Bank, Post office, Gas station, Laundry, Cafe, Hospital, Subway, Daiso, Olive Young):

```xml
<string name="cat_convenience">편의점</string>
<string name="cat_mart">대형마트</string>
<string name="cat_pharmacy">약국</string>
<string name="cat_bank">은행</string>
<string name="cat_post">우체국</string>
<string name="cat_fuel">주유소</string>
<string name="cat_laundry">세탁소</string>
<string name="cat_cafe">카페</string>
<string name="cat_hospital">병원</string>
<string name="cat_subway">지하철역</string>
<string name="cat_daiso">다이소</string>
<string name="cat_oliveyoung">올리브영</string>
<string name="editor_title_hint">무엇을 해야 하나요?</string>
<string name="editor_memo_hint">메모 (선택)</string>
<string name="editor_section_category">어디서 알려드릴까요?</string>
<string name="editor_brand_hint">브랜드 추가 (예: GS25)</string>
<string name="editor_place_hint">특정 장소 검색</string>
<string name="editor_place_search_failed">장소를 검색하지 못했어요. 위치와 네트워크를 확인해주세요.</string>
<string name="editor_save">저장</string>
<string name="home_empty">아직 기록이 없어요.\n생각난 순간, 여기에 적어두세요.</string>
<string name="home_completed">완료했어요</string>
```

`CatalogLabels.kt`:

```kotlin
package com.recordofp.app.ui.common

import androidx.annotation.StringRes
import com.recordofp.app.R

@StringRes
fun catalogLabelRes(categoryId: String): Int = when (categoryId) {
    "convenience" -> R.string.cat_convenience
    "mart" -> R.string.cat_mart
    "pharmacy" -> R.string.cat_pharmacy
    "bank" -> R.string.cat_bank
    "post" -> R.string.cat_post
    "fuel" -> R.string.cat_fuel
    "laundry" -> R.string.cat_laundry
    "cafe" -> R.string.cat_cafe
    "hospital" -> R.string.cat_hospital
    "subway" -> R.string.cat_subway
    "daiso" -> R.string.cat_daiso
    "oliveyoung" -> R.string.cat_oliveyoung
    else -> R.string.cat_convenience // 카탈로그에 없는 id는 저장 경로에서 걸러진다
}
```

- [ ] **Step 2: EditorViewModel 실패하는 테스트**

`EditorViewModelTest.kt`:

```kotlin
package com.recordofp.app.ui.editor

import com.recordofp.app.data.location.LocationProvider
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    private class FakeRepo : ReminderRepository {
        var saved: Reminder? = null
        override fun observeActive(): Flow<List<Reminder>> = emptyFlow()
        override suspend fun upsert(reminder: Reminder): Long { saved = reminder; return 1 }
        override suspend fun complete(id: Long) {}
        override suspend fun muteUntil(id: Long, untilEpochMs: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun activeTriggers() = emptyList<com.recordofp.app.domain.model.TriggerSpec>()
    }

    private class FakePoi : PoiRepository {
        override suspend fun search(
            resolution: PoiResolution, query: String, center: GeoPoint, radiusM: Int, maxResults: Int,
        ) = listOf(PoiCandidate("k1", "$query 역삼점", GeoPoint(37.49, 127.03), 300.0))
    }

    private val repo = FakeRepo()

    private fun vm(location: GeoPoint? = GeoPoint(37.5, 127.0)) = EditorViewModel(
        repository = repo, poiRepository = FakePoi(),
        locationProvider = object : LocationProvider {
            override suspend fun currentOrLast() = location
        },
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `제목과 트리거가 있어야 저장 가능하다`() = runTest {
        val vm = vm()
        assertTrue(!vm.state.value.canSave)
        vm.onTitleChange("건전지 사기")
        assertTrue(!vm.state.value.canSave)
        vm.toggleCategory("convenience")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `저장하면 선택이 트리거 스펙으로 매핑된다`() = runTest {
        val vm = vm()
        vm.onTitleChange("휴지")
        vm.toggleCategory("convenience")
        vm.toggleCategory("mart")
        vm.addBrand(" GS25 ")
        vm.pickPlace(PickedPlace("우리집 앞 CU", "k9", GeoPoint(37.51, 127.0)))
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        val saved = repo.saved!!
        assertEquals("휴지", saved.title)
        val byType = saved.triggers.groupBy { it.type }
        assertEquals(setOf("convenience", "mart"), byType.getValue(TriggerType.CATEGORY).map { it.categoryId }.toSet())
        assertEquals(listOf("GS25"), byType.getValue(TriggerType.BRAND).map { it.brandKeyword })
        assertEquals("k9", byType.getValue(TriggerType.PLACE).single().placeKakaoId)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `장소 검색은 키워드 결과를 상태에 싣는다`() = runTest {
        val vm = vm()
        vm.onPlaceQueryChange("CU")
        vm.searchPlace()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("CU 역삼점", vm.state.value.placeResults.single().name)
    }

    @Test
    fun `위치를 못 얻으면 검색 실패 플래그가 선다`() = runTest {
        val vm = vm(location = null)
        vm.onPlaceQueryChange("CU")
        vm.searchPlace()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.placeSearchFailed)
    }
}
```

- [ ] **Step 3: 실패 확인** — Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.EditorViewModelTest"` → Unresolved reference.

- [ ] **Step 4: EditorViewModel 구현**

```kotlin
package com.recordofp.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordofp.app.data.location.LocationProvider
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickedPlace(val name: String, val kakaoId: String, val point: GeoPoint)

data class EditorUiState(
    val title: String = "",
    val memo: String = "",
    val selectedCategoryIds: Set<String> = emptySet(),
    val brandKeywords: List<String> = emptyList(),
    val place: PickedPlace? = null,
    val placeQuery: String = "",
    val placeResults: List<PoiCandidate> = emptyList(),
    val placeSearchFailed: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean
        get() = title.isNotBlank() &&
            (selectedCategoryIds.isNotEmpty() || brandKeywords.isNotEmpty() || place != null)
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val poiRepository: PoiRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onMemoChange(v: String) = _state.update { it.copy(memo = v) }
    fun toggleCategory(id: String) = _state.update {
        val s = it.selectedCategoryIds
        it.copy(selectedCategoryIds = if (id in s) s - id else s + id)
    }
    fun addBrand(keyword: String) {
        val k = keyword.trim()
        if (k.isEmpty()) return
        _state.update { if (k in it.brandKeywords) it else it.copy(brandKeywords = it.brandKeywords + k) }
    }
    fun removeBrand(keyword: String) = _state.update { it.copy(brandKeywords = it.brandKeywords - keyword) }
    fun onPlaceQueryChange(v: String) = _state.update { it.copy(placeQuery = v, placeSearchFailed = false) }
    fun pickPlace(place: PickedPlace) = _state.update { it.copy(place = place, placeResults = emptyList(), placeQuery = "") }
    fun clearPlace() = _state.update { it.copy(place = null) }

    fun searchPlace() {
        val query = _state.value.placeQuery.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            val here = locationProvider.currentOrLast()
            if (here == null) {
                _state.update { it.copy(placeSearchFailed = true) }
                return@launch
            }
            runCatching {
                poiRepository.search(PoiResolution.KEYWORD, query, here, radiusM = 20_000, maxResults = 10)
            }.onSuccess { results ->
                _state.update { it.copy(placeResults = results, placeSearchFailed = false) }
            }.onFailure {
                _state.update { it.copy(placeSearchFailed = true) }
            }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
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
            repository.upsert(
                Reminder(title = s.title.trim(), memo = s.memo.trim().ifEmpty { null }, createdAt = 0, updatedAt = 0, triggers = triggers),
            )
            _state.update { it.copy(saved = true) }
        }
    }
}
```

(createdAt/updatedAt은 저장소가 clock으로 덮어쓴다 — T8의 toEntity 로직.)

- [ ] **Step 5: 통과 확인** — Run: Editor 테스트 → 4 PASS.

- [ ] **Step 6: HomeViewModel + 테스트**

`HomeViewModelTest.kt` (실패 확인 후 구현 순서 동일):

```kotlin
package com.recordofp.app.ui.home

import app.cash.turbine.test
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.model.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepo : ReminderRepository {
        val flow = MutableStateFlow<List<Reminder>>(emptyList())
        val completed = mutableListOf<Long>()
        override fun observeActive(): Flow<List<Reminder>> = flow
        override suspend fun upsert(reminder: Reminder) = 0L
        override suspend fun complete(id: Long) { completed += id }
        override suspend fun muteUntil(id: Long, untilEpochMs: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun activeTriggers() = emptyList<com.recordofp.app.domain.model.TriggerSpec>()
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `목록을 구독하고 완료를 저장소에 위임한다`() = runTest {
        val repo = FakeRepo()
        val vm = HomeViewModel(repo)
        vm.items.test {
            assertEquals(0, awaitItem().size)
            repo.flow.value = listOf(Reminder(id = 1, title = "휴지", createdAt = 0, updatedAt = 0))
            assertEquals("휴지", awaitItem().single().title)
        }
        vm.complete(1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(1L), repo.completed)
    }
}
```

`HomeViewModel.kt`:

```kotlin
package com.recordofp.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.model.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ReminderRepository,
) : ViewModel() {

    val items: StateFlow<List<Reminder>> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun complete(id: Long) = viewModelScope.launch { repository.complete(id) }
}
```

- [ ] **Step 7: 화면 구현 (수동 확인 대상)**

`HomeScreen.kt` 교체 — 핵심 구조 (TODO 주석 제거):

```kotlin
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onNearbyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.title_home)) },
                actions = {
                    IconButton(onClick = onNearbyClick) { Icon(Icons.Filled.Place, stringResource(R.string.title_nearby)) }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Filled.Settings, stringResource(R.string.title_settings)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.home_empty), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    ReminderRow(item, onComplete = { viewModel.complete(item.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderRow(item: Reminder, onComplete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) { onComplete(); true } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.home_completed))
            }
        },
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                if (item.triggers.isNotEmpty()) {
                    Text(
                        text = item.triggers.joinToString("  ") { it.chipLabel() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TriggerSpec.chipLabel(): String = when (type) {
    TriggerType.CATEGORY -> {
        val entry = categoryId?.let { TriggerCatalog.byId(it) }
        "${entry?.emoji ?: ""} ${entry?.let { stringResource(catalogLabelRes(it.id)) } ?: ""}"
    }
    TriggerType.BRAND -> "🔎 $brandKeyword"
    TriggerType.PLACE -> "📌 $placeName"
}
```

`EditorScreen.kt` 교체 — 구조: 제목/메모 `OutlinedTextField`, 카테고리는 `FlowRow`(`androidx.compose.foundation.layout.FlowRow`)에 `FilterChip`(선택 상태 = `id in state.selectedCategoryIds`, 라벨 = `"${entry.emoji} ${stringResource(catalogLabelRes(entry.id))}"`, `TriggerCatalog.entries` 전체), 브랜드는 입력 필드 + 추가 버튼 + 선택된 것 `InputChip`(X로 removeBrand), 장소는 검색 필드 + 검색 버튼 + 결과 `ListItem`(name, distance "약 %dm") 탭 시 `pickPlace(PickedPlace(name, id, point))`, 선택된 장소는 `InputChip`(X로 clearPlace), 실패 시 `editor_place_search_failed` 텍스트. 하단 `Button(enabled = state.canSave)`으로 저장. `LaunchedEffect(state.saved) { if (state.saved) onDone() }`. 전체 Column은 `verticalScroll(rememberScrollState())`.

`MainActivity.kt`의 `onCreate` 끝에 APP_OPEN 훅 추가:

```kotlin
// 앱 진입 시 기회적 재배치 (§6.2 APP_OPEN) — 판정은 거버너가 한다
ReseedWorker.runNow(this, ReseedCause.APP_OPEN)
```

- [ ] **Step 8: 전체 검증 + 수동 확인 + 커밋**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL. 에뮬레이터에서: 항목 생성(카테고리 2개+브랜드) → 홈에 칩 표시 → 스와이프 완료 확인.

```bash
git add -A android/app/src
git commit -m "feat: 홈 목록과 에디터 - 카테고리/브랜드/장소 트리거 기록 플로우 (§4.1)"
```

---

### Task 10: 점진적 권한 온보딩 + 홈 보호 배너 (스펙 §4.2, §4.3 일부)

가치 소개 → 알림 권한 → 위치(사용 중) → 홈. 백그라운드 "항상 허용"은 온보딩에서 요구하지 않고 홈 배너/설정에서 업셀한다(§4.2.4). 배터리 최적화 예외는 자동 요청 금지 — 안내만(T11 설정 화면).

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/data/repo/SettingsStore.kt`
- Create: `android/app/src/main/java/com/recordofp/app/ui/permissions/PermissionStatus.kt`
- Create: `android/app/src/main/java/com/recordofp/app/ui/onboarding/OnboardingScreen.kt`, `OnboardingViewModel.kt`
- Modify: `ui/AppNavHost.kt`(시작 분기), `ui/home/HomeScreen.kt`(보호 배너), `di/AppModule.kt`(바인딩), strings.xml(ko/en)
- Test: `android/app/src/test/java/com/recordofp/app/ui/onboarding/OnboardingViewModelTest.kt`

**Interfaces:**
- Produces (T11이 확장·재사용):
  ```kotlin
  // data/repo/SettingsStore.kt
  interface SettingsStore {
      val onboardingDone: Flow<Boolean>
      suspend fun setOnboardingDone()
  }
  class DataStoreSettingsStore @Inject constructor(dataStore: DataStore<Preferences>) : SettingsStore
  // ui/permissions/PermissionStatus.kt
  data class PermissionSnapshot(
      val notifications: Boolean, val fineLocation: Boolean,
      val backgroundLocation: Boolean, val batteryUnrestricted: Boolean,
  ) { val fullyProtected: Boolean get() = notifications && fineLocation && backgroundLocation }
  fun readPermissionSnapshot(context: Context): PermissionSnapshot
  fun appDetailsSettingsIntent(context: Context): Intent  // 백그라운드 위치 업셀용 설정 딥링크
  ```

- [ ] **Step 1: 실패하는 VM 테스트**

`OnboardingViewModelTest.kt`:

```kotlin
package com.recordofp.app.ui.onboarding

import com.recordofp.app.data.repo.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeStore : SettingsStore {
        val flag = MutableStateFlow(false)
        override val onboardingDone: Flow<Boolean> = flag
        override suspend fun setOnboardingDone() { flag.value = true }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `finish는 완료 플래그를 영속화한다`() = runTest {
        val store = FakeStore()
        val vm = OnboardingViewModel(store)
        vm.finish()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, store.flag.value)
    }
}
```

Run → Unresolved reference 확인.

- [ ] **Step 2: SettingsStore + VM 구현**

`SettingsStore.kt`:

```kotlin
package com.recordofp.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsStore {
    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone()
}

@Singleton
class DataStoreSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {
    private val onboardingKey = booleanPreferencesKey("onboarding_done")

    override val onboardingDone: Flow<Boolean> = dataStore.data.map { it[onboardingKey] ?: false }

    override suspend fun setOnboardingDone() {
        dataStore.edit { it[onboardingKey] = true }
    }
}
```

`OnboardingViewModel.kt`:

```kotlin
package com.recordofp.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordofp.app.data.repo.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val store: SettingsStore,
) : ViewModel() {
    /** null = 로딩 중 (내비 분기 대기) */
    val done: StateFlow<Boolean?> = store.onboardingDone
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun finish() = viewModelScope.launch { store.setOnboardingDone() }
}
```

`BindsModule`에 `@Binds abstract fun settingsStore(impl: DataStoreSettingsStore): SettingsStore` 추가. Run: VM 테스트 PASS.

- [ ] **Step 3: PermissionStatus 리더**

`PermissionStatus.kt`:

```kotlin
package com.recordofp.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class PermissionSnapshot(
    val notifications: Boolean,
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val fullyProtected: Boolean get() = notifications && fineLocation && backgroundLocation
}

fun readPermissionSnapshot(context: Context): PermissionSnapshot {
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val pm = context.getSystemService(PowerManager::class.java)
    return PermissionSnapshot(
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        batteryUnrestricted = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false,
    )
}

/** A11+ 백그라운드 위치는 앱 설정에서만 켤 수 있다 (§4.2) — 설정 화면 딥링크 */
fun appDetailsSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
```

- [ ] **Step 4: 온보딩 화면 + 내비 분기 + 홈 배너**

strings.xml(ko) 추가 (en 대응 필수):

```xml
<string name="onboard_1_title">지나칠 때, 다시 떠오르게</string>
<string name="onboard_1_body">"편의점 가면 건전지 사야지" — 기록해두면 근처를 지날 때 알려드려요.</string>
<string name="onboard_2_title">알림 허용</string>
<string name="onboard_2_body">장소 근처에서 알려드리려면 알림 권한이 필요해요.</string>
<string name="onboard_3_title">위치 허용</string>
<string name="onboard_3_body">주변의 상점을 찾으려면 위치 권한이 필요해요.</string>
<string name="onboard_next">다음</string>
<string name="onboard_allow">허용하기</string>
<string name="onboard_skip">나중에</string>
<string name="onboard_start">시작하기</string>
<string name="banner_protection_title">알림이 꺼질 수 있는 상태예요</string>
<string name="banner_protection_action">설정에서 확인</string>
```

`OnboardingScreen.kt` — 구조: `var step by rememberSaveable { mutableIntStateOf(0) }`, step별 Column(이모지 아이콘 Text 64sp + title(headlineSmall) + body). step 1은 `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`로 `POST_NOTIFICATIONS`(SDK 33+일 때만 — 미만이면 자동 통과해 다음 step), step 2는 동일 계약으로 `ACCESS_FINE_LOCATION`. 각 권한 step은 [허용하기]와 [나중에] 둘 다 다음으로 진행(§4.2 열화 모드 — 거부해도 앱 사용 가능). 마지막 step [시작하기] → `viewModel.finish()` → 호출측 내비게이션이 done 변화로 홈 전환.

`AppNavHost.kt` 수정:

```kotlin
@Composable
fun AppNavHost(onboardingViewModel: OnboardingViewModel = hiltViewModel()) {
    val done by onboardingViewModel.done.collectAsStateWithLifecycle()
    when (done) {
        null -> Box(Modifier.fillMaxSize()) {} // DataStore 첫 로드 대기 (수 ms)
        false -> OnboardingScreen()
        true -> MainGraph()
    }
}

@Composable
private fun MainGraph() { /* 기존 NavHost(HOME/EDITOR/NEARBY/SETTINGS) 내용을 그대로 이 함수로 이동 */ }
```

`HomeScreen.kt` 배너 — 목록 위에 추가:

```kotlin
val context = LocalContext.current
var snapshot by remember { mutableStateOf(readPermissionSnapshot(context)) }
LifecycleResumeEffect(Unit) { // 설정에서 돌아오면 갱신
    snapshot = readPermissionSnapshot(context)
    onPauseOrDispose { }
}
if (!snapshot.fullyProtected) {
    Card(
        onClick = onSettingsClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.banner_protection_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.banner_protection_action), style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

(`LifecycleResumeEffect`는 `androidx.lifecycle.compose` — lifecycle-runtime-compose에 포함.)

- [ ] **Step 5: 전체 검증 + 수동 확인 + 커밋**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug`. 에뮬레이터: 앱 데이터 삭제 → 온보딩 4단계 → 권한 다이얼로그 → 홈 진입 → 배너(항상 허용 미부여 상태) 표시 확인.

```bash
git add -A android/app/src
git commit -m "feat: 점진적 권한 온보딩과 홈 보호 배너 (§4.2-4.3)"
```

---

### Task 11: 설정 화면 — 알림 정책 + 보호 상태 대시보드 (스펙 §4.3, §4.5)

정책 저장(DataStore) → `GatePolicyProvider`를 스토어 기반으로 교체(T7의 기본값 구현 삭제) → 설정 UI. 배터리 최적화는 시스템 설정 화면으로 안내만 한다(자동 요청 금지, §4.2).

**Files:**
- Modify: `android/app/src/main/java/com/recordofp/app/data/repo/SettingsStore.kt` (정책 확장)
- Create: `android/app/src/main/java/com/recordofp/app/data/engine/StoreGatePolicyProvider.kt`
- Modify: `data/engine/GeofenceEventHandler.kt`(DefaultGatePolicyProvider 삭제), `di/AppModule.kt`(재바인딩)
- Modify: `android/app/src/test/java/com/recordofp/app/ui/onboarding/OnboardingViewModelTest.kt` (인터페이스 확장에 따른 페이크 보강 — Step 2 참조)
- Create: `android/app/src/main/java/com/recordofp/app/ui/settings/SettingsViewModel.kt`
- Modify: `ui/settings/SettingsScreen.kt`, strings.xml(ko/en)
- Test: `android/app/src/test/java/com/recordofp/app/data/engine/StoreGatePolicyProviderTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  // SettingsStore에 추가
  data class NotificationPolicySettings(
      val cooldownHours: Int = 4,          // 선택지 1/4/12/24 (§4.5)
      val dailyCapTotal: Int = 10,         // 선택지 5/10/20/0(0=무제한)
      val quietEnabled: Boolean = true,
      val quietStartMinute: Int = 22 * 60,
      val quietEndMinute: Int = 8 * 60,
  )
  interface SettingsStore {
      // 기존 onboardingDone 유지 +
      val policy: Flow<NotificationPolicySettings>
      suspend fun updatePolicy(transform: (NotificationPolicySettings) -> NotificationPolicySettings)
  }
  ```

- [ ] **Step 1: 실패하는 매핑 테스트**

`StoreGatePolicyProviderTest.kt`:

```kotlin
package com.recordofp.app.data.engine

import com.recordofp.app.data.repo.NotificationPolicySettings
import com.recordofp.app.data.repo.SettingsStore
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeSettings(initial: NotificationPolicySettings) : SettingsStore {
    val flow = MutableStateFlow(initial)
    override val onboardingDone: Flow<Boolean> = MutableStateFlow(true)
    override suspend fun setOnboardingDone() {}
    override val policy: Flow<NotificationPolicySettings> = flow
    override suspend fun updatePolicy(transform: (NotificationPolicySettings) -> NotificationPolicySettings) {
        flow.value = transform(flow.value)
    }
}

class StoreGatePolicyProviderTest {

    @Test
    fun `설정값이 게이트 정책으로 매핑된다`() = runTest {
        val provider = StoreGatePolicyProvider(FakeSettings(NotificationPolicySettings(cooldownHours = 12, dailyCapTotal = 20)))
        val policy = provider.policy()
        assertEquals(Duration.ofHours(12), policy.cooldownPerItem)
        assertEquals(20, policy.dailyCapTotal)
        assertEquals(22 * 60, policy.quietStartMinute)
    }

    @Test
    fun `무제한(0)은 사실상 무한 상한으로, 방해금지 꺼짐은 시작==끝으로 매핑된다`() = runTest {
        val provider = StoreGatePolicyProvider(
            FakeSettings(NotificationPolicySettings(dailyCapTotal = 0, quietEnabled = false)),
        )
        val policy = provider.policy()
        assertEquals(Int.MAX_VALUE, policy.dailyCapTotal)
        assertEquals(policy.quietStartMinute, policy.quietEndMinute) // NotificationGate: 시작==끝 → 비활성
    }
}
```

Run → 컴파일 실패 확인 (policy/updatePolicy/StoreGatePolicyProvider 미존재).

- [ ] **Step 2: 스토어 확장 + 프로바이더 구현**

`SettingsStore.kt`에 `NotificationPolicySettings`(위 정의)와 인터페이스 멤버 추가. `DataStoreSettingsStore` 구현:

```kotlin
private val cooldownKey = intPreferencesKey("policy_cooldown_hours")
private val capKey = intPreferencesKey("policy_daily_cap")
private val quietEnabledKey = booleanPreferencesKey("policy_quiet_enabled")
private val quietStartKey = intPreferencesKey("policy_quiet_start")
private val quietEndKey = intPreferencesKey("policy_quiet_end")

override val policy: Flow<NotificationPolicySettings> = dataStore.data.map { p ->
    NotificationPolicySettings(
        cooldownHours = p[cooldownKey] ?: 4,
        dailyCapTotal = p[capKey] ?: 10,
        quietEnabled = p[quietEnabledKey] ?: true,
        quietStartMinute = p[quietStartKey] ?: 22 * 60,
        quietEndMinute = p[quietEndKey] ?: 8 * 60,
    )
}

override suspend fun updatePolicy(transform: (NotificationPolicySettings) -> NotificationPolicySettings) {
    dataStore.edit { p ->
        val next = transform(
            NotificationPolicySettings(
                cooldownHours = p[cooldownKey] ?: 4,
                dailyCapTotal = p[capKey] ?: 10,
                quietEnabled = p[quietEnabledKey] ?: true,
                quietStartMinute = p[quietStartKey] ?: 22 * 60,
                quietEndMinute = p[quietEndKey] ?: 8 * 60,
            ),
        )
        p[cooldownKey] = next.cooldownHours
        p[capKey] = next.dailyCapTotal
        p[quietEnabledKey] = next.quietEnabled
        p[quietStartKey] = next.quietStartMinute
        p[quietEndKey] = next.quietEndMinute
    }
}
```

`StoreGatePolicyProvider.kt`:

```kotlin
package com.recordofp.app.data.engine

import com.recordofp.app.data.repo.SettingsStore
import com.recordofp.app.domain.engine.NotificationGate
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** 설정 저장소의 값을 필터 체인 정책으로 변환 (스펙 §4.5 → §6.5) */
@Singleton
class StoreGatePolicyProvider @Inject constructor(
    private val settings: SettingsStore,
) : GatePolicyProvider {
    override suspend fun policy(): NotificationGate.Policy {
        val s = settings.policy.first()
        return NotificationGate.Policy(
            cooldownPerItem = Duration.ofHours(s.cooldownHours.toLong()),
            dailyCapTotal = if (s.dailyCapTotal <= 0) Int.MAX_VALUE else s.dailyCapTotal,
            quietStartMinute = if (s.quietEnabled) s.quietStartMinute else 0,
            quietEndMinute = if (s.quietEnabled) s.quietEndMinute else 0,
        )
    }
}
```

`GeofenceEventHandler.kt`에서 `DefaultGatePolicyProvider` 클래스 삭제, `BindsModule`의 바인딩을 `@Binds abstract fun gatePolicyProvider(impl: StoreGatePolicyProvider): GatePolicyProvider`로 교체.

인터페이스 확장으로 T10의 `OnboardingViewModelTest` 내 `FakeStore`가 컴파일되지 않게 된다 — 해당 페이크에 다음 두 멤버를 추가한다 (import `com.recordofp.app.data.repo.NotificationPolicySettings` 포함):

```kotlin
override val policy: Flow<NotificationPolicySettings> = MutableStateFlow(NotificationPolicySettings())
override suspend fun updatePolicy(transform: (NotificationPolicySettings) -> NotificationPolicySettings) {}
```

- [ ] **Step 3: 통과 확인** — Run: `.\gradlew.bat testDebugUnitTest` → 전부 PASS (T7 핸들러 테스트는 익명 프로바이더라 영향 없음).

- [ ] **Step 4: SettingsViewModel + 화면**

strings.xml(ko) 추가 (en 대응 필수):

```xml
<string name="settings_section_protection">보호 상태</string>
<string name="settings_perm_notifications">알림 권한</string>
<string name="settings_perm_location">위치 권한</string>
<string name="settings_perm_background">항상 허용 (백그라운드)</string>
<string name="settings_perm_battery">배터리 최적화 예외</string>
<string name="settings_perm_ok">켜짐</string>
<string name="settings_perm_off">꺼짐 — 탭해서 설정</string>
<string name="settings_battery_guide">삼성 기기는 [설정 &gt; 배터리 &gt; 절전 모드/앱 절전]에서 P의기록을 예외로 두어야 알림이 안정적으로 옵니다.</string>
<string name="settings_section_policy">알림 정책</string>
<string name="settings_cooldown">같은 항목 재알림 간격</string>
<string name="settings_daily_cap">하루 알림 상한</string>
<string name="settings_cap_unlimited">무제한</string>
<string name="settings_quiet">방해금지 시간대</string>
<string name="settings_diagnostics">문제 해결 (진단)</string>
<string name="settings_hours_fmt">%d시간</string>
<string name="settings_count_fmt">%d건</string>
```

`SettingsViewModel.kt`:

```kotlin
package com.recordofp.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordofp.app.data.repo.NotificationPolicySettings
import com.recordofp.app.data.repo.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
) : ViewModel() {

    val policy: StateFlow<NotificationPolicySettings> = store.policy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationPolicySettings())

    fun setCooldownHours(h: Int) = update { it.copy(cooldownHours = h) }
    fun setDailyCap(n: Int) = update { it.copy(dailyCapTotal = n) }
    fun setQuietEnabled(enabled: Boolean) = update { it.copy(quietEnabled = enabled) }
    fun setQuietRange(startMinute: Int, endMinute: Int) =
        update { it.copy(quietStartMinute = startMinute, quietEndMinute = endMinute) }

    private fun update(transform: (NotificationPolicySettings) -> NotificationPolicySettings) =
        viewModelScope.launch { store.updatePolicy(transform) }
}
```

`SettingsScreen.kt` 교체 — 구조 (TODO 주석 제거, `LazyColumn` 섹션 3개):
1. **보호 상태**: `readPermissionSnapshot(context)`를 `LifecycleResumeEffect`로 갱신. 항목 4행 — 각 행은 `ListItem(headlineContent=라벨, trailingContent=상태 텍스트(켜짐=primary/꺼짐=error))`. 꺼진 행 탭 시: 알림 → `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)`, 위치/항상 허용 → `appDetailsSettingsIntent(context)`, 배터리 → `Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATIONS_SETTINGS)`(목록 화면 — 자동 요청 아님). 아래에 `settings_battery_guide` 본문 텍스트.
2. **알림 정책**: 쿨다운 `SegmentedButton` 1/4/12/24(라벨 `settings_hours_fmt`), 상한 `SegmentedButton` 5/10/20/무제한(값 0), 방해금지 `Switch` + 켜짐일 때 시작·끝 시각 두 개의 `OutlinedButton`(라벨 "22:00" 형식) → 탭 시 `AlertDialog` 안에 M3 `TimePicker` → 확인 시 `setQuietRange`.
3. **문제 해결**: `ListItem` "진단" → `onDiagnosticsClick()` (콜백 파라미터로 추가 — 라우트는 T13에서 연결. T13 전까지는 `AppNavHost`에서 빈 람다 전달).

`SettingsScreen` 시그니처: `fun SettingsScreen(onDiagnosticsClick: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel())`.

- [ ] **Step 5: 전체 검증 + 수동 확인 + 커밋**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug`. 에뮬레이터: 정책 변경 → 앱 재시작 후 유지 확인, 보호 상태 행 탭 → 시스템 설정 이동 확인.

```bash
git add -A android/app/src
git commit -m "feat: 설정 - 알림 정책 저장과 보호 상태 대시보드 (§4.3, §4.5)"
```

---

### Task 12: 주변 보기 — 열화 모드의 핵심 화면 (스펙 §3.1, §4.2)

백그라운드 권한 없이도(위치 '사용 중'만으로) 앱을 열면 지금 주변에서 처리할 수 있는 일이 보이는 화면. T1 해석기·T2 카카오 조회·T6 위치를 그대로 재사용한다. 지도 SDK는 v1.1 — 카카오맵 앱 딥링크로 대체(§3.2).

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/ui/nearby/NearbyViewModel.kt`
- Modify: `ui/nearby/NearbyScreen.kt`, strings.xml(ko/en)
- Test: `android/app/src/test/java/com/recordofp/app/ui/nearby/NearbyViewModelTest.kt`

**Interfaces:**
- Consumes: `TriggerResolver`(T1), `PoiRepository`, `LocationProvider`, `ReminderRepository.activeTriggers()`
- Produces:
  ```kotlin
  data class NearbyGroup(val matchKey: String, val pois: List<PoiCandidate>)
  data class NearbyUiState(
      val loading: Boolean = true, val locationUnavailable: Boolean = false,
      val groups: List<NearbyGroup> = emptyList(),
  )
  ```

- [ ] **Step 1: 실패하는 테스트**

`NearbyViewModelTest.kt`:

```kotlin
package com.recordofp.app.ui.nearby

import com.recordofp.app.data.location.LocationProvider
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.engine.TriggerResolver
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepo(val triggers: List<TriggerSpec>) : ReminderRepository {
        override fun observeActive(): Flow<List<Reminder>> = emptyFlow()
        override suspend fun upsert(reminder: Reminder) = 0L
        override suspend fun complete(id: Long) {}
        override suspend fun muteUntil(id: Long, untilEpochMs: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun activeTriggers() = triggers
    }

    private class FakePoi : PoiRepository {
        override suspend fun search(
            resolution: PoiResolution, query: String, center: GeoPoint, radiusM: Int, maxResults: Int,
        ) = listOf(PoiCandidate("p-$query", "$query 지점", GeoPoint(37.501, 127.0), 150.0))
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(triggers: List<TriggerSpec>, location: GeoPoint?) = NearbyViewModel(
        repository = FakeRepo(triggers), poiRepository = FakePoi(), resolver = TriggerResolver(),
        locationProvider = object : LocationProvider { override suspend fun currentOrLast() = location },
    )

    @Test
    fun `활성 트리거별로 주변 POI 그룹을 만든다 - PLACE는 거리 계산으로 포함`() = runTest {
        val vm = vm(
            listOf(
                TriggerSpec(id = 1, reminderId = 1, type = TriggerType.CATEGORY, categoryId = "convenience"),
                TriggerSpec(
                    id = 2, reminderId = 1, type = TriggerType.PLACE,
                    placeName = "회사 우체국", placePoint = GeoPoint(37.509, 127.0), // ~1km
                ),
            ),
            location = GeoPoint(37.5, 127.0),
        )
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.groups.size)
        val cat = state.groups.first { it.matchKey == "cat:convenience" }
        assertEquals("CS2 지점", cat.pois.single().name)
        val place = state.groups.first { it.matchKey == "place:2" }
        assertEquals("회사 우체국", place.pois.single().name)
        assertTrue(place.pois.single().distanceM in 900.0..1100.0)
    }

    @Test
    fun `위치를 못 얻으면 locationUnavailable이 선다`() = runTest {
        val vm = vm(emptyList(), location = null)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.locationUnavailable)
    }
}
```

Run → Unresolved reference 확인.

- [ ] **Step 2: 구현**

`NearbyViewModel.kt`:

```kotlin
package com.recordofp.app.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordofp.app.data.location.LocationProvider
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.PlaceRequest
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.engine.QueryRequest
import com.recordofp.app.domain.engine.TriggerResolver
import com.recordofp.app.domain.model.distanceMeters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NearbyGroup(val matchKey: String, val pois: List<PoiCandidate>)

data class NearbyUiState(
    val loading: Boolean = true,
    val locationUnavailable: Boolean = false,
    val groups: List<NearbyGroup> = emptyList(),
)

@HiltViewModel
class NearbyViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val poiRepository: PoiRepository,
    private val resolver: TriggerResolver,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, locationUnavailable = false) }
            val here = locationProvider.currentOrLast()
            if (here == null) {
                _state.update { it.copy(loading = false, locationUnavailable = true) }
                return@launch
            }
            val groups = resolver.resolve(repository.activeTriggers()).mapNotNull { req ->
                when (req) {
                    is QueryRequest -> {
                        val pois = runCatching {
                            poiRepository.search(req.resolution, req.query, here, maxResults = 5)
                        }.getOrDefault(emptyList())
                        if (pois.isEmpty()) null else NearbyGroup(req.matchKey, pois)
                    }
                    is PlaceRequest -> NearbyGroup(
                        req.matchKey,
                        listOf(
                            PoiCandidate(
                                id = req.matchKey, name = req.name ?: "", point = req.point,
                                distanceM = distanceMeters(here, req.point),
                            ),
                        ),
                    )
                }
            }
            _state.update { it.copy(loading = false, groups = groups) }
        }
    }
}
```

- [ ] **Step 3: 통과 확인** — Run: Nearby 테스트 → 2 PASS.

- [ ] **Step 4: 화면 구현**

strings.xml(ko) 추가 (en 대응 필수):

```xml
<string name="nearby_empty">주변에 처리할 일이 없어요.</string>
<string name="nearby_no_location">현재 위치를 확인할 수 없어요. 위치 권한과 GPS를 확인해주세요.</string>
<string name="nearby_distance_fmt">약 %dm</string>
<string name="nearby_open_map">카카오맵에서 보기</string>
```

`NearbyScreen.kt` 교체 — 구조: `LaunchedEffect(Unit) { viewModel.load() }`, 상단 새로고침 `IconButton(Icons.Filled.Refresh)` → `load()`. 상태 분기: loading → `CircularProgressIndicator`, locationUnavailable → `nearby_no_location`, 빈 그룹 → `nearby_empty`. 그룹 렌더: 헤더는 matchKey 접두로 분기 — `"cat:{id}"` → `TriggerCatalog.byId(id)`의 이모지+`catalogLabelRes(id)`, `"brand:*"` → 🔎+키워드, `"place:*"` → 📌+POI name. POI 행: `ListItem(headlineContent = name, supportingContent = stringResource(R.string.nearby_distance_fmt, poi.distanceM.toInt()))`, 탭 시:

```kotlin
val uri = "kakaomap://look?p=${poi.point.lat},${poi.point.lng}"
runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    .onFailure { // 카카오맵 미설치 → 웹 지도
        context.startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://map.kakao.com/link/map/${Uri.encode(poi.name)},${poi.point.lat},${poi.point.lng}")))
    }
```

- [ ] **Step 5: 전체 검증 + 수동 확인 + 커밋**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug`. 에뮬레이터: 항목 2개(편의점/브랜드) 상태에서 주변 보기 → 그룹·거리 표시, 탭 → 지도 이동 확인.

```bash
git add -A android/app/src
git commit -m "feat: 주변 보기 - 트리거 매칭 POI 리스트와 카카오맵 딥링크 (§3.1)"
```

---

### Task 13: 진단 화면 + 최종 배선 + 필드 테스트 준비 (스펙 §4.4, §10.2)

"왜 알림이 안 왔는지"를 앱이 스스로 답하는 화면, 로그 정리(prune), 진단 라우트 연결, GPX 도구 디렉토리, 그리고 v1 기능 전체의 수동 E2E 체크리스트.

**Files:**
- Create: `android/app/src/main/java/com/recordofp/app/ui/settings/DiagnosticsScreen.kt`, `DiagnosticsViewModel.kt`
- Modify: `ui/AppNavHost.kt`(DIAGNOSTICS 라우트), strings.xml(ko/en)
- Create: `tools/routes/README.md`, `tools/routes/gangnam-walk.gpx`
- Test: `android/app/src/test/java/com/recordofp/app/ui/settings/DiagnosticsViewModelTest.kt`

- [ ] **Step 1: 실패하는 테스트**

`DiagnosticsViewModelTest.kt`:

```kotlin
package com.recordofp.app.ui.settings

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-08-31T03:00:00Z")

    private class FakeRuns : EngineRunLogDao {
        val flow = MutableStateFlow<List<EngineRunLogEntity>>(emptyList())
        var prunedBefore: Long? = null
        override suspend fun insert(entity: EngineRunLogEntity) {}
        override fun observeRecent(limit: Int): Flow<List<EngineRunLogEntity>> = flow
        override suspend fun pruneOlderThan(before: Long) { prunedBefore = before }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `시작 시 14일 이전 로그를 정리한다`() = runTest {
        val dao = FakeRuns()
        DiagnosticsViewModel(dao, Clock.fixed(now, ZoneId.of("Asia/Seoul")))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(now.toEpochMilli() - 14L * 24 * 3600 * 1000, dao.prunedBefore)
    }

    @Test
    fun `내보내기 텍스트는 시각·원인·결과를 담는다`() = runTest {
        val dao = FakeRuns()
        val vm = DiagnosticsViewModel(dao, Clock.fixed(now, ZoneId.of("Asia/Seoul")))
        dao.flow.value = listOf(
            EngineRunLogEntity(at = now.toEpochMilli(), cause = "SENTINEL_EXIT", result = "APPLIED", registeredCount = 12, note = null),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val text = vm.buildExport()
        assertTrue(text.contains("SENTINEL_EXIT"))
        assertTrue(text.contains("APPLIED"))
        assertTrue(text.contains("12"))
    }
}
```

Run → Unresolved reference 확인.

- [ ] **Step 2: 구현**

`DiagnosticsViewModel.kt`:

```kotlin
package com.recordofp.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val runLogDao: EngineRunLogDao,
    private val clock: Clock,
) : ViewModel() {

    val entries: StateFlow<List<EngineRunLogEntity>> = runLogDao.observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            runLogDao.pruneOlderThan(clock.millis() - RETENTION_MS)
        }
    }

    /** 진단 로그 수동 내보내기 (§4.4 — 기기 밖 자동 전송 없음, 사용자가 공유할 때만) */
    fun buildExport(): String {
        val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
        return entries.value.joinToString("\n") { e ->
            val t = fmt.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()))
            "[$t] ${e.cause} -> ${e.result} (fences=${e.registeredCount})${e.note?.let { " $it" } ?: ""}"
        }
    }

    companion object {
        private const val RETENTION_MS = 14L * 24 * 3600 * 1000
    }
}
```

strings.xml(ko) 추가 (en 대응 필수):

```xml
<string name="diag_title">진단</string>
<string name="diag_empty">아직 엔진 실행 기록이 없어요.</string>
<string name="diag_export">로그 내보내기</string>
```

`DiagnosticsScreen.kt` — 구조: `Scaffold` + TopAppBar(제목 `diag_title`, actions에 공유 IconButton → `Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, viewModel.buildExport())`를 `Intent.createChooser`로 시작). 본문: entries 비면 `diag_empty`, 아니면 `LazyColumn`의 `ListItem`(headline = "${cause} → ${result}", supporting = 시각 + note, trailing = registeredCount). 시각 포맷은 VM의 buildExport와 동일 패턴 재사용 없이 화면에서 `remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }`.

`AppNavHost.kt`의 MainGraph에 라우트 추가:

```kotlin
composable(Routes.DIAGNOSTICS) { DiagnosticsScreen() }
// Routes에 const val DIAGNOSTICS = "diagnostics" 추가
// SETTINGS composable을 다음으로 교체:
composable(Routes.SETTINGS) {
    SettingsScreen(onDiagnosticsClick = { navController.navigate(Routes.DIAGNOSTICS) })
}
```

- [ ] **Step 3: 통과 확인** — Run: `.\gradlew.bat testDebugUnitTest` → 전부 PASS.

- [ ] **Step 4: 필드 테스트 도구 (§10.2)**

`tools/routes/README.md`:

```markdown
# 필드 테스트 경로 (설계 §10.2)

에뮬레이터 Extended Controls > Location > Import GPX로 불러와 재생한다.
- 도보 시나리오: 재생 속도 1x (Playback speed)
- 차량 시나리오: 같은 경로를 5x로 재생 — DWELL 60초 필터가 걸러야 정상
- 결과 판정: 앱 설정 > 진단 화면에서 FENCE_EVENT/차단 사유 로그 확인
- 회차별 튜닝 변경은 EngineParams 커밋으로 기록한다
```

`tools/routes/gangnam-walk.gpx` (강남역→역삼역 도보, 편의점 밀집 구간):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="record-of-p" xmlns="http://www.topografix.com/GPX/1/1">
  <trk><name>gangnam-walk</name><trkseg>
    <trkpt lat="37.4979" lon="127.0276"/>
    <trkpt lat="37.4985" lon="127.0298"/>
    <trkpt lat="37.4991" lon="127.0320"/>
    <trkpt lat="37.4997" lon="127.0342"/>
    <trkpt lat="37.5003" lon="127.0364"/>
    <trkpt lat="37.5006" lon="127.0386"/>
    <trkpt lat="37.5008" lon="127.0408"/>
  </trkseg></trk>
</gpx>
```

- [ ] **Step 5: v1 수동 E2E 체크리스트 실행 (에뮬레이터)**

1. 앱 데이터 삭제 → 온보딩 → 권한 허용 → 홈 진입
2. "건전지 사기" 기록 (편의점 + 브랜드 GS25) → 홈 칩 확인
3. 진단 화면: APP_OPEN/ITEM_CHANGE → APPLIED, fences>0 확인
4. GPX 재생(1x) → 근처 알림 수신 → [오늘 그만] → 재재생 시 BLOCK_SNOOZED 로그 확인
5. [완료] 액션 → 홈에서 사라짐 확인
6. 시스템 설정에서 "항상 허용" 부여 → 앱 종료(스와이프 킬) → GPX 재생 → 백그라운드 알림 수신
7. 재부팅 → 진단에 BOOT → APPLIED 확인
8. 주변 보기: 그룹·거리·카카오맵 이동
9. 설정: 쿨다운 1시간으로 변경 → 재시작 후 유지
10. TalkBack 켜고 홈·에디터 주요 요소 낭독 확인, 다크 모드 확인 (§8)

- [ ] **Step 6: 최종 검증 + 커밋**

Run: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug` → BUILD SUCCESSFUL (lint 경고는 기록, 오류만 차단).

```bash
git add -A android/app/src tools
git commit -m "feat: 진단 화면·로그 정리·필드 테스트 도구 - v1 기능 완성 (§4.4, §10.2)"
```

---

## Spec Coverage Map (자가 점검 결과)

| 스펙 | 태스크 | 비고 |
|---|---|---|
| §3.1 CRUD·트리거 3종·다중 지정 | T8, T9 | |
| §3.3 카탈로그·커스텀 브랜드 | T1, T9 | 카탈로그 자체는 스켈레톤에 존재 |
| §4.1 기록·알림·처리 플로우 | T7, T9 | |
| §4.2 점진 온보딩·업셀 | T10, T11 | 배터리 예외는 안내만 |
| §4.3 보호 상태 | T10(배너), T11(대시보드) | |
| §4.4 진단 | T7(사유 로깅), T13(화면·내보내기) | |
| §4.5 정책 기본값·변경 | T11 | 항목당 상한·지점 24h는 §4.5대로 고정값 |
| §6.1~6.3 재배치 | T1, T3~T6 | 플래너·예산은 스켈레톤에서 구현 완료 |
| §6.4 실패 처리 | T2(전파), T5(유지), T6(백오프·위치) | |
| §6.5 파이프라인 | T7 | |
| §6.2 원인 전체 배선 | BOOT=T6, SENTINEL=T7, PERIODIC=스켈레톤 App, APP_OPEN=T9, ITEM_CHANGE=T8, RETRY=T6 | |
| §7 카카오 연동 | T2 (+스켈레톤 구현) | |
| §8 접근성·국제화 | 각 태스크 strings(en) + T13 체크리스트 10 | |
| §10.1~10.3 테스트·CI | 전 태스크 TDD, CI는 하네스에 존재 | |
| 계획 범위 밖 | §11 출시 절차(스토어 등록·테스터 모집·정책 폼), §10.2 실기기 필드 튜닝 회차 | v1 코드 완성 후 별도 진행 |

## 실행 노트

- 각 태스크는 이전 태스크의 커밋 위에서 시작한다. 테스트 실패 상태로 다음 태스크로 넘어가지 않는다.
- UI 태스크(T9~T13)의 "수동 확인"은 에뮬레이터 기준이며, 실기기 필드 튜닝(§10.2 합격선 판정)은 이 계획 완료 후 별도 사이클로 진행한다.
- 카카오 REST 키가 `local.properties`에 없으면 T2 이후 실기 확인이 제한된다 — T2 시작 전에 발급해 둘 것.

