package com.e46.boardlauncher.telemetry

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat

data class TelemetrySnapshot(
    val speedKmh: Int,
    val hasGpsFix: Boolean,
    val btConnected: Boolean,
    val wifiConnected: Boolean,
    val voltageOrBattery: String
)

class VehicleTelemetryManager(
    private val context: Context,
    private val onUpdate: (TelemetrySnapshot) -> Unit
) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var lastSpeedKmh: Int = 0
    private var hasGpsFix: Boolean = false
    private var isListeningGps: Boolean = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            hasGpsFix = true
            lastSpeedKmh = if (location.hasSpeed()) {
                (location.speed * 3.6f).toInt().coerceIn(0, 320)
            } else {
                0
            }
            emitSnapshot()
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) { emitSnapshot() }
        override fun onProviderDisabled(provider: String) {
            hasGpsFix = false
            lastSpeedKmh = 0
            emitSnapshot()
        }
    }

    private val systemStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            emitSnapshot()
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        try {
            context.registerReceiver(systemStateReceiver, filter)
        } catch (_: Exception) {
        }

        startGpsIfPermitted()
        emitSnapshot()
    }

    fun stop() {
        try {
            context.unregisterReceiver(systemStateReceiver)
        } catch (_: Exception) {
        }
        if (isListeningGps) {
            try {
                locationManager?.removeUpdates(locationListener)
            } catch (_: Exception) {
            }
            isListeningGps = false
        }
    }

    fun startGpsIfPermitted() {
        if (isListeningGps) return
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine || locationManager == null) return

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.0f,
                    locationListener
                )
                isListeningGps = true
            }
        } catch (_: Exception) {
        }
    }

    fun emitSnapshot() {
        onUpdate(
            TelemetrySnapshot(
                speedKmh = lastSpeedKmh,
                hasGpsFix = hasGpsFix,
                btConnected = isBluetoothActive(),
                wifiConnected = isWifiConnected(),
                voltageOrBattery = readSystemPowerText()
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun isBluetoothActive(): Boolean {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            adapter.isEnabled
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun isWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(net) ?: return false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            } else {
                val info = cm.activeNetworkInfo
                info != null && info.isConnected
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readSystemPowerText(): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (pct in 1..100) "ACC • $pct%" else "12V ACC"
        } catch (_: Exception) {
            "12V ACC"
        }
    }
}
