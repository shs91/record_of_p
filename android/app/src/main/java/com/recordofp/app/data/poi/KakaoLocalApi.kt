package com.recordofp.app.data.poi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 카카오 로컬 REST API (설계 §7).
 * 주의: 카카오는 x=경도(lng), y=위도(lat)이며 좌표·거리를 문자열로 반환한다.
 */
interface KakaoLocalApi {

    @GET("v2/local/search/category.json")
    suspend fun searchByCategory(
        @Query("category_group_code") categoryGroupCode: String,
        @Query("x") lng: String,
        @Query("y") lat: String,
        @Query("radius") radiusM: Int,
        @Query("sort") sort: String = "distance",
        @Query("size") size: Int = 15,
        @Query("page") page: Int = 1,
    ): KakaoSearchResponse

    @GET("v2/local/search/keyword.json")
    suspend fun searchByKeyword(
        @Query("query") query: String,
        @Query("x") lng: String,
        @Query("y") lat: String,
        @Query("radius") radiusM: Int,
        @Query("sort") sort: String = "distance",
        @Query("size") size: Int = 15,
        @Query("page") page: Int = 1,
    ): KakaoSearchResponse

    companion object {
        const val BASE_URL = "https://dapi.kakao.com/"
    }
}

@Serializable
data class KakaoSearchResponse(
    val documents: List<KakaoPlaceDto> = emptyList(),
    val meta: KakaoMetaDto = KakaoMetaDto(),
)

@Serializable
data class KakaoMetaDto(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("is_end") val isEnd: Boolean = true,
)

@Serializable
data class KakaoPlaceDto(
    val id: String = "",
    @SerialName("place_name") val placeName: String = "",
    @SerialName("category_group_code") val categoryGroupCode: String = "",
    @SerialName("road_address_name") val roadAddressName: String = "",
    /** 경도 */
    val x: String = "",
    /** 위도 */
    val y: String = "",
    /** 검색 중심으로부터의 거리(미터) — 중심 좌표를 준 경우에만 존재 */
    val distance: String = "",
    @SerialName("place_url") val placeUrl: String = "",
)
