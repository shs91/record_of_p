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
