// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.wakeword

/**
 * The last [rows] rows of [width] values each, oldest first, as one flat
 * array ready to be a model's input. Starts filled with [fill].
 */
class RowWindow(private val rows: Int, private val width: Int, private val fill: Float) {
    private val values = FloatArray(rows * width) { fill }

    /** Appends whole rows from [data] (its length a multiple of [width]), dropping the oldest. */
    fun push(data: FloatArray) {
        require(data.size % width == 0) { "Rows are $width values wide" }
        val incoming = data.size / width
        if (incoming >= rows) {
            data.copyInto(values, 0, data.size - values.size)
            return
        }
        val keep = (rows - incoming) * width
        values.copyInto(values, 0, values.size - keep)
        data.copyInto(values, keep)
    }

    /** A copy of the window. */
    fun snapshot(): FloatArray = values.copyOf()

    fun reset() = values.fill(fill)
}
