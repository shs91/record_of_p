package com.recordofp.app.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lng: Double)

private const val EARTH_RADIUS_M = 6_371_000.0

/** 하버사인 거리 (미터) */
fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val sinLat = sin(dLat / 2)
    val sinLng = sin(dLng / 2)
    val h = sinLat * sinLat +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sinLng * sinLng
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}
