// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import it.manentlabs.app.wearable.omi.GattQueue.Kind
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GattQueueTest {
    private val timeouts = mutableListOf<Kind>()
    private val queue = GattQueue { timeouts += it }
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `only a matching callback completes the operation`() = runTest {
        val read = async { queue.run(Kind.READ, a) { true } }
        runCurrent()
        queue.complete(Kind.WRITE, a, GattQueue.Answer())
        queue.complete(Kind.READ, b, GattQueue.Answer())
        runCurrent()
        assertFalse(read.isCompleted)
        queue.complete(Kind.READ, a, GattQueue.Answer(byteArrayOf(7)))
        assertArrayEquals(byteArrayOf(7), read.await()?.value)
    }

    @Test
    fun `operations run one at a time`() = runTest {
        val started = mutableListOf<Kind>()
        val first = async { queue.run(Kind.MTU) { started += Kind.MTU; true } }
        val second = async { queue.run(Kind.DISCOVER) { started += Kind.DISCOVER; true } }
        runCurrent()
        assertEquals(listOf(Kind.MTU), started)
        queue.complete(Kind.MTU, null, GattQueue.Answer(number = 247))
        runCurrent()
        assertEquals(247, first.await()?.number)
        assertEquals(listOf(Kind.MTU, Kind.DISCOVER), started)
        queue.complete(Kind.DISCOVER, null, GattQueue.Answer())
        second.await()
    }

    @Test
    fun `an unanswered operation times out after 5 s and is reported`() = runTest {
        val op = async { queue.run(Kind.WRITE, a) { true } }
        advanceTimeBy(4_999)
        assertFalse(op.isCompleted)
        advanceTimeBy(2)
        assertNull(op.await())
        assertEquals(listOf(Kind.WRITE), timeouts)
    }

    @Test
    fun `an operation that cannot start fails at once without a timeout`() = runTest {
        assertNull(queue.run(Kind.READ, a) { false })
        assertNull(queue.run(Kind.READ, a) { throw SecurityException("revoked") })
        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun `a failure status or a dropped link fails the operation`() = runTest {
        val failed = async { queue.run(Kind.READ, a) { true } }
        runCurrent()
        queue.complete(Kind.READ, a, null)
        assertNull(failed.await())

        val dropped = async { queue.run(Kind.RSSI) { true } }
        runCurrent()
        queue.failPending()
        assertNull(dropped.await())
        assertTrue(timeouts.isEmpty())
    }
}
