package pk.tailormaster.pro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class AppSettings(
    // Shop identity
    val shopName: String = "ٹیلر ماسٹر",
    val shopPhone: String = "",
    val shopAddress: String = "",
    val logoFile: String = "",
    // Receipt designer
    val receiptHeading: String = "",
    val footer: String = "شکریہ! دوبارہ تشریف لائیں",
    val terms: String = "رسید کے بغیر کپڑے نہیں دیے جائیں گے۔ ۳۰ دن بعد دکان ذمہ دار نہیں ہوگی۔",
    val deliveryTerms: String = "",
    val receiptFontSize: Int = 24,
    val paperWidthMm: Int = 58,
    /** Printable dots per line. 58mm printers usually 384, 80mm usually 576. */
    val printableDots: Int = 384,
    val marginDots: Int = 4,
    val receiptFont: String = "naskh",
    val showLogo: Boolean = true,
    val showFatherName: Boolean = false,
    val showAddress: Boolean = false,
    val showMeasurementsOnBooking: Boolean = false,
    val showTerms: Boolean = true,
    val showSignature: Boolean = true,
    val copyType: String = "کسٹمر کاپی",
    val printShopCopy: Boolean = false,
    val feedLines: Int = 3,
    val cutPaper: Boolean = false,
    // Printer
    val printerAddress: String = "",
    val printerName: String = "",
    // General
    val currencyLabel: String = "روپے",
    val dateFormat: String = "dd/MM/yyyy",
    val urduDigits: Boolean = false,
    val defaultUnit: String = "انچ",
    val theme: String = "system",
    val uiFont: String = "system",
    val dueSoonDays: Int = 1,
    // Security & backup
    val pinHash: String = "",
    val lastBackupAt: Long = 0,
    val sampleLoaded: Boolean = false,
    val seeded: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tailor_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state
    val value: AppSettings get() = _state.value

    private fun load(): AppSettings = try {
        prefs.getString(KEY, null)?.let { json.decodeFromString(AppSettings.serializer(), it) } ?: AppSettings()
    } catch (e: Exception) {
        AppSettings()
    }

    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_state.value)
        prefs.edit().putString(KEY, json.encodeToString(AppSettings.serializer(), n)).apply()
        _state.value = n
    }

    fun replaceAll(s: AppSettings) = update { s }

    fun checkPin(pin: String): Boolean = value.pinHash.isEmpty() || value.pinHash == hash(pin)
    fun setPin(pin: String) = update { it.copy(pinHash = if (pin.isEmpty()) "" else hash(pin)) }
    val hasPin: Boolean get() = value.pinHash.isNotEmpty()

    companion object {
        private const val KEY = "settings_json"
        fun hash(pin: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(("tmp-salt-" + pin).toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
