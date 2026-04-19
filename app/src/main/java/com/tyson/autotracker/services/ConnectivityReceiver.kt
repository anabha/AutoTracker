package com.tyson.autotracker.services

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.tyson.autotracker.data.AutotrackerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ConnectivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val mac = device?.address ?: return
                checkConnectivity(context, bluetoothMac = mac, isConnect = true)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                // If anything disconnects, tell the service to re-verify its connections
                if (LocationService.isTracking.value) {
                    val serviceIntent = Intent(context, LocationService::class.java).apply {
                        this.action = "CHECK_CONNECTION"
                    }
                    context.startService(serviceIntent)
                }
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                val info = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                if (info?.isConnected == true) {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val ssid = wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
                    if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                        checkConnectivity(context, wifiSsid = ssid, isConnect = true)
                    }
                } else if (info?.isConnected == false) {
                    // Wi-Fi disconnected, tell service to check
                    if (LocationService.isTracking.value) {
                        val serviceIntent = Intent(context, LocationService::class.java).apply {
                            this.action = "CHECK_CONNECTION"
                        }
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }

    private fun checkConnectivity(context: Context, bluetoothMac: String? = null, wifiSsid: String? = null, isConnect: Boolean) {
        if (!isConnect) return // Disconnects handled by CHECK_CONNECTION signal

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val db = AutotrackerDatabase.getDatabase(context)
            val vehicles = db.vehicleDao().getAllVehicles().first()

            val matchedVehicle = vehicles.find {
                (bluetoothMac != null && it.bluetoothMacAddress == bluetoothMac) ||
                (wifiSsid != null && it.wifiSsid == wifiSsid)
            }

            if (matchedVehicle != null) {
                // Start Trip if not already tracking this vehicle
                if (!(LocationService.isTracking.value && LocationService.activeVehicleId.value == matchedVehicle.id)) {
                    val serviceIntent = Intent(context, LocationService::class.java).apply {
                        putExtra("VEHICLE_ID", matchedVehicle.id)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
        }
    }
}