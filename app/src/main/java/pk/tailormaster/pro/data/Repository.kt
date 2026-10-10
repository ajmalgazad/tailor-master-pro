package pk.tailormaster.pro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import pk.tailormaster.pro.domain.Calc
import pk.tailormaster.pro.domain.Dates

/** Thrown with an Urdu message that can be shown directly to the user. */
class UserError(message: String) : Exception(message)

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun decodeValues(json: String): List<MeasureValue> = try {
    if (json.isBlank()) emptyList() else AppJson.decodeFromString(ListSerializer(MeasureValue.serializer()), json)
} catch (e: Exception) { emptyList() }

fun encodeValues(v: List<MeasureValue>): String = AppJson.encodeToString(ListSerializer(MeasureValue.serializer()), v)

fun decodeCharges(json: String): List<Charge> = try {
    if (json.isBlank()) emptyList() else AppJson.decodeFromString(ListSerializer(Charge.serializer()), json)
} catch (e: Exception) { emptyList() }

fun encodeCharges(v: List<Charge>): String = AppJson.encodeToString(ListSerializer(Charge.serializer()), v)

fun decodeSnapshot(json: String): MeasureSnapshot? = try {
    if (json.isBlank()) null else AppJson.decodeFromString(MeasureSnapshot.serializer(), json)
} catch (e: Exception) { null }

fun encodeIds(ids: List<Long>): String = AppJson.encodeToString(ListSerializer(Long.serializer()), ids)
fun decodeIds(json: String): List<Long> = try {
    AppJson.decodeFromString(ListSerializer(Long.serializer()), json)
} catch (e: Exception) { emptyList() }

// ------------- View models (plain data returned to screens) -------------

data class CustomerRow(val customer: Customer, val balance: Long, val members: Int)

data class DraftItem(
    val key: Long = System.nanoTime(),
    val existingItemId: Long? = null,
    val garmentId: Long,
    val garmentName: String,
    val memberId: Long,
    val memberName: String,
    val qty: Int = 1,
    val rate: Long = 0,
    val profileId: Long? = null,
    val existingSnapshot: String? = null,
    val existingProfileId: Long? = null,
    val status: String = OrderStatus.NEW,
    val notes: String = "",
)

data class OrderDraft(
    val editingOrderId: Long? = null,
    val customerId: Long = 0,
    val memberId: Long = 0,
    val bookingDate: Long = System.currentTimeMillis(),
    val deliveryDate: Long = Dates.addDays(System.currentTimeMillis(), 7),
    val urgent: Boolean = false,
    val instructions: String = "",
    val internalNotes: String = "",
    val items: List<DraftItem> = emptyList(),
    val charges: List<Charge> = emptyList(),
    val discountIsPercent: Boolean = false,
    val discountInput: Double = 0.0,
    val advance: Long = 0,
    val advanceMethod: String = PayMethod.CASH,
    val advanceRef: String = "",
    val isDraft: Boolean = false,
    val rebookedFromId: Long? = null,
)

data class OrderRow(
    val order: Order,
    val customerName: String,
    val customerMobile: String,
    val memberName: String,
    val itemCount: Int,
    val pendingItems: Int,
    val paid: Long,
    val outstanding: Long,
) {
    val overdue: Boolean
        get() = !order.isDraft && order.status != OrderStatus.DELIVERED && order.status != OrderStatus.CANCELLED &&
            order.deliveryDate < Dates.today()
}

data class OrderFull(
    val order: Order,
    val customer: Customer,
    val member: FamilyMember?,
    val items: List<OrderItem>,
    val txns: List<Txn>,
    val paid: Long,
    val outstanding: Long,
    val deliveries: List<Delivery>,
    val log: List<StatusLog>,
    val customerBalance: Long,
)

data class LedgerEntry(
    val date: Long,
    val description: String,
    val debit: Long,
    val credit: Long,
    val balance: Long,
    val txn: Txn? = null,
    val orderId: Long? = null,
)

data class Ledger(val customer: Customer, val entries: List<LedgerEntry>, val balance: Long)

data class Dashboard(
    val todayBookings: Int,
    val todayDeliveries: Int,
    val openOrders: Int,
    val readyOrders: Int,
    val overdueOrders: Int,
    val todayCollection: Long,
    val monthCollection: Long,
    val totalOutstanding: Long,
    val totalCustomers: Int,
    val todayExpenses: Long,
    val alerts: List<OrderRow>,
)

class Repository(private val db: AppDatabase, val settings: SettingsStore) {
    /** Every write goes through the tracking DAO so it can be synced. */
    private val dao: TailorDao = TrackingDao(db.dao(), db)

    /** Set by the cloud sync: receives the changes of each committed transaction. */
    @Volatile var onCommitted: ((List<Change>) -> Unit)? = null

    init { DeviceIds.device = settings.value.deviceNo }

    private val _version = MutableStateFlow(0L)
    /** Increments after every write so screens know to reload. */
    val version: StateFlow<Long> = _version
    fun bump() { _version.value = _version.value + 1 }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
    private suspend fun <T> write(block: () -> T): T = withContext(Dispatchers.IO) { tx(block) }

    /** Blocking transaction with change tracking (call on a background thread). */
    internal fun <T> tx(block: () -> T): T {
        Changes.begin()
        try {
            val r = db.runInTransaction<T> { block() }
            val changes = Changes.take()
            if (changes.isNotEmpty()) onCommitted?.invoke(changes)
            bump()
            return r
        } finally {
            Changes.clear()
        }
    }

