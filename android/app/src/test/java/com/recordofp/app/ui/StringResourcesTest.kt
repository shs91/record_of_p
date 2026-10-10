package com.recordofp.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
