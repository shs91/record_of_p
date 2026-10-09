package com.recordofp.app.platform

import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.db.EngineRunLogEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

private class FakeRunLog(private val insertError: Exception? = null) : EngineRunLogDao {
    val entries = mutableListOf<EngineRunLogEntity>()
    override suspend fun insert(entity: EngineRunLogEntity) {
        insertError?.let { throw it }
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
        FakeRunLog(insertError = IllegalStateException("disk full")).logReceiverError(clock, "NOTIFICATION_ACTION", IllegalStateException("x"))
    }

    @Test
    fun `취소는 삼키지 않고 그대로 전파한다`() = runTest {
        val dao = FakeRunLog(insertError = CancellationException("cancelled"))
        try {
            dao.logReceiverError(clock, "FENCE_EVENT", IllegalStateException("x"))
            fail("취소 예외가 전파되어야 한다")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }
}
