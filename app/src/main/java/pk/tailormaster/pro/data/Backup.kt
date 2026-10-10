package pk.tailormaster.pro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val app: String = "TailorMasterPro",
    val formatVersion: Int = 1,
    val dbVersion: Int = AppDatabase.VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: AppSettings = AppSettings(),
    val customers: List<Customer> = emptyList(),
    val members: List<FamilyMember> = emptyList(),
    val garments: List<Garment> = emptyList(),
    val fields: List<MeasurementField> = emptyList(),
    val profiles: List<MeasurementProfile> = emptyList(),
    val customRates: List<CustomRate> = emptyList(),
    val rateHistory: List<RateHistory> = emptyList(),
    val services: List<ExtraService> = emptyList(),
    val orders: List<Order> = emptyList(),
    val items: List<OrderItem> = emptyList(),
    val statusLogs: List<StatusLog> = emptyList(),
    val txns: List<Txn> = emptyList(),
    val deliveries: List<Delivery> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val printLogs: List<PrintLog> = emptyList(),
    val audits: List<AuditLog> = emptyList(),
    /** Shop logo image (PNG) as Base64, so the logo moves to a new phone too. */
    val logoBase64: String = "",
)

data class BackupCheck(val data: BackupData?, val errors: List<String>, val summary: List<Pair<String, Int>>) {
    val ok get() = data != null && errors.isEmpty()
}

object BackupManager {

    suspend fun export(repo: Repository, logoBase64: String = ""): String = withContext(Dispatchers.IO) {
        val dao = repo.rawDao()
        val data = repo.rawDb().runInTransaction<BackupData> {
            BackupData(
                settings = repo.settings.value,
                customers = dao.customers(), members = dao.allMembers(), garments = dao.garments(),
                fields = dao.allFields(), profiles = dao.allProfiles(), customRates = dao.allCustomRates(),
                rateHistory = dao.rateHistory(), services = dao.services(), orders = dao.orders(),
                items = dao.allItems(), statusLogs = dao.allStatusLogs(), txns = dao.txns(),
                deliveries = dao.allDeliveries(), expenses = dao.expenses(), printLogs = dao.allPrintLogs(),
                audits = dao.allAudits(),
                logoBase64 = logoBase64,
            )
        }
        AppJson.encodeToString(BackupData.serializer(), data)
    }

    fun check(text: String): BackupCheck {
        val data = try {
            AppJson.decodeFromString(BackupData.serializer(), text)
        } catch (e: Exception) {
            return BackupCheck(null, listOf("فائل درست بیک اپ نہیں ہے یا خراب ہے"), emptyList())
        }
        val errors = mutableListOf<String>()
        if (data.app != "TailorMasterPro") errors += "یہ ٹیلر ماسٹر پرو کا بیک اپ نہیں ہے"
        if (data.formatVersion > 1) errors += "یہ بیک اپ ایپ کے نئے ورژن کا ہے۔ پہلے ایپ اپ ڈیٹ کریں"
        if (data.dbVersion > AppDatabase.VERSION) errors += "یہ بیک اپ ایپ کے نئے ورژن کا ہے۔ پہلے ایپ اپ ڈیٹ کریں"

        fun <T> uniq(name: String, list: List<T>, id: (T) -> Long) {
            if (list.map(id).toSet().size != list.size) errors += "$name میں ایک جیسے نمبر ملے"
        }
        uniq("کسٹمرز", data.customers) { it.id }
        uniq("فیملی ممبرز", data.members) { it.id }
        uniq("کپڑے", data.garments) { it.id }
        uniq("ناپ پروفائلز", data.profiles) { it.id }
        uniq("آرڈرز", data.orders) { it.id }
        uniq("آرڈر آئٹمز", data.items) { it.id }
        uniq("لین دین", data.txns) { it.id }

        val cIds = data.customers.map { it.id }.toSet()
        val mIds = data.members.map { it.id }.toSet()
        val gIds = data.garments.map { it.id }.toSet()
        val oIds = data.orders.map { it.id }.toSet()
        if (data.members.any { it.customerId !in cIds }) errors += "کچھ فیملی ممبرز کا کسٹمر موجود نہیں"
        if (data.profiles.any { it.memberId !in mIds || it.customerId !in cIds || it.garmentId !in gIds }) errors += "کچھ ناپ پروفائلز کا ریکارڈ نامکمل ہے"
        if (data.fields.any { it.garmentId !in gIds }) errors += "کچھ ناپ کے خانوں کا کپڑا موجود نہیں"
        if (data.orders.any { it.customerId !in cIds }) errors += "کچھ آرڈرز کا کسٹمر موجود نہیں"
        if (data.items.any { it.orderId !in oIds }) errors += "کچھ آرڈر آئٹمز کا آرڈر موجود نہیں"
        if (data.txns.any { it.customerId !in cIds || (it.orderId != null && it.orderId !in oIds) }) errors += "کچھ لین دین کا ریکارڈ نامکمل ہے"
        if (data.deliveries.any { it.orderId !in oIds }) errors += "کچھ ڈیلیوریز کا آرڈر موجود نہیں"
        if (data.items.any { it.qty <= 0 || it.rate < 0 }) errors += "کچھ آرڈر آئٹمز میں غلط تعداد یا ریٹ"
        if (data.txns.any { it.amount < 0 }) errors += "کچھ لین دین میں منفی رقم"

        val summary = listOf(
            "کسٹمرز" to data.customers.size, "فیملی ممبرز" to data.members.size,
            "ناپ پروفائلز" to data.profiles.size, "کپڑوں کی اقسام" to data.garments.size,
            "آرڈرز" to data.orders.size, "لین دین" to data.txns.size, "اخراجات" to data.expenses.size,
        )
        return BackupCheck(data, errors, summary)
    }

    /** Replaces ALL data with the backup, inside one transaction (all-or-nothing). */
    suspend fun restore(repo: Repository, data: BackupData) = withContext(Dispatchers.IO) {
        val dao = repo.rawDao()
        repo.rawDb().runInTransaction(Runnable {
            dao.clearAudits(); dao.clearPrintLogs(); dao.clearExpenses(); dao.clearDeliveries(); dao.clearTxns()
            dao.clearStatusLogs(); dao.clearItems(); dao.clearOrders(); dao.clearServices(); dao.clearRateHistory()
            dao.clearRates(); dao.clearProfiles(); dao.clearFields(); dao.clearGarments(); dao.clearMembers(); dao.clearCustomers()
            dao.insertCustomers(data.customers); dao.insertMembers(data.members); dao.insertGarments(data.garments)
            dao.insertFields(data.fields); dao.insertProfiles(data.profiles); dao.insertRates(data.customRates)
            dao.insertRateHistories(data.rateHistory); dao.insertServices(data.services); dao.insertOrders(data.orders)
            dao.insertItems(data.items); dao.insertStatusLogs(data.statusLogs); dao.insertTxns(data.txns)
            dao.insertDeliveries(data.deliveries); dao.insertExpenses(data.expenses); dao.insertPrintLogs(data.printLogs)
            dao.insertAudits(data.audits)
            dao.insertAudit(AuditLog(entity = "backup", entityId = 0, action = "restore", detail = "exported ${data.exportedAt}"))
        })
        val keepPin = repo.settings.value.pinHash
        val keepBackup = repo.settings.value.lastBackupAt
        repo.settings.replaceAll(data.settings.copy(pinHash = keepPin, lastBackupAt = keepBackup, seeded = true))
        repo.bump()
    }
}
