package dev.tomerklein.dradis.telemetry

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dev.tomerklein.dradis.commands.CommandSink
import dev.tomerklein.dradis.commands.SensorsInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "dradis.sensor"

/**
 * Collects step + motion sensor data and publishes it to the `sensors` topic.
 * - Step Counter (TYPE_STEP_COUNTER): cumulative steps since boot.
 * - Step Detector (TYPE_STEP_DETECTOR): counted since the service started.
 * - Significant Motion (TYPE_SIGNIFICANT_MOTION): a one-shot trigger; we record
 *   that motion occurred and re-arm it.
 *
 * Step sensors need the ACTIVITY_RECOGNITION runtime permission (API 29+);
 * without it they simply produce no readings.
 */
class SensorReporter(private val sink: CommandSink) : SensorEventListener {

    private val json = Json { encodeDefaults = true }
    private val sm = sink.appContext.getSystemService(SensorManager::class.java)
    private val stepCounter = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetector = sm?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val significantMotion = sm?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    @Volatile
    private var stepCount: Long? = null
    private val stepsDetected = AtomicLong(0)
    @Volatile
    private var motionDetected = false

    private val motionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            motionDetected = true
            // Significant-motion is one-shot — re-arm it for the next event.
            significantMotion?.let { sm?.requestTriggerSensor(this, it) }
        }
    }

    fun start() {
        if (sm == null) return
        if (!activityRecognitionGranted()) {
            sink.logInfo("Sensors: ACTIVITY_RECOGNITION not granted — step data unavailable")
        }
        stepCounter?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        stepDetector?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        significantMotion?.let { sm.requestTriggerSensor(motionListener, it) }
    }

    fun stop() {
        sm ?: return
        runCatching { sm.unregisterListener(this) }
        significantMotion?.let { runCatching { sm.cancelTriggerSensor(motionListener, it) } }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> stepCount = event.values.firstOrNull()?.toLong()
            Sensor.TYPE_STEP_DETECTOR -> stepsDetected.incrementAndGet()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Publish a sensors snapshot. The motion flag is reset after each report. */
    fun publish() {
        val info = SensorsInfo(
            stepCounter = stepCount,
            stepsDetected = stepsDetected.get(),
            motionDetected = motionDetected,
            time = System.currentTimeMillis() / 1000,
        )
        motionDetected = false
        runCatching { sink.publish(sink.topics.sensors, json.encodeToString(info)) }
            .onFailure { Log.e(TAG, "sensors publish failed", it) }
    }

    private fun activityRecognitionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                sink.appContext, Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
}