    private fun audit(entity: String, id: Long, action: String, detail: String = "") {
        dao.insertAudit(AuditLog(entity = entity, entityId = id, action = action, detail = detail))
    }

    // =============== First launch ===============
    suspend fun seedIfNeeded() = io {
        if (dao.garmentCount() == 0) tx { Seed.insertDefaults(dao) }
    }.also { bump() }

    // =============== Customers ===============
    suspend fun customer(id: Long) = io { dao.customer(id) }

    suspend fun customerRows(includeInactive: Boolean): List<CustomerRow> = io {
        val bal = balancesInternal()
        val memberCount = dao.allMembers().groupBy { it.customerId }.mapValues { it.value.count { m -> m.active } }
        dao.customers()
            .filter { it.mergedIntoId == null && (includeInactive || it.active) }
            .map { CustomerRow(it, bal[it.id] ?: it.openingBalance, memberCount[it.id] ?: 0) }
    }

    suspend fun findDuplicates(c: Customer): List<Customer> = io {
        val byMobile = if (c.mobile.isNotBlank()) dao.customersWithMobile(c.mobile.trim(), c.id) else emptyList()
        val byWa = if (c.whatsapp.isNotBlank()) dao.customersWithMobile(c.whatsapp.trim(), c.id) else emptyList()
        val byName = dao.customersWithName(c.name.trim(), c.fatherName.trim(), c.id)
        (byMobile + byWa + byName).distinctBy { it.id }
    }

    suspend fun saveCustomer(c: Customer): Long {
        if (c.name.isBlank()) throw UserError("کسٹمر کا نام لازمی ہے")
        if (c.openingBalance < 0) throw UserError("ابتدائی بیلنس منفی نہیں ہو سکتا")
        val clean = c.copy(name = c.name.trim(), mobile = c.mobile.trim(), whatsapp = c.whatsapp.trim())
        return write {
            if (clean.id == 0L) {
                val id = dao.insertCustomer(clean)
                dao.insertMember(FamilyMember(customerId = id, name = clean.name, relation = "ذاتی استعمال", isSelf = true, mobile = clean.mobile))
                audit("customer", id, "create", clean.name)
                id
            } else {
                val old = dao.customer(clean.id)
                dao.updateCustomer(clean)
                if (old != null && old.openingBalance != clean.openingBalance) {
                    audit("customer", clean.id, "opening_balance", "${old.openingBalance} -> ${clean.openingBalance}")
                }
                audit("customer", clean.id, "update", clean.name)
                clean.id
            }
        }
    }

    suspend fun setCustomerActive(id: Long, active: Boolean) = write {
        dao.customer(id)?.let { dao.updateCustomer(it.copy(active = active)) }
        audit("customer", id, if (active) "activate" else "deactivate")
    }

    /** Moves everything from [fromId] into [toId]; the old record is kept as merged (not deleted). */
    suspend fun mergeCustomers(fromId: Long, toId: Long) {
        if (fromId == toId) throw UserError("ایک ہی کسٹمر کو آپس میں ضم نہیں کیا جا سکتا")
        write {
            val from = dao.customer(fromId) ?: throw UserError("کسٹمر نہیں ملا")
            val to = dao.customer(toId) ?: throw UserError("کسٹمر نہیں ملا")
            dao.members(fromId).forEach { m ->
                if (m.isSelf) dao.updateMember(m.copy(isSelf = false, relation = "ضم شدہ: " + from.name))
            }
            dao.moveMembers(fromId, toId)
            dao.moveProfiles(fromId, toId)
            dao.moveOrders(fromId, toId)
            dao.moveTxns(fromId, toId)
            dao.moveRates(fromId, toId)
            dao.updateCustomer(to.copy(openingBalance = to.openingBalance + from.openingBalance,
                notes = (to.notes + "\nضم شدہ ریکارڈ: " + from.name + " (" + from.mobile + ")").trim()))
            dao.updateCustomer(from.copy(active = false, mergedIntoId = toId, openingBalance = 0))
            audit("customer", toId, "merge", "from $fromId (${from.name})")
        }
    }

    private fun balancesInternal(): Map<Long, Long> {
        val orders = dao.orders().filter { !it.isDraft && it.status != OrderStatus.CANCELLED }
            .groupBy { it.customerId }.mapValues { e -> e.value.sumOf { it.netAmount } }
        val txns = dao.txns().groupBy { it.customerId }.mapValues { e -> e.value.sumOf { it.signedEffect } }
        return dao.customers().associate { c ->
            c.id to Calc.customerBalance(c.openingBalance, listOf(orders[c.id] ?: 0), listOf(txns[c.id] ?: 0))
        }
    }

    suspend fun balance(customerId: Long): Long = io { balancesInternal()[customerId] ?: 0 }

    // =============== Family members ===============
    suspend fun members(customerId: Long) = io { dao.members(customerId) }

    /** Every family member together with its (non-merged) main customer. */
    suspend fun membersWithCustomers(): List<Pair<FamilyMember, Customer>> = io {
        val cs = dao.customers().filter { it.mergedIntoId == null }.associateBy { it.id }
        dao.allMembers().mapNotNull { m -> cs[m.customerId]?.let { m to it } }
    }
    suspend fun member(id: Long) = io { dao.member(id) }

