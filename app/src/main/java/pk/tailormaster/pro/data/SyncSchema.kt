package pk.tailormaster.pro.data

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * One synced table. Collection names and field names are shared with the web app,
 * so the same record looks identical in the phone, in Firestore and in the browser.
 */
class SyncTable<T : Any>(
    val coll: String,
    val table: String,
    val ser: KSerializer<T>,
    val idOf: (T) -> Long,
    val upsert: (TailorDao, T) -> Unit,
) {
    fun toJson(x: T): JsonObject = AppJson.encodeToJsonElement(ser, x).jsonObject
    fun fromJson(o: JsonObject): T = AppJson.decodeFromJsonElement(ser, o)
}

object SyncSchema {
    val customers = SyncTable("customers", "customers", Customer.serializer(), { it.id }, { d, x -> d.upsertCustomer(x) })
    val members = SyncTable("members", "family_members", FamilyMember.serializer(), { it.id }, { d, x -> d.upsertFamilyMember(x) })
    val garments = SyncTable("garments", "garments", Garment.serializer(), { it.id }, { d, x -> d.upsertGarment(x) })
    val fields = SyncTable("fields", "measurement_fields", MeasurementField.serializer(), { it.id }, { d, x -> d.upsertMeasurementField(x) })
    val profiles = SyncTable("profiles", "measurement_profiles", MeasurementProfile.serializer(), { it.id }, { d, x -> d.upsertMeasurementProfile(x) })
    val customRates = SyncTable("customRates", "custom_rates", CustomRate.serializer(), { it.id }, { d, x -> d.upsertCustomRate(x) })
    val rateHistory = SyncTable("rateHistory", "rate_history", RateHistory.serializer(), { it.id }, { d, x -> d.upsertRateHistory(x) })
    val services = SyncTable("services", "services", ExtraService.serializer(), { it.id }, { d, x -> d.upsertExtraService(x) })
    val orders = SyncTable("orders", "orders", Order.serializer(), { it.id }, { d, x -> d.upsertOrder(x) })
    val items = SyncTable("items", "order_items", OrderItem.serializer(), { it.id }, { d, x -> d.upsertOrderItem(x) })
    val statusLogs = SyncTable("statusLogs", "status_log", StatusLog.serializer(), { it.id }, { d, x -> d.upsertStatusLog(x) })
    val txns = SyncTable("txns", "txns", Txn.serializer(), { it.id }, { d, x -> d.upsertTxn(x) })
    val deliveries = SyncTable("deliveries", "deliveries", Delivery.serializer(), { it.id }, { d, x -> d.upsertDelivery(x) })
    val expenses = SyncTable("expenses", "expenses", Expense.serializer(), { it.id }, { d, x -> d.upsertExpense(x) })
    val printLogs = SyncTable("printLogs", "print_log", PrintLog.serializer(), { it.id }, { d, x -> d.upsertPrintLog(x) })
    val audits = SyncTable("audits", "audit_log", AuditLog.serializer(), { it.id }, { d, x -> d.upsertAuditLog(x) })

    val all: List<SyncTable<*>> = listOf(customers, members, garments, fields, profiles, customRates, rateHistory, services,
        orders, items, statusLogs, txns, deliveries, expenses, printLogs, audits)

    fun byColl(c: String): SyncTable<*>? = all.firstOrNull { it.coll == c }

    // ---- JSON <-> Firestore map ----
    fun toMap(o: JsonObject): Map<String, Any?> = o.mapValues { (_, v) -> plain(v) }

    private fun plain(v: JsonElement): Any? = when (v) {
        is JsonNull -> null
        is JsonPrimitive -> if (v.isString) v.content else v.booleanOrNull ?: v.longOrNull ?: v.doubleOrNull
        else -> v.toString()
    }

    fun fromMap(m: Map<String, Any?>): JsonObject = JsonObject(m.filterKeys { !it.startsWith("_") }.mapValues { (_, v) ->
        when (v) {
            null -> JsonNull
            is String -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            is Long -> JsonPrimitive(v)
            is Int -> JsonPrimitive(v.toLong())
            is Double -> if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 9e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)
            is Number -> JsonPrimitive(v.toDouble())
            else -> JsonPrimitive(v.toString())
        }
    })
}

/** A pending change made inside one database transaction. json == null means "deleted". */
data class Change(val coll: String, val id: Long, val json: JsonObject?)

/**
 * Collects the changes of the current write transaction (per thread). They are sent to the
 * cloud only after the transaction has committed, so a failed save never reaches other devices.
 */
object Changes {
    private val buffer = ThreadLocal<MutableList<Change>?>()
    fun begin() { buffer.set(mutableListOf()) }
    fun take(): List<Change> = buffer.get()?.toList() ?: emptyList()
    fun clear() { buffer.set(null) }
    fun <T : Any> put(t: SyncTable<T>, x: T) { buffer.get()?.add(Change(t.coll, t.idOf(x), t.toJson(x))) }
    fun <T : Any> putAll(t: SyncTable<T>, xs: List<T>) { xs.forEach { put(t, it) } }
    fun <T : Any> del(t: SyncTable<T>, x: T) { buffer.get()?.add(Change(t.coll, t.idOf(x), null)) }
    fun <T : Any> delAll(t: SyncTable<T>, xs: List<T>) { xs.forEach { del(t, it) } }
}

/**
 * Record ids are split into device ranges so that two devices never create the same id.
 * Device 0 (the first phone) keeps the original small numbers; device N uses N×10,000,000 + sequence.
 */
object DeviceIds {
    const val SPAN = 10_000_000L
    @Volatile var device: Int = 0

    fun next(db: SupportSQLiteDatabase, table: String): Long {
        val lo = device * SPAN
        val hi = lo + SPAN
        db.query("SELECT MAX(id) FROM $table WHERE id >= ? AND id < ?", arrayOf<Any>(lo, hi)).use { c ->
            val max = if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else lo
            return if (max < lo + 1) lo + 1 else max + 1
        }
    }

    /** Human code like C-0012 (device 0) or C-3-0012 (device 3). */
    fun code(prefix: String, id: Long, pad: Int): String =
        if (id < SPAN) prefix + "-" + id.toString().padStart(pad, '0')
        else prefix + "-" + (id / SPAN) + "-" + (id % SPAN).toString().padStart(4, '0')

    /** Parses "12", "B-00012", "B-3-0012", "3-12" back to an id. */
    fun parse(text: String): Long? {
        val t = pk.tailormaster.pro.domain.Digits.toLatin(text).trim().uppercase().replace(" ", "")
        val body = t.replace(Regex("^[A-Z]+-?"), "")
        val parts = body.split("-", "/").filter { it.isNotEmpty() }
        return when (parts.size) {
            1 -> parts[0].toLongOrNull()
            2 -> { val d = parts[0].toLongOrNull(); val s = parts[1].toLongOrNull(); if (d != null && s != null) d * SPAN + s else null }
            else -> null
        }
    }
}
