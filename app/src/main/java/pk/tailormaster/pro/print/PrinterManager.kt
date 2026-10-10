package pk.tailormaster.pro.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

/** ESC/POS command helpers. */
object EscPos {
    val INIT = byteArrayOf(0x1B, 0x40)
    fun feed(lines: Int) = byteArrayOf(0x1B, 0x64, lines.coerceIn(0, 10).toByte())
    val CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    /** DLE EOT 1 — printer status. */
    val STATUS_PRINTER = byteArrayOf(0x10, 0x04, 0x01)
    /** DLE EOT 4 — paper roll sensor status. */
    val STATUS_PAPER = byteArrayOf(0x10, 0x04, 0x04)

    /** Raster bit image (GS v 0), sent in bands so that small printer buffers are not overrun. */
    fun raster(bmp: Bitmap, bandRows: Int = 96): List<ByteArray> {
        val w = bmp.width; val h = bmp.height
        val bytesPerRow = (w + 7) / 8
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = mutableListOf<ByteArray>()
        var y0 = 0
        while (y0 < h) {
            val rows = minOf(bandRows, h - y0)
            val bos = ByteArrayOutputStream()
            bos.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00,
                (bytesPerRow and 0xFF).toByte(), ((bytesPerRow shr 8) and 0xFF).toByte(),
                (rows and 0xFF).toByte(), ((rows shr 8) and 0xFF).toByte()))
            for (y in y0 until y0 + rows) {
                for (bx in 0 until bytesPerRow) {
                    var b = 0
                    for (bit in 0 until 8) {
                        val x = bx * 8 + bit
                        if (x < w && px[y * w + x] == Color.BLACK) b = b or (0x80 shr bit)
                    }
                    bos.write(b)
                }
            }
            out += bos.toByteArray()
            y0 += rows
        }
        return out
    }
}

data class PrinterDevice(val name: String, val address: String, val bonded: Boolean)

sealed class PrinterState {
    object Disconnected : PrinterState()
    data class Connecting(val name: String) : PrinterState()
    data class Connected(val name: String) : PrinterState()
    data class Printing(val name: String) : PrinterState()
    data class Error(val message: String) : PrinterState()
}

/** Result of a print job, reported honestly. */
data class PrintResult(val status: String, val message: String) {
    val ok get() = status != STATUS_FAILED
    companion object {
        const val STATUS_OK = "OK"        // printer answered after printing
        const val STATUS_SENT = "SENT"    // data sent, printer did not answer status query
        const val STATUS_FAILED = "FAILED"
    }
}

class PrinterManager(private val context: Context) {
    private val spp: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var connectedAddress: String? = null
    private val lock = Mutex()

    private val _state = MutableStateFlow<PrinterState>(PrinterState.Disconnected)
    val state: StateFlow<PrinterState> = _state

