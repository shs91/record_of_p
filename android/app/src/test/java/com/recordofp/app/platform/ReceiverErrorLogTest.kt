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