    suspend fun saveMember(m: FamilyMember): Long {
        if (m.name.isBlank()) throw UserError("فرد کا نام لازمی ہے")
        return write {
            if (m.id == 0L) dao.insertMember(m.copy(name = m.name.trim())).also { audit("member", it, "create", m.name) }
            else { dao.updateMember(m); audit("member", m.id, "update", m.name); m.id }
        }
    }

    // =============== Garments, fields, services ===============
    suspend fun garments() = io { dao.garments() }
    suspend fun garment(id: Long) = io { dao.garment(id) }
    suspend fun fields(garmentId: Long) = io { dao.fields(garmentId) }
    suspend fun services() = io { dao.services() }

    suspend fun saveGarment(g: Garment): Long {
        if (g.name.isBlank()) throw UserError("کپڑے کا نام لازمی ہے")
        if (g.defaultRate < 0) throw UserError("ریٹ منفی نہیں ہو سکتا")
        return write {
            if (g.id == 0L) {
                val sort = (dao.garments().maxOfOrNull { it.sortOrder } ?: 0) + 1
                val id = dao.insertGarment(g.copy(sortOrder = sort))
                dao.insertRateHistory(RateHistory(garmentId = id, oldRate = 0, newRate = g.defaultRate, note = "نیا کپڑا"))
                id
            } else {
                val old = dao.garment(g.id)
                dao.updateGarment(g)
                if (old != null && old.defaultRate != g.defaultRate) {
                    dao.insertRateHistory(RateHistory(garmentId = g.id, oldRate = old.defaultRate, newRate = g.defaultRate, note = "ڈیفالٹ ریٹ"))
                }
                g.id
            }
        }
    }

