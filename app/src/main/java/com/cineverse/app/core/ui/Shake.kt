package com.cineverse.app.core.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs

/**
 * A shake detector, ported from the website's js/shake.js.
 *
 * Three hard jolts inside a second and a bit. One jolt is a phone being put
 * down on a table; three in quick succession is somebody meaning it. After a
 * shake it stays quiet for a moment, so one enthusiastic shake is one shake.
 *
 * The force is in m/s² INCLUDING gravity — the same units the browser's
 * `accelerationIncludingGravity` reports — so 26 is roughly two and a half g
 * summed over the three axes, which a phone lying still never reaches.
 */
class ShakeDetector(
    private val force: Float = 26f,
    private val needed: Int = 3,
    private val windowMs: Long = 1_200,
    private val cooldownMs: Long = 2_500,
) {
    private val hits = ArrayDeque<Long>()
    private var last = Long.MIN_VALUE / 2

    /** Feed one sample; true on the sample that completes a shake. */
    fun sample(x: Float, y: Float, z: Float, at: Long): Boolean {
        if (at - last < cooldownMs) return false
        if (abs(x) + abs(y) + abs(z) < force) return false
        while (hits.isNotEmpty() && at - hits.first() >= windowMs) hits.removeFirst()
        hits.addLast(at)
        if (hits.size < needed) return false
        hits.clear()
        last = at
        return true
    }
}

/**
 * Calls [onShake] when the phone is shaken — only while the screen is resumed
 * and [enabled] is true, so the accelerometer is never kept awake behind a
 * locked screen or a screen the user has left.
 */
@Composable
fun OnShake(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(onShake)
    val active = enabled && resumed.isAtLeast(Lifecycle.State.RESUMED)

    DisposableEffect(active) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!active || manager == null || sensor == null) return@DisposableEffect onDispose { }
        val detector = ShakeDetector()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                if (detector.sample(x, y, z, System.currentTimeMillis())) latest()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
}
