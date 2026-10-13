package com.vzmah.app.data.sensor

import com.vzmah.app.domain.model.MotionVector
import com.vzmah.app.domain.usecase.GestureConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class StepFilterTest {

    private val sampleRate = 50.0
    private val dt = 1.0 / sampleRate
    private val gestureConfig = GestureConfig()

    @Test
    fun `first sample produces zero output because gravity estimate is initialised`() {
        val filter = StepFilter()

        val result = filter.process(0.0, MotionVector(0.0, 9.81, 0.0))

        assertEquals(0.0, result.linearAccel.magnitude, 1e-9)
        assertEquals(0.0, result.filteredAccel.magnitude, 1e-9)
    }

    @Test
    fun `gravity only input decays to zero`() {
        val filter = StepFilter(sampleRateHz = sampleRate)

        var last = MotionVector.ZERO
        repeat(600) { index ->
            last = filter.process(index * dt, MotionVector(0.0, 9.81, 0.0)).filteredAccel
        }

        assertTrue("Остаток должен затухнуть, получено ${last.magnitude}", last.magnitude < 0.05)
    }

    @Test
    fun `cadence band 1_5 to 2_5 Hz is attenuated at every frequency of the band`() {
        val amplitude = 3.0
        var worstRatio = 0.0

        listOf(1.5, 1.75, 2.0, 2.25, 2.5).forEach { hz ->
            val filter = StepFilter(sampleRateHz = sampleRate)
            val rms = measureRms(filter) { index ->
                val t = index * dt
                MotionVector(0.0, MotionVector.GRAVITY + amplitude * sin(2.0 * PI * hz * t), 0.0)
            }
            worstRatio = maxOf(worstRatio, rms / amplitude)
        }

        assertTrue(
            "Остаток походных колебаний не должен превышать 35% амплитуды, получено ${"%.2f".format(worstRatio * 100)}%",
            worstRatio < 0.35,
        )
    }

    @Test
    fun `cadence centre frequency is suppressed by an order of magnitude`() {
        val filter = StepFilter(sampleRateHz = sampleRate)
        val amplitude = 3.0

        val rms = measureRms(filter) { index ->
            val t = index * dt
            MotionVector(0.0, MotionVector.GRAVITY + amplitude * sin(2.0 * PI * 2.0 * t), 0.0)
        }

        assertTrue("RMS=$rms при амплитуде $amplitude", rms < amplitude / 10.0)
    }

    @Test
    fun `walking residue crosses the calm line so the detector stays blocked`() {
        val filter = StepFilter(sampleRateHz = sampleRate)
        val calmLine = gestureConfig.calmAccelG * MotionVector.GRAVITY
        var worstPeak = 0.0

        repeat(300) { index ->
            val t = index * dt
            val cadence = 1.6 + 0.5 * sin(2.0 * PI * 0.15 * t)
            val amplitude = 2.0 + 1.5 * sin(2.0 * PI * 0.25 * t)
            val result = filter.process(
                t,
                MotionVector(0.0, MotionVector.GRAVITY + amplitude * sin(2.0 * PI * cadence * t), 0.0),
            )
            worstPeak = maxOf(worstPeak, result.filteredAccel.magnitude)
        }

        assertTrue(
            "Пик остатка ходьбы ${"%.2f".format(worstPeak)} м/с² должен превышать линию покоя ${"%.2f".format(calmLine)} м/с²",
            worstPeak > calmLine,
        )
    }

    @Test
    fun `a phone lying still stays below the calm line`() {
        val filter = StepFilter(sampleRateHz = sampleRate)
        val calmLine = gestureConfig.calmAccelG * MotionVector.GRAVITY
        var worstPeak = 0.0

        repeat(300) { index ->
            val t = index * dt
            val result = filter.process(
                t,
                MotionVector(0.0, MotionVector.GRAVITY + 0.05 * sin(2.0 * PI * 0.3 * t), 0.0),
            )
            worstPeak = maxOf(worstPeak, result.filteredAccel.magnitude)
        }

        assertTrue(
            "Покой ${"%.3f".format(worstPeak)} м/с² должен оставаться ниже линии ${"%.2f".format(calmLine)} м/с²",
            worstPeak < calmLine,
        )
    }

    @Test
    fun `horizontal walking component on the x axis is filtered the same way`() {
        val filter = StepFilter(sampleRateHz = sampleRate)
        val amplitude = 3.0

        val rms = measureRms(filter) { index ->
            val t = index * dt
            MotionVector(amplitude * sin(2.0 * PI * 2.0 * t), 0.0, 0.0)
        }

        assertTrue("RMS=$rms", rms < amplitude / 10.0)
    }

    @Test
    fun `reset clears state`() {
        val filter = StepFilter(sampleRateHz = sampleRate)
        repeat(200) { index ->
            filter.process(index * dt, MotionVector(0.0, MotionVector.GRAVITY + 2.0 * sin(0.2 * index), 0.0))
        }

        filter.reset()

        assertEquals(0.0, filter.process(0.0, MotionVector(1.0, 2.0, 3.0)).filteredAccel.magnitude, 1e-9)
    }

    @Test
    fun `duplicate timestamps do not break the filter`() {
        val filter = StepFilter(sampleRateHz = sampleRate)

        repeat(100) { index ->
            filter.process(1.0, MotionVector(0.0, 9.81 + index.toDouble(), 0.0))
        }

        val result = filter.process(1.0, MotionVector(0.0, 9.81, 0.0))
        assertTrue(result.filteredAccel.magnitude.isFinite())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `notch frequency above Nyquist is rejected`() {
        StepFilter(sampleRateHz = sampleRate, cadenceFrequencyHz = 30.0)
    }

    private fun measureRms(
        filter: StepFilter,
        skipSamples: Int = 400,
        totalSamples: Int = 1000,
        signal: (Int) -> MotionVector,
    ): Double {
        var sumSquares = 0.0
        var counted = 0
        repeat(totalSamples) { index ->
            val result = filter.process(index * dt, signal(index))
            if (index >= skipSamples) {
                sumSquares += result.filteredAccel.magnitude * result.filteredAccel.magnitude
                counted++
            }
        }
        return sqrt(sumSquares / counted)
    }
}
