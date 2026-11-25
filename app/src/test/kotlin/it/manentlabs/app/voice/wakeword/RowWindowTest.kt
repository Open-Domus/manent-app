// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.wakeword

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RowWindowTest {
    @Test
    fun `keeps the newest rows, oldest first`() {
        val window = RowWindow(rows = 3, width = 2, fill = 1f)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f), window.snapshot(), 0f)
        window.push(floatArrayOf(2f, 2f))
        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 1f, 2f, 2f), window.snapshot(), 0f)
        window.push(floatArrayOf(3f, 3f, 4f, 4f))
        assertArrayEquals(floatArrayOf(2f, 2f, 3f, 3f, 4f, 4f), window.snapshot(), 0f)
        window.push(floatArrayOf(5f, 5f, 6f, 6f, 7f, 7f, 8f, 8f))
        assertArrayEquals(floatArrayOf(6f, 6f, 7f, 7f, 8f, 8f), window.snapshot(), 0f)
        window.reset()
        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f), window.snapshot(), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `only whole rows go in`() {
        RowWindow(rows = 3, width = 2, fill = 0f).push(floatArrayOf(1f))
    }
}
