package uk.org.stsava.printer

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private lateinit var status: TextView
    private lateinit var deviceList: LinearLayout
    private val btAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 41)
        } else showPairedDevices()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(0xFFF7F5EF.toInt())
        }
        root.addView(TextView(this).apply {
            text = "St Sava Printer"; textSize = 26f
            setTextColor(0xFF174B35.toInt()); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Bluetooth setup for the church receipt printer"
            textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 8, 0, 22)
        })
        status = TextView(this).apply {
            text = "Checking Bluetooth…"; textSize = 16f
            setPadding(14, 14, 14, 14); setBackgroundColor(0xFFE8E4D8.toInt())
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Refresh paired devices"; setOnClickListener { showPairedDevices() }
        })
        root.addView(TextView(this).apply {
            text = "This preview only checks the Bluetooth connection. It does not print or send printer commands yet."
            textSize = 14f; setPadding(0, 18, 0, 12)
        })
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(deviceList) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showPairedDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Bluetooth permission is needed. Please allow it and refresh."
            return
        }
        val bt = btAdapter
        if (bt == null) { status.text = "This tablet does not report Bluetooth support."; return }
        if (!bt.isEnabled) { status.text = "Please turn Bluetooth on in Android Settings, then refresh."; return }
        deviceList.removeAllViews()
        val paired = try { bt.bondedDevices.orEmpty().sortedBy { it.name ?: it.address } }
        catch (_: SecurityException) { status.text = "Bluetooth permission is missing."; return }
        if (paired.isEmpty()) {
            status.text = "No paired devices found. Pair X5h-953E in Android Bluetooth settings first."
            return
        }
        status.text = "Choose your paired printer below."
        paired.forEach { device ->
            deviceList.addView(Button(this).apply {
                text = (device.name ?: "Bluetooth device") + "\n" + device.address
                setOnClickListener { connect(device) }
            })
        }
    }

    private fun connect(device: BluetoothDevice) {
        status.text = "Connecting to " + (device.name ?: "printer") + "…"
        thread(name = "printer-connect") {
            try {
                socket?.close()
                val newSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                newSocket.connect()
                socket = newSocket
                runOnUiThread { status.text = "Connected to " + (device.name ?: "Bluetooth device") + ". Connection test passed." }
            } catch (e: Exception) {
                try { socket?.close() } catch (_: IOException) { }
                socket = null
                runOnUiThread {
                    status.text = "Could not connect. Check that X5h-953E is paired and TinyPrint is closed, then try again. (" + e.javaClass.simpleName + ")"
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) showPairedDevices()
            else status.text = "Bluetooth permission was not granted. Enable Nearby devices permission in Settings."
        }
    }

    override fun onDestroy() {
        try { socket?.close() } catch (_: IOException) { }
        socket = null
        super.onDestroy()
    }
}
