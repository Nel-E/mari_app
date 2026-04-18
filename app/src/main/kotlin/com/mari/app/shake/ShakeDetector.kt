package com.mari.app.shake

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class ShakeDetector @Inject constructor(
    private val sensorManager: SensorManager,
) : ShakeEventSource {

    private val _shakeEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val shakeEvents: SharedFlow<Unit> = _shakeEvents.asSharedFlow()

    private var config = ShakeConfig()
    private var aboveThresholdSinceNs: Long? = null
    private var lastShakeNs: Long = 0L

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)

            if (magnitude >= config.thresholdMs2) {
                val since = aboveThresholdSinceNs
                if (since == null) {
                    aboveThresholdSinceNs = event.timestamp
                } else if ((event.timestamp - since) >= config.durationMs * 1_000_000L) {
                    if ((event.timestamp - lastShakeNs) >= config.debounceMs * 1_000_000L) {
                        _shakeEvents.tryEmit(Unit)
                        lastShakeNs = event.timestamp
                    }
                    aboveThresholdSinceNs = null
                }
            } else {
                aboveThresholdSinceNs = null
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    fun start(shakeConfig: ShakeConfig) {
        config = shakeConfig
        aboveThresholdSinceNs = null
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: return
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
        aboveThresholdSinceNs = null
    }
}
