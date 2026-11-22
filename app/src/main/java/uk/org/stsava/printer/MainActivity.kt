package uk.org.stsava.printer

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private lateinit var status: TextView
    private lateinit var deviceList: LinearLayout
    private lateinit var printButton: Button
    private val btAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var pendingReceipt: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        handlePrintIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 41)
        } else showPairedDevices()
    }


    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePrintIntent(intent)
    }

    private fun handlePrintIntent(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_VIEW) return
        val receipt = intent.data?.getQueryParameter("text") ?: return
        pendingReceipt = receipt
        status.text = "Receipt received. Connecting to X5h-953E…"
        val bt = btAdapter ?: run { status.text = "Bluetooth is unavailable."; return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Allow Nearby devices permission, then reopen the receipt from the register."
            return
        }
        val device = try { bt.bondedDevices.firstOrNull { it.name == "X5h-953E" } }
            catch (_: SecurityException) { null }
        if (device == null) {
            status.text = "Pair X5h-953E in Android Bluetooth settings, then send the receipt again."
            return
        }
        if (socket?.isConnected == true) {
            pendingReceipt = null
            printReceiptText(receipt)
        } else connect(device)
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
            text = "Bluetooth receipt printer for St Sava Church"
            textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 8, 0, 18)
        })
        status = TextView(this).apply {
            text = "Checking Bluetooth…"; textSize = 16f
            setPadding(14, 14, 14, 14); setBackgroundColor(0xFFE8E4D8.toInt())
        }
        root.addView(status)
        printButton = Button(this).apply {
            text = "Print test receipt"
            isEnabled = false
            setOnClickListener { printTestReceipt() }
        }
        root.addView(printButton)
        root.addView(Button(this).apply {
            text = "Refresh paired devices"; setOnClickListener { showPairedDevices() }
        })
        root.addView(TextView(this).apply {
            text = "Choose X5h-953E below. Connect first, then print the short test receipt."
            textSize = 14f; setPadding(0, 12, 0, 12)
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
        printButton.isEnabled = false
        thread(name = "printer-connect") {
            try {
                try { socket?.close() } catch (_: IOException) { }
                val newSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                newSocket.connect()
                socket = newSocket
                runOnUiThread {
                    status.text = "Connected to " + (device.name ?: "Bluetooth device") + "."
                    printButton.isEnabled = true
                    pendingReceipt?.let { receipt ->
                        pendingReceipt = null
                        printReceiptText(receipt)
                    } ?: run { status.text = "Connected to " + (device.name ?: "Bluetooth device") + ". Ready for a test print." }
                }
            } catch (e: Exception) {
                try { socket?.close() } catch (_: IOException) { }
                socket = null
                runOnUiThread {
                    status.text = "Could not connect. Check that X5h-953E is paired and TinyPrint is closed, then try again. (" + e.javaClass.simpleName + ")"
                }
            }
        }
    }

    private fun printTestReceipt() {
        val activeSocket = socket
        if (activeSocket == null || !activeSocket.isConnected) {
            status.text = "Please connect to X5h-953E first."
            printButton.isEnabled = false
            return
        }
        printButton.isEnabled = false
        status.text = "Sending test receipt to X5h-953E… Please wait."
        thread(name = "printer-print") {
            try {
                val bytes = buildTestReceiptJob()
                val output = activeSocket.outputStream
                var offset = 0
                while (offset < bytes.size) {
                    val count = minOf(80, bytes.size - offset)
                    output.write(bytes, offset, count)
                    output.flush()
                    offset += count
                    Thread.sleep(25)
                }
                runOnUiThread {
                    status.text = "Test receipt sent. Check the printer."
                    printButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Print attempt failed: " + e.javaClass.simpleName + ". Reconnect and try again."
                    printButton.isEnabled = socket?.isConnected == true
                }
            }
        }
    }


    private fun printReceiptText(receipt: String) {
        val activeSocket = socket
        if (activeSocket == null || !activeSocket.isConnected) {
            pendingReceipt = receipt
            status.text = "Printer is not connected. Reopen the receipt from Church Register after connecting."
            return
        }
        printButton.isEnabled = false
        status.text = "Printing Church Register receipt…"
        thread(name = "receipt-print") {
            try {
                val bytes = buildReceiptJob(receipt)
                val output = activeSocket.outputStream
                var offset = 0
                while (offset < bytes.size) {
                    val count = minOf(80, bytes.size - offset)
                    output.write(bytes, offset, count)
                    output.flush()
                    offset += count
                    Thread.sleep(25)
                }
                runOnUiThread { status.text = "Church Register receipt sent. Check the printer."; printButton.isEnabled = true }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Receipt print failed: " + e.javaClass.simpleName; printButton.isEnabled = socket?.isConnected == true }
            }
        }
    }

    private fun buildReceiptJob(receipt: String): ByteArray {
        // Use larger, bold characters with generous spacing: the printer's
        // test heading is readable, while the previous small receipt font was not.
        val width = 384
        val wrapped = ArrayList<String>()
        receipt.lines().forEach { line ->
            if (line.isEmpty()) wrapped.add("")
            else line.chunked(21).forEach { wrapped.add(it) }
        }
        wrapped.add("")
        wrapped.add("Cashier: Luka Krivosic")
        val lineHeight = 44
        val height = maxOf(600, wrapped.size * lineHeight + 100)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 27f
            isFilterBitmap = false
            // Keep antialiased edges but avoid faint grey output on thermal paper.
            isSubpixelText = false
            hinting = Paint.HINTING_ON
        }
        var y = 38f
        wrapped.forEach { line ->
            if (line.isNotEmpty()) canvas.drawText(line, width / 2f, y, paint)
            y += lineHeight
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        val job = ArrayList<Byte>()
        job.addAll(packet(0xA4, byteArrayOf(0x33)))
        job.addAll(packet(0xAF, byteArrayOf(0x40, 0x1F)))
        job.addAll(packet(0xBE, byteArrayOf(0x01)))
        job.addAll(packet(0xBD, byteArrayOf(0x0A)))
        val line = IntArray(width)
        for (row in 0 until height) {
            for (x in 0 until width) {
                val c = pixels[row * width + x]
                val luminance = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                line[x] = if (luminance < 175) 1 else 0
            }
            val rle = encodeRle(line)
            if (rle.size <= (width + 7) / 8) job.addAll(packet(0xBF, rle))
            else {
                val raw = ByteArray((width + 7) / 8)
                for (x in 0 until width) {
                    if (line[x] == 1) raw[x / 8] = (raw[x / 8].toInt() or (1 shl (7 - (x % 8)))).toByte()
                }
                job.addAll(packet(0xA2, raw))
            }
            if ((row + 1) % 200 == 0) job.addAll(packet(0xBD, byteArrayOf(0x0A)))
        }
        job.addAll(packet(0xA1, byteArrayOf(0x60, 0x00, 0x11)))
        job.addAll(packet(0xA3, byteArrayOf(0x00)))
        return ByteArray(job.size) { i -> job[i] }
    }

    private fun buildTestReceiptJob(): ByteArray {
        val width = 384
        val height = 650 // Adds approximately 4 cm of blank tear-off paper at 200 DPI
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        paint.textSize = 29f
        canvas.drawText("ST SAVA CHURCH", width / 2f, 48f, paint)
        canvas.drawLine(18f, 66f, 366f, 66f, paint.apply { strokeWidth = 2f })
        paint.textSize = 29f
        canvas.drawText("PRINTER TEST", width / 2f, 112f, paint)
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BLUETOOTH CONNECTED", width / 2f, 165f, paint)
        canvas.drawText("X5h PRINTER READY", width / 2f, 207f, paint)
        paint.textSize = 23f
        canvas.drawText(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.UK).format(Date()), width / 2f, 252f, paint)
        canvas.drawLine(18f, 275f, 366f, 275f, paint.apply { strokeWidth = 2f })
        paint.textSize = 21f
        canvas.drawText("TEST ONLY - NO SALE", width / 2f, 312f, paint)
        canvas.drawText("Cashier: Luka Krivosic", width / 2f, 355f, paint)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        val job = ArrayList<Byte>()
        job.addAll(packet(0xA4, byteArrayOf(0x33)))
        job.addAll(packet(0xAF, byteArrayOf(0x40, 0x1F)))
        job.addAll(packet(0xBE, byteArrayOf(0x01)))
        job.addAll(packet(0xBD, byteArrayOf(0x0A)))

        val line = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = pixels[y * width + x]
                val luminance = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                line[x] = if (luminance < 175) 1 else 0
            }
            val rle = encodeRle(line)
            if (rle.size <= (width + 7) / 8) {
                job.addAll(packet(0xBF, rle))
            } else {
                val raw = ByteArray((width + 7) / 8)
                for (x in 0 until width) {
                    if (line[x] == 1) raw[x / 8] = (raw[x / 8].toInt() or (1 shl (7 - (x % 8)))).toByte()
                }
                job.addAll(packet(0xA2, raw))
            }
            if ((y + 1) % 200 == 0) job.addAll(packet(0xBD, byteArrayOf(0x0A)))
        }
        job.addAll(packet(0xA1, byteArrayOf(0x60, 0x00, 0x11)))
        job.addAll(packet(0xA3, byteArrayOf(0x00)))
        return ByteArray(job.size) { i -> job[i] }
    }

    private fun encodeRle(line: IntArray): ByteArray {
        val out = ArrayList<Byte>()
        var colour = line[0]
        var count = 1
        fun appendRun(c: Int, originalCount: Int) {
            var remaining = originalCount
            while (remaining > 127) {
                out.add(((c shl 7) or 127).toByte())
                remaining -= 127
            }
            if (remaining > 0) out.add(((c shl 7) or remaining).toByte())
        }
        for (i in 1 until line.size) {
            if (line[i] == colour) count++ else {
                appendRun(colour, count)
                colour = line[i]
                count = 1
            }
        }
        appendRun(colour, count)
        return ByteArray(out.size) { i -> out[i] }
    }

    private fun packet(command: Int, payload: ByteArray): List<Byte> {
        val result = ArrayList<Byte>()
        result.add(0x51)
        result.add(0x78)
        result.add(command.toByte())
        result.add(0x00)
        result.add((payload.size and 0xFF).toByte())
        result.add(((payload.size shr 8) and 0xFF).toByte())
        payload.forEach { result.add(it) }
        result.add(crc8(payload).toByte())
        result.add(0xFF.toByte())
        return result
    }

    private fun crc8(data: ByteArray): Int {
        var crc = 0
        for (value in data) {
            crc = crc xor (value.toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 0x80) != 0) ((crc shl 1) xor 0x07) and 0xFF
                else (crc shl 1) and 0xFF
            }
        }
        return crc
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showPairedDevices()
                if (pendingReceipt != null) handlePrintIntent(intent)
            }
            else status.text = "Bluetooth permission was not granted. Enable Nearby devices permission in Settings."
        }
    }

    override fun onDestroy() {
        try { socket?.close() } catch (_: IOException) { }
        socket = null
        super.onDestroy()
    }
}
