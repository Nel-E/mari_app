package com.mari.app.shake

import android.content.Context
import android.hardware.SensorEventListener
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.hardware.SensorManager
import android.hardware.SensorEvent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShakeDetectorTest {

    private lateinit var sensorManager: SensorManager
    private lateinit var detector: ShakeDetector
    private lateinit var listener: SensorEventListener

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        detector = ShakeDetector(sensorManager)
        detector.start(ShakeConfig(thresholdMs2 = 12f, durationMs = 100L, debounceMs = 1500L))
        listener = extractListener(detector)
    }

    @Test
    fun `no shake when magnitude stays below threshold`() = runTest {
        detector.shakeEvents.test {
            repeat(5) { i ->
                sendEvent(listener, magnitude = 5f, timestampNs = i * 50_000_000L)
            }
            expectNoEvents()
            cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `shake emitted when magnitude exceeds threshold for required duration`() = runTest(UnconfinedTestDispatcher()) {
        detector.shakeEvents.test {
            val t0 = 0L
            val t1 = 150_000_000L // 150 ms in ns — exceeds durationMs=100
            sendEvent(listener, magnitude = 15f, timestampNs = t0)
            sendEvent(listener, magnitude = 15f, timestampNs = t1)
            assertThat(awaitItem()).isEqualTo(Unit)
            cancel()
        }
    }

    @Test
    fun `no shake when duration is too short`() = runTest {
        detector.shakeEvents.test {
            val t0 = 0L
            val t1 = 50_000_000L // 50 ms — less than durationMs=100
            sendEvent(listener, magnitude = 15f, timestampNs = t0)
            sendEvent(listener, magnitude = 15f, timestampNs = t1)
            expectNoEvents()
            cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `debounce suppresses second shake within window`() = runTest(UnconfinedTestDispatcher()) {
        detector.shakeEvents.test {
            triggerShake(listener, startNs = 0L)
            awaitItem() // first shake emitted

            // second shake attempt 500ms later — within 1500ms debounce
            triggerShake(listener, startNs = 500_000_000L)
            expectNoEvents()
            cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `second shake allowed after debounce window`() = runTest(UnconfinedTestDispatcher()) {
        detector.shakeEvents.test {
            triggerShake(listener, startNs = 0L)
            awaitItem() // first shake

            // second shake 2000ms later — outside 1500ms debounce
            triggerShake(listener, startNs = 2_000_000_000L)
            assertThat(awaitItem()).isEqualTo(Unit)
            cancel()
        }
    }

    @Test
    fun `dropping below threshold resets duration tracking`() = runTest {
        detector.shakeEvents.test {
            val t0 = 0L
            sendEvent(listener, magnitude = 15f, timestampNs = t0)       // starts timer
            sendEvent(listener, magnitude = 2f, timestampNs = 60_000_000L) // drops below — resets
            sendEvent(listener, magnitude = 15f, timestampNs = 70_000_000L) // restarts timer
            // only 10ms since restart — no shake
            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun `stop unregisters listener and clears state`() = runTest {
        detector.stop()
        val freshListener = extractListener(detector)
        detector.shakeEvents.test {
            triggerShake(freshListener, startNs = 0L)
            // detector was stopped — no registration, no emit
            expectNoEvents()
            cancel()
        }
    }

    // — helpers —

    private fun triggerShake(l: SensorEventListener, startNs: Long) {
        sendEvent(l, magnitude = 15f, timestampNs = startNs)
        sendEvent(l, magnitude = 15f, timestampNs = startNs + 150_000_000L)
    }

    private fun sendEvent(l: SensorEventListener, magnitude: Float, timestampNs: Long) {
        val event = makeSensorEvent(magnitude, timestampNs)
        l.onSensorChanged(event)
    }

    private fun makeSensorEvent(magnitude: Float, timestampNs: Long): SensorEvent {
        val ctor = SensorEvent::class.java.getDeclaredConstructor(Int::class.java)
        ctor.isAccessible = true
        val event = ctor.newInstance(3) as SensorEvent
        // Put full magnitude on x-axis so sqrt(x²+y²+z²) == magnitude
        event.values[0] = magnitude
        event.values[1] = 0f
        event.values[2] = 0f
        event.timestamp = timestampNs
        return event
    }

    private fun extractListener(d: ShakeDetector): SensorEventListener {
        val field = ShakeDetector::class.java.getDeclaredField("listener")
        field.isAccessible = true
        return field.get(d) as SensorEventListener
    }
}