    private val _found = MutableStateFlow<List<PrinterDevice>>(emptyList())
    val found: StateFlow<List<PrinterDevice>> = _found
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isSupported() = adapter != null
    @SuppressLint("MissingPermission")
    fun isEnabled() = try { adapter?.isEnabled == true } catch (e: SecurityException) { false }

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<PrinterDevice> = try {
        adapter?.bondedDevices?.map { PrinterDevice(it.name ?: it.address, it.address, true) }?.sortedBy { it.name } ?: emptyList()
    } catch (e: SecurityException) { emptyList() }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (d != null) {
                        val name = try { d.name } catch (e: SecurityException) { null } ?: d.address
                        val bonded = try { d.bondState == BluetoothDevice.BOND_BONDED } catch (e: SecurityException) { false }
                        if (_found.value.none { it.address == d.address }) _found.value = _found.value + PrinterDevice(name, d.address, bonded)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _scanning.value = false
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    _found.value = _found.value.map { pd ->
                        val bonded = bondedDevices().any { it.address == pd.address }
                        pd.copy(bonded = bonded)
                    }
                }
            }
        }
    }
    private var registered = false

    @SuppressLint("MissingPermission")
    fun startScan(): String? {
        val a = adapter ?: return "اس فون میں بلوٹوتھ نہیں ہے"
        if (!hasPermissions()) return "بلوٹوتھ کی اجازت درکار ہے"
        if (!isEnabled()) return "پہلے فون کا بلوٹوتھ آن کریں"
        if (!registered) {
            val f = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, f, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        _found.value = emptyList()
        return try {
            a.cancelDiscovery()
            _scanning.value = a.startDiscovery()
            if (_scanning.value) null else "تلاش شروع نہیں ہو سکی۔ لوکیشن / بلوٹوتھ آن کریں"
        } catch (e: SecurityException) { "بلوٹوتھ کی اجازت درکار ہے" }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() { try { adapter?.cancelDiscovery() } catch (_: SecurityException) {} ; _scanning.value = false }

    @SuppressLint("MissingPermission")
    fun pair(address: String): String? = try {
        val d = adapter?.getRemoteDevice(address) ?: return "ڈیوائس نہیں ملی"
        if (d.createBond()) null else "جوڑنے کی درخواست ناکام۔ فون کی بلوٹوتھ سیٹنگ سے جوڑیں (PIN عام طور پر 0000 یا 1234)"
    } catch (e: SecurityException) { "بلوٹوتھ کی اجازت درکار ہے" }

    @SuppressLint("MissingPermission")
    private fun openSocket(address: String): BluetoothSocket {
        val a = adapter ?: throw IOException("اس فون میں بلوٹوتھ نہیں ہے")
        if (!a.isEnabled) throw IOException("فون کا بلوٹوتھ بند ہے")
        val d = a.getRemoteDevice(address)
        try { a.cancelDiscovery() } catch (_: SecurityException) {}
        val attempts = listOf<() -> BluetoothSocket>(
            { d.createRfcommSocketToServiceRecord(spp) },
            { d.createInsecureRfcommSocketToServiceRecord(spp) },
            { d.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType!!).invoke(d, 1) as BluetoothSocket },
        )
        var last: Exception? = null
        for (make in attempts) {
            try {
                val s = make()
                s.connect()
                return s
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException("پرنٹر سے رابطہ نہیں ہو سکا: " + (last?.message ?: ""))
    }

    suspend fun connect(address: String, name: String): String? = lock.withLock {
        withContext(Dispatchers.IO) { connectLocked(address, name) }
    }

    private fun connectLocked(address: String, name: String): String? {
        if (socket?.isConnected == true && connectedAddress == address) return null
        closeLocked()
        if (!hasPermissions()) return "بلوٹوتھ کی اجازت درکار ہے"
        _state.value = PrinterState.Connecting(name)
        return try {
            socket = openSocket(address)
            connectedAddress = address
            _state.value = PrinterState.Connected(name)
            null
        } catch (e: Exception) {
            _state.value = PrinterState.Error(e.message ?: "رابطہ ناکام")
            e.message ?: "رابطہ ناکام"
        }
    }

    suspend fun disconnect() = lock.withLock { withContext(Dispatchers.IO) { closeLocked() } }

    private fun closeLocked() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        connectedAddress = null
        _state.value = PrinterState.Disconnected
    }

    /** Reads a single status byte if the printer answers within [timeoutMs]. */
    private suspend fun query(cmd: ByteArray, timeoutMs: Long): Int? {
        val s = socket ?: return null
        return try {
            val input = s.inputStream
            while (input.available() > 0) input.read()
            s.outputStream.write(cmd); s.outputStream.flush()
            var waited = 0L
            while (waited < timeoutMs) {
                if (input.available() > 0) return input.read()
                delay(50); waited += 50
            }
            null
        } catch (e: Exception) { null }
    }

    /**
     * Prints the bitmaps (one per copy). Status is reported honestly:
     * OK only if the printer answered a status query after the job; SENT if data went out but the
     * printer gave no answer (many cheap printers never answer); FAILED on any error.
     */
    suspend fun print(address: String, name: String, pages: List<Bitmap>, feedLines: Int, cut: Boolean): PrintResult =
        lock.withLock {
            withContext(Dispatchers.IO) {
                if (address.isBlank()) return@withContext PrintResult(PrintResult.STATUS_FAILED, "پہلے پرنٹر منتخب کریں (سیٹنگز ← پرنٹر)")
                connectLocked(address, name)?.let { return@withContext PrintResult(PrintResult.STATUS_FAILED, it) }
                _state.value = PrinterState.Printing(name)
                try {
                    // Paper check before printing (only if the printer supports it)
                    val paper = query(EscPos.STATUS_PAPER, 600)
                    if (paper != null && (paper and 0x60) == 0x60) {
                        _state.value = PrinterState.Connected(name)
                        return@withContext PrintResult(PrintResult.STATUS_FAILED, "پرنٹر میں کاغذ ختم ہے")
                    }
                    val out = socket!!.outputStream
                    for (bmp in pages) {
                        out.write(EscPos.INIT)
                        for (band in EscPos.raster(bmp)) {
                            out.write(band); out.flush()
                            delay(40)
                        }
                        out.write(EscPos.feed(feedLines))
                        if (cut) out.write(EscPos.CUT)
                        out.flush()
                    }
                    val st = query(EscPos.STATUS_PRINTER, 2500)
                    _state.value = PrinterState.Connected(name)
                    when {
                        st == null -> PrintResult(PrintResult.STATUS_SENT, "رسید پرنٹر کو بھیج دی گئی، لیکن پرنٹر نے تصدیق نہیں کی۔ کاغذ چیک کر لیں")
                        (st and 0x08) != 0 -> PrintResult(PrintResult.STATUS_FAILED, "پرنٹر آف لائن ہے یا ڈھکن کھلا ہے")
                        else -> PrintResult(PrintResult.STATUS_OK, "رسید پرنٹ ہو گئی (پرنٹر نے تصدیق کی)")
                    }
                } catch (e: Exception) {
                    closeLocked()
                    _state.value = PrinterState.Error("پرنٹ ناکام: " + (e.message ?: ""))
                    PrintResult(PrintResult.STATUS_FAILED, "پرنٹ ناکام: رابطہ ٹوٹ گیا۔ پرنٹر آن اور قریب رکھیں۔ " + (e.message ?: ""))
                }
            }
        }
}