    suspend fun moveGarment(id: Long, up: Boolean) = write {
        val list = dao.garments().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i >= 0 && j in list.indices) {
            val a = list[i]; val b = list[j]
            list[i] = b; list[j] = a
            list.forEachIndexed { idx, g -> if (g.sortOrder != idx) dao.updateGarment(g.copy(sortOrder = idx)) }
        }
    }

    suspend fun saveField(f: MeasurementField): Long {
        if (f.label.isBlank()) throw UserError("ناپ کا نام لازمی ہے")
        return write {
            if (f.id == 0L) {
                val sort = (dao.fields(f.garmentId).maxOfOrNull { it.sortOrder } ?: 0) + 1
                dao.insertField(f.copy(sortOrder = sort))
            } else { dao.updateField(f); f.id }
        }
    }

    suspend fun moveField(garmentId: Long, id: Long, up: Boolean) = write {
        val list = dao.fields(garmentId).toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i >= 0 && j in list.indices) {
            val a = list[i]; val b = list[j]
            list[i] = b; list[j] = a
            list.forEachIndexed { idx, f -> if (f.sortOrder != idx) dao.updateField(f.copy(sortOrder = idx)) }
        }
    }

    suspend fun saveService(s: ExtraService): Long {
        if (s.name.isBlank()) throw UserError("سروس کا نام لازمی ہے")
        if (s.amount < 0) throw UserError("رقم منفی نہیں ہو سکتی")
        return write { if (s.id == 0L) dao.insertService(s) else { dao.updateService(s); s.id } }
    }

    // =============== Rates ===============
    suspend fun customRates() = io { dao.customRates() }
    suspend fun rateHistory() = io { dao.rateHistory() }

    /** Member-specific rate > customer-specific rate > garment default rate. */
    suspend fun resolveRate(garmentId: Long, customerId: Long, memberId: Long): Long = io {
        val rates = dao.customRates().filter { it.active && it.garmentId == garmentId }
        rates.firstOrNull { it.memberId == memberId && memberId != 0L }?.rate
            ?: rates.firstOrNull { it.customerId == customerId && it.memberId == null && customerId != 0L }?.rate
            ?: dao.garment(garmentId)?.defaultRate ?: 0
    }

    suspend fun saveCustomRate(r: CustomRate): Long {
        if (r.rate < 0) throw UserError("ریٹ منفی نہیں ہو سکتا")
        if (r.customerId == null && r.memberId == null) throw UserError("کسٹمر منتخب کریں")
        return write {
            if (r.id == 0L) {
                val id = dao.insertRate(r.copy(updatedAt = System.currentTimeMillis()))
                dao.insertRateHistory(RateHistory(garmentId = r.garmentId, customerId = r.customerId, memberId = r.memberId, oldRate = 0, newRate = r.rate, note = "خصوصی ریٹ"))
                id
            } else {
                val old = dao.customRate(r.id)
                dao.updateRate(r.copy(updatedAt = System.currentTimeMillis()))
                if (old != null && (old.rate != r.rate || old.active != r.active)) {
                    dao.insertRateHistory(RateHistory(garmentId = r.garmentId, customerId = r.customerId, memberId = r.memberId,
                        oldRate = old.rate, newRate = r.rate, note = if (r.active) "خصوصی ریٹ" else "خصوصی ریٹ غیر فعال"))
                }
                r.id
            }
        }
    }

    // =============== Measurement profiles ===============
    suspend fun profilesForMember(memberId: Long) = io { dao.profilesForMember(memberId) }
    suspend fun profilesForCustomer(customerId: Long) = io { dao.profilesForCustomer(customerId) }
    suspend fun allProfiles() = io { dao.allProfiles() }
    suspend fun profile(id: Long) = io { dao.profile(id) }

    private fun checkRequired(p: MeasurementProfile) {
        val fields = dao.fields(p.garmentId).filter { it.active && it.required }
        val vals = decodeValues(p.valuesJson)
        val missing = fields.filter { f -> vals.none { it.fieldId == f.id && it.value.isNotBlank() } }
        if (missing.isNotEmpty()) throw UserError("یہ ناپ لازمی ہیں: " + missing.joinToString("، ") { it.label })
    }

    suspend fun saveNewProfile(p: MeasurementProfile): Long {
        if (p.name.isBlank()) throw UserError("پروفائل کا نام لازمی ہے")
        return write {
            checkRequired(p)
            val hasDefault = dao.profilesForMember(p.memberId).any { it.garmentId == p.garmentId && it.isDefault && !it.archived }
            val makeDefault = p.isDefault || !hasDefault
            if (makeDefault) dao.clearDefault(p.memberId, p.garmentId)
            val id = dao.insertProfile(p.copy(id = 0, isDefault = makeDefault, archived = false))
            audit("profile", id, "create", p.name)
            id
        }
    }

    /** Saves changed measurements as a NEW version and archives the old one. Old orders are untouched. */
    suspend fun saveProfileVersion(old: MeasurementProfile, updated: MeasurementProfile): Long {
        if (updated.name.isBlank()) throw UserError("پروفائل کا نام لازمی ہے")
        return write {
            checkRequired(updated)
            dao.updateProfile(old.copy(archived = true, isDefault = false))
            val id = dao.insertProfile(updated.copy(id = 0, previousId = old.id, archived = false,
                isDefault = old.isDefault || updated.isDefault, date = System.currentTimeMillis()))
            audit("profile", id, "new_version", "previous ${old.id}")
            id
        }
    }

    suspend fun duplicateProfile(p: MeasurementProfile, newName: String, memberId: Long = p.memberId): Long = write {
        val id = dao.insertProfile(p.copy(id = 0, name = newName, memberId = memberId, isDefault = false,
            archived = false, previousId = null, date = System.currentTimeMillis(), lastUsed = null))
        audit("profile", id, "duplicate", "from ${p.id}")
        id
    }

    suspend fun setDefaultProfile(p: MeasurementProfile) = write {
        dao.clearDefault(p.memberId, p.garmentId)
        dao.updateProfile(p.copy(isDefault = true))
    }

    suspend fun archiveProfile(p: MeasurementProfile, archived: Boolean) = write {
        dao.updateProfile(p.copy(archived = archived, isDefault = if (archived) false else p.isDefault))
    }

    suspend fun profileHistory(p: MeasurementProfile): List<MeasurementProfile> = io {
        val out = mutableListOf<MeasurementProfile>()
        var prev = p.previousId
        var guard = 0
        while (prev != null && guard++ < 200) {
            val x = dao.profile(prev) ?: break
            out += x
            prev = x.previousId
        }
        out
    }

    // =============== Orders ===============
    suspend fun buildDraftFromOrder(orderId: Long, forRebook: Boolean): OrderDraft = io {
        val o = dao.order(orderId) ?: throw UserError("آرڈر نہیں ملا")
        val items = dao.items(orderId)
        OrderDraft(
            editingOrderId = if (forRebook) null else o.id,
            customerId = o.customerId, memberId = o.memberId,
            bookingDate = if (forRebook) System.currentTimeMillis() else o.bookingDate,
            deliveryDate = if (forRebook) Dates.addDays(System.currentTimeMillis(), 7) else o.deliveryDate,
            urgent = o.urgent, instructions = o.instructions, internalNotes = if (forRebook) "" else o.internalNotes,
            items = items.map {
                DraftItem(existingItemId = if (forRebook) null else it.id, garmentId = it.garmentId, garmentName = it.garmentName,
                    memberId = it.memberId, memberName = it.memberName, qty = it.qty, rate = it.rate,
                    profileId = it.profileId,
                    existingSnapshot = if (forRebook) null else it.measurementJson,
                    existingProfileId = if (forRebook) null else it.profileId,
                    status = if (forRebook) OrderStatus.NEW else it.status, notes = it.notes)
            },
            charges = decodeCharges(o.chargesJson),
            discountIsPercent = o.discountType == DiscountType.PERCENT,
            discountInput = o.discountInput,
            isDraft = if (forRebook) false else o.isDraft,
            rebookedFromId = if (forRebook) o.id else o.rebookedFromId,
        )
    }

    private fun snapshotFor(profileId: Long?): String {
        if (profileId == null) return ""
        val p = dao.profile(profileId) ?: return ""
        return AppJson.encodeToString(MeasureSnapshot.serializer(), MeasureSnapshot(
            profileId = p.id, profileName = p.name, unit = p.unit, date = p.date,
            fitting = p.fitting, instructions = p.instructions, values = decodeValues(p.valuesJson)))
    }

    fun totalsOf(d: OrderDraft): Calc.Totals = Calc.totals(
        d.items.map { Calc.Line(it.qty, it.rate) }, d.charges.map { it.amount }, d.discountIsPercent, d.discountInput)

    /** Creates or edits an order. Returns the order id. */
    suspend fun saveOrder(d: OrderDraft): Long {
        if (d.customerId == 0L) throw UserError("کسٹمر منتخب کریں")
        if (d.memberId == 0L) throw UserError("فیملی ممبر منتخب کریں")
        Calc.validate(d.items.map { Calc.Line(it.qty, it.rate) }, d.charges.map { it.amount }, d.discountIsPercent, d.discountInput)
            ?.let { throw UserError(it) }
        if (d.advance < 0) throw UserError("ایڈوانس منفی نہیں ہو سکتا")
        if (d.deliveryDate < Dates.startOfDay(d.bookingDate)) throw UserError("ڈیلیوری کی تاریخ بکنگ سے پہلے نہیں ہو سکتی")
        val t = totalsOf(d)
        if (d.advance > t.net) throw UserError("ایڈوانس کل رقم سے زیادہ ہے")
        val now = System.currentTimeMillis()
        return write {
            val orderId: Long
            if (d.editingOrderId == null) {
                orderId = dao.insertOrder(Order(
                    customerId = d.customerId, memberId = d.memberId, bookingDate = d.bookingDate,
                    deliveryDate = d.deliveryDate, urgent = d.urgent, instructions = d.instructions,
                    internalNotes = d.internalNotes, status = OrderStatus.NEW, isDraft = d.isDraft,
                    chargesJson = encodeCharges(d.charges),
                    discountType = if (d.discountIsPercent) DiscountType.PERCENT else DiscountType.AMOUNT,
                    discountInput = d.discountInput, itemsTotal = t.itemsTotal, chargesTotal = t.chargesTotal,
                    discountAmount = t.discount, netAmount = t.net, createdAt = now, updatedAt = now,
                    rebookedFromId = d.rebookedFromId))
                dao.insertStatusLog(StatusLog(orderId = orderId, fromStatus = "", toStatus = OrderStatus.NEW,
                    note = if (d.isDraft) "ڈرافٹ بنایا" else if (d.rebookedFromId != null) "دوبارہ بکنگ" else "بکنگ"))
                audit("order", orderId, "create", "net ${t.net}")
            } else {
                val old = dao.order(d.editingOrderId) ?: throw UserError("آرڈر نہیں ملا")
                if (old.status == OrderStatus.CANCELLED) throw UserError("منسوخ آرڈر میں ترمیم نہیں ہو سکتی")
                val oldItems = dao.items(old.id)
                if (oldItems.any { it.delivered }) throw UserError("جس آرڈر کے کپڑے ڈیلیور ہو چکے ہوں اس میں ترمیم نہیں ہو سکتی")
                if (!d.isDraft) {
                    val paid = -dao.txnsForOrder(old.id).sumOf { it.signedEffect }
                    if (t.net < paid) throw UserError("نئی کل رقم وصول شدہ رقم سے کم ہے۔ پہلے ریفنڈ درج کریں")
                }
                orderId = old.id
                dao.updateOrder(old.copy(
                    customerId = d.customerId, memberId = d.memberId, bookingDate = d.bookingDate,
                    deliveryDate = d.deliveryDate, urgent = d.urgent, instructions = d.instructions,
                    internalNotes = d.internalNotes, isDraft = d.isDraft, chargesJson = encodeCharges(d.charges),
                    discountType = if (d.discountIsPercent) DiscountType.PERCENT else DiscountType.AMOUNT,
                    discountInput = d.discountInput, itemsTotal = t.itemsTotal, chargesTotal = t.chargesTotal,
                    discountAmount = t.discount, netAmount = t.net, updatedAt = now))
                dao.deleteItems(old.id)
                dao.insertStatusLog(StatusLog(orderId = orderId, fromStatus = old.status, toStatus = old.status,
                    note = "ترمیم: پرانی رقم ${old.netAmount / 100} → نئی رقم ${t.net / 100}" +
                        if (old.isDraft && !d.isDraft) " (ڈرافٹ کنفرم)" else ""))
                audit("order", orderId, "edit", "old net ${old.netAmount}, new net ${t.net}")
            }
            for (it in d.items) {
                val keepSnapshot = it.existingSnapshot != null && it.existingProfileId == it.profileId
                val snap = if (keepSnapshot) it.existingSnapshot!! else snapshotFor(it.profileId)
                dao.insertItem(OrderItem(orderId = orderId, garmentId = it.garmentId, garmentName = it.garmentName,
                    memberId = it.memberId, memberName = it.memberName, qty = it.qty, rate = it.rate,
                    total = Calc.lineTotal(it.qty, it.rate), profileId = it.profileId, measurementJson = snap,
                    status = it.status, notes = it.notes))
                it.profileId?.let { pid -> dao.touchProfile(pid, now) }
            }
            if (d.advance > 0) {
                val tid = dao.insertTxn(Txn(customerId = d.customerId, orderId = orderId, date = now, type = TxnType.PAYMENT,
                    amount = d.advance, signedEffect = TxnType.effect(TxnType.PAYMENT, d.advance), cash = true,
                    method = d.advanceMethod, reference = d.advanceRef, notes = "ایڈوانس"))
                audit("txn", tid, "create", "advance ${d.advance}")
            }
            orderId
        }
    }

    suspend fun setOrderStatus(orderId: Long, status: String, applyToItems: Boolean, note: String = "") = write {
        val o = dao.order(orderId) ?: throw UserError("آرڈر نہیں ملا")
        if (o.isDraft) throw UserError("پہلے ڈرافٹ کو کنفرم کریں")
        if (o.status == OrderStatus.CANCELLED) throw UserError("منسوخ آرڈر کا اسٹیٹس تبدیل نہیں ہو سکتا")
        if (status == OrderStatus.DELIVERED) throw UserError("ڈیلیوری کے لیے ڈیلیوری اسکرین استعمال کریں")
        if (status == OrderStatus.CANCELLED) throw UserError("منسوخی کے لیے منسوخ بٹن استعمال کریں")
        dao.updateOrder(o.copy(status = status, updatedAt = System.currentTimeMillis()))
        dao.insertStatusLog(StatusLog(orderId = orderId, fromStatus = o.status, toStatus = status, note = note))
        if (applyToItems) {
            dao.items(orderId).filter { !it.delivered }.forEach {
                if (it.status != status) {
                    dao.updateItem(it.copy(status = status))
                    dao.insertStatusLog(StatusLog(orderId = orderId, itemId = it.id, fromStatus = it.status, toStatus = status))
                }
            }
        }
    }

    suspend fun setItemStatus(itemId: Long, status: String) = write {
        val it = dao.item(itemId) ?: throw UserError("آئٹم نہیں ملا")
        if (it.delivered) throw UserError("یہ کپڑا ڈیلیور ہو چکا ہے")
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED) throw UserError("یہ اسٹیٹس یہاں سے نہیں لگ سکتا")
        dao.updateItem(it.copy(status = status))
        dao.insertStatusLog(StatusLog(orderId = it.orderId, itemId = it.id, fromStatus = it.status, toStatus = status))
        // When every pending item reaches the same stage, move the order with it.
        val o = dao.order(it.orderId)
        val pending = dao.items(it.orderId).filter { x -> !x.delivered }
        if (o != null && !o.isDraft && o.status != OrderStatus.CANCELLED && pending.isNotEmpty() && pending.all { x -> x.status == status } && o.status != status) {
            dao.updateOrder(o.copy(status = status, updatedAt = System.currentTimeMillis()))
            dao.insertStatusLog(StatusLog(orderId = o.id, fromStatus = o.status, toStatus = status, note = "تمام کپڑے"))
        }
    }

    suspend fun saveInternalNotes(orderId: Long, notes: String) = write {
        dao.order(orderId)?.let { dao.updateOrder(it.copy(internalNotes = notes, updatedAt = System.currentTimeMillis())) }
    }

    suspend fun cancelOrder(orderId: Long, reason: String) = write {
        val o = dao.order(orderId) ?: throw UserError("آرڈر نہیں ملا")
        if (o.status == OrderStatus.CANCELLED) throw UserError("آرڈر پہلے ہی منسوخ ہے")
        if (dao.items(orderId).any { it.delivered }) throw UserError("ڈیلیور شدہ آرڈر منسوخ نہیں ہو سکتا")
        dao.updateOrder(o.copy(status = OrderStatus.CANCELLED, updatedAt = System.currentTimeMillis()))
        dao.insertStatusLog(StatusLog(orderId = orderId, fromStatus = o.status, toStatus = OrderStatus.CANCELLED, note = reason))
        audit("order", orderId, "cancel", reason)
    }

    suspend fun order(id: Long) = io { dao.order(id) }

    suspend fun orderFull(orderId: Long): OrderFull = io {
        val o = dao.order(orderId) ?: throw UserError("آرڈر نہیں ملا")
        val c = dao.customer(o.customerId) ?: throw UserError("کسٹمر نہیں ملا")
        val txns = dao.txnsForOrder(orderId)
        val paid = -txns.sumOf { it.signedEffect }
        val net = if (o.status == OrderStatus.CANCELLED || o.isDraft) 0 else o.netAmount
        OrderFull(o, c, dao.member(o.memberId), dao.items(orderId), txns, paid, Calc.outstanding(net, paid),
            dao.deliveries(orderId), dao.statusLog(orderId), balancesInternal()[c.id] ?: 0)
    }

    suspend fun orderRows(): List<OrderRow> = io { orderRowsInternal() }

    private fun orderRowsInternal(): List<OrderRow> {
        val customers = dao.customers().associateBy { it.id }
        val members = dao.allMembers().associateBy { it.id }
        val items = dao.allItems().groupBy { it.orderId }
        val paidBy = dao.txns().filter { it.orderId != null }.groupBy { it.orderId!! }
            .mapValues { e -> -e.value.sumOf { it.signedEffect } }
        return dao.orders().map { o ->
            val its = items[o.id] ?: emptyList()
            val paid = paidBy[o.id] ?: 0
            val net = if (o.status == OrderStatus.CANCELLED || o.isDraft) 0 else o.netAmount
            val c = customers[o.customerId]
            OrderRow(o, c?.name ?: "-", c?.mobile ?: "", members[o.memberId]?.name ?: "-",
                its.sumOf { it.qty }, its.filter { !it.delivered }.sumOf { it.qty }, paid, net - paid)
        }
    }

    // =============== Delivery ===============
    suspend fun deliver(orderId: Long, itemIds: List<Long>, notes: String, payment: Long, method: String,
                        forceCompleteRemaining: Boolean): Long {
        if (payment < 0) throw UserError("رقم منفی نہیں ہو سکتی")
        return write {
            val o = dao.order(orderId) ?: throw UserError("آرڈر نہیں ملا")
            if (o.isDraft) throw UserError("ڈرافٹ آرڈر ڈیلیور نہیں ہو سکتا")
            if (o.status == OrderStatus.CANCELLED) throw UserError("منسوخ آرڈر ڈیلیور نہیں ہو سکتا")
            val items = dao.items(orderId)
            val selected = items.filter { it.id in itemIds && !it.delivered }
            val remainingAfter = items.filter { !it.delivered && it.id !in itemIds }
            if (selected.isEmpty() && !(forceCompleteRemaining && remainingAfter.isNotEmpty())) throw UserError("ڈیلیور کرنے کے لیے کپڑے منتخب کریں")
            val now = System.currentTimeMillis()
            val txnId = if (payment > 0) {
                val paid = -dao.txnsForOrder(orderId).sumOf { it.signedEffect }
                if (payment > o.netAmount - paid) throw UserError("وصولی بقایا رقم سے زیادہ ہے")
                dao.insertTxn(Txn(customerId = o.customerId, orderId = orderId, date = now, type = TxnType.PAYMENT,
                    amount = payment, signedEffect = TxnType.effect(TxnType.PAYMENT, payment), cash = true,
                    method = method, notes = "ڈیلیوری پر وصولی"))
            } else null
            val toMark = if (forceCompleteRemaining) selected + remainingAfter else selected
            val deliveryId = dao.insertDelivery(Delivery(orderId = orderId, date = now,
                itemIdsJson = encodeIds(toMark.map { it.id }), notes = notes, paymentTxnId = txnId,
                forcedComplete = forceCompleteRemaining && remainingAfter.isNotEmpty()))
            toMark.forEach {
                dao.updateItem(it.copy(delivered = true, deliveredAt = now, deliveryId = deliveryId, status = OrderStatus.DELIVERED))
                dao.insertStatusLog(StatusLog(orderId = orderId, itemId = it.id, fromStatus = it.status, toStatus = OrderStatus.DELIVERED,
                    note = if (it in remainingAfter) "مالک کی تصدیق سے مکمل" else ""))
            }
            val allDone = dao.items(orderId).all { it.delivered }
            if (allDone && o.status != OrderStatus.DELIVERED) {
                dao.updateOrder(o.copy(status = OrderStatus.DELIVERED, updatedAt = now))
                dao.insertStatusLog(StatusLog(orderId = orderId, fromStatus = o.status, toStatus = OrderStatus.DELIVERED,
                    note = if (forceCompleteRemaining) "مالک کی تصدیق سے مکمل ڈیلیوری" else "مکمل ڈیلیوری"))
            } else if (!allDone) {
                dao.insertStatusLog(StatusLog(orderId = orderId, fromStatus = o.status, toStatus = o.status, note = "جزوی ڈیلیوری"))
            }
            audit("delivery", deliveryId, "create", "order $orderId items ${toMark.size}")
            deliveryId
        }
    }

    suspend fun delivery(id: Long) = io { dao.delivery(id) }
    suspend fun allDeliveries() = io { dao.allDeliveries() }

    // =============== Payments & ledger ===============
    suspend fun txn(id: Long) = io { dao.txn(id) }
    suspend fun allTxns() = io { dao.txns() }

    suspend fun recordTxn(customerId: Long, orderId: Long?, type: String, amount: Long, method: String,
                          reference: String, notes: String, date: Long): Long {
        if (amount <= 0) throw UserError("رقم صفر سے زیادہ ہونی چاہیے")
        if (type == TxnType.REVERSAL) throw UserError("غلط قسم")
        return write {
            dao.customer(customerId) ?: throw UserError("کسٹمر نہیں ملا")
            if (orderId != null) {
                val o = dao.order(orderId) ?: throw UserError("آرڈر نہیں ملا")
                if (o.customerId != customerId) throw UserError("یہ آرڈر اس کسٹمر کا نہیں")
            }
            val id = dao.insertTxn(Txn(customerId = customerId, orderId = orderId, date = date, type = type, amount = amount,
                signedEffect = TxnType.effect(type, amount), cash = TxnType.isCash(type), method = method,
                reference = reference, notes = notes))
            audit("txn", id, "create", "$type $amount")
            id
        }
    }

    /** Never deletes: creates an opposite entry and links both. */
    suspend fun reverseTxn(txnId: Long, reason: String): Long = write {
        val t = dao.txn(txnId) ?: throw UserError("اندراج نہیں ملا")
        if (t.type == TxnType.REVERSAL) throw UserError("ریورسل کو دوبارہ ریورس نہیں کیا جا سکتا")
        if (t.reversedById != null) throw UserError("یہ اندراج پہلے ہی منسوخ ہو چکا ہے")
        val id = dao.insertTxn(Txn(customerId = t.customerId, orderId = t.orderId, date = System.currentTimeMillis(),
            type = TxnType.REVERSAL, amount = t.amount, signedEffect = -t.signedEffect, cash = t.cash,
            method = t.method, reference = Fmt2.txnNo(t.id), notes = reason, reversesId = t.id))
        dao.updateTxn(t.copy(reversedById = id))
        audit("txn", t.id, "reverse", reason)
        id
    }

    suspend fun ledger(customerId: Long): Ledger = io { ledgerInternal(customerId) }

    private fun ledgerInternal(customerId: Long): Ledger {
        val c = dao.customer(customerId) ?: throw UserError("کسٹمر نہیں ملا")
        data class Raw(val date: Long, val order: Int, val desc: String, val effect: Long, val txn: Txn?, val orderId: Long?)
        val raws = mutableListOf<Raw>()
        dao.ordersForCustomer(customerId).filter { !it.isDraft }.forEach { o ->
            val cancelled = o.status == OrderStatus.CANCELLED
            val disc = if (o.discountAmount > 0) " (رعایت ${o.discountAmount / 100})" else ""
            raws += Raw(o.bookingDate, 1, "بکنگ ${Fmt2.orderNo(o.id)}" + disc + if (cancelled) " — منسوخ" else "",
                if (cancelled) 0 else o.netAmount, null, o.id)
        }
        dao.txnsForCustomer(customerId).forEach { t ->
            val label = TxnType.label(t.type) + " ${Fmt2.txnNo(t.id)}" +
                (t.orderId?.let { " / ${Fmt2.orderNo(it)}" } ?: "") +
                (if (t.reversedById != null) " (منسوخ شدہ)" else "") +
                (if (t.notes.isNotBlank()) " — ${t.notes}" else "")
            raws += Raw(t.date, 2, label, t.signedEffect, t, t.orderId)
        }
        var bal = c.openingBalance
        val entries = mutableListOf(LedgerEntry(c.regDate, "ابتدائی بیلنس", if (c.openingBalance > 0) c.openingBalance else 0,
            if (c.openingBalance < 0) -c.openingBalance else 0, bal))
        raws.sortedWith(compareBy({ it.date }, { it.order })).forEach { r ->
            bal += r.effect
            entries += LedgerEntry(r.date, r.desc, if (r.effect > 0) r.effect else 0, if (r.effect < 0) -r.effect else 0, bal, r.txn, r.orderId)
        }
        return Ledger(c, entries, bal)
    }

    /** Customer balance right after the given transaction (for payment receipts). */
    suspend fun balanceAfterTxn(txnId: Long): Long = io {
        val t = dao.txn(txnId) ?: throw UserError("اندراج نہیں ملا")
        val l = ledgerInternal(t.customerId)
        l.entries.lastOrNull { it.txn?.id == txnId }?.balance ?: l.balance
    }

    // =============== Expenses ===============
    suspend fun expenses() = io { dao.expenses() }

    suspend fun saveExpense(e: Expense): Long {
        if (e.amount <= 0) throw UserError("رقم صفر سے زیادہ ہونی چاہیے")
        if (e.category.isBlank()) throw UserError("قسم منتخب کریں")
        return write {
            if (e.id == 0L) dao.insertExpense(e).also { audit("expense", it, "create", "${e.category} ${e.amount}") }
            else { dao.updateExpense(e); audit("expense", e.id, "update", "${e.category} ${e.amount}"); e.id }
        }
    }

    suspend fun voidExpense(e: Expense, reason: String) = write {
        dao.updateExpense(e.copy(voided = true, notes = (e.notes + " [منسوخ: $reason]").trim()))
        audit("expense", e.id, "void", reason)
    }

    // =============== Dashboard ===============
    suspend fun dashboard(day: Long): Dashboard = io {
        val from = Dates.startOfDay(day); val to = Dates.endOfDay(day)
        val mFrom = Dates.startOfMonth(day); val mTo = Dates.endOfMonth(day)
        val rows = orderRowsInternal().filter { !it.order.isDraft }
        val txns = dao.txns().filter { it.cash }
        val bal = balancesInternal()
        val activeCustomers = dao.customers().filter { it.mergedIntoId == null && it.active }
        val dueLimit = Dates.endOfDay(Dates.addDays(day, settings.value.dueSoonDays))
        val alerts = rows.filter { r ->
            r.order.status != OrderStatus.DELIVERED && r.order.status != OrderStatus.CANCELLED && r.order.deliveryDate <= dueLimit
        }.sortedBy { it.order.deliveryDate }
        Dashboard(
            todayBookings = rows.count { it.order.bookingDate in from..to },
            todayDeliveries = rows.count { it.order.deliveryDate in from..to && it.order.status != OrderStatus.CANCELLED },
            openOrders = rows.count { it.order.status in OrderStatus.open },
            readyOrders = rows.count { it.order.status == OrderStatus.READY },
            overdueOrders = rows.count { r -> r.order.status != OrderStatus.DELIVERED && r.order.status != OrderStatus.CANCELLED && r.order.deliveryDate < from },
            todayCollection = -txns.filter { it.date in from..to }.sumOf { it.signedEffect },
            monthCollection = -txns.filter { it.date in mFrom..mTo }.sumOf { it.signedEffect },
            totalOutstanding = bal.filter { e -> activeCustomers.any { it.id == e.key } && e.value > 0 }.values.sum(),
            totalCustomers = activeCustomers.size,
            todayExpenses = dao.expenses().filter { !it.voided && it.date in from..to }.sumOf { it.amount },
            alerts = alerts,
        )
    }

    // =============== Print & audit logs ===============
    suspend fun logPrint(kind: String, refId: Long, title: String, status: String, message: String) = write {
        dao.insertPrintLog(PrintLog(kind = kind, refId = refId, title = title, status = status, message = message))
    }

    suspend fun printLogs(limit: Int = 200) = io { dao.printLogs(limit) }
    suspend fun audits(limit: Int = 300) = io { dao.audits(limit) }

    // Exposed for reports/backup helpers in the same module
    internal fun rawDao() = dao
    internal fun rawDb() = db
    internal fun balancesSync() = balancesInternal()
    internal fun orderRowsSync() = orderRowsInternal()
    internal fun ledgerSync(customerId: Long) = ledgerInternal(customerId)
}

/** Number formats without depending on UI settings (used inside stored text). */
object Fmt2 {
    fun orderNo(id: Long) = DeviceIds.code("B", id, 5)
    fun txnNo(id: Long) = DeviceIds.code("T", id, 5)
}
