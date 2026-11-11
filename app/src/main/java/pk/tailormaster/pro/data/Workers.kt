package pk.tailormaster.pro.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.serialization.Serializable
import pk.tailormaster.pro.data.SyncSchema as S

/*
 * Kaarigar (workers): the shop gives garments to its workers to cut / stitch / embroider,
 * pays them per piece, and gives advances. Money in PAISA, dates in epoch ms, like everywhere.
 *
 * Worker balance = wages of finished work − payments and advances (not cancelled).
 * Positive = the shop owes the worker. Negative = the worker has taken more advance than earned.
 */

@Serializable
@Entity(tableName = "workers", indices = [Index("name")])
data class Worker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mobile: String = "",
    val cnic: String = "",
    /** What the worker makes, e.g. "شلوار قمیض، واسکٹ" (free text). */
    val skills: String = "",
    val address: String = "",
    val notes: String = "",
    val active: Boolean = true,
    val sample: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

object JobStatus {
    const val ASSIGNED = "ASSIGNED"
    const val DONE = "DONE"
    const val CANCELLED = "CANCELLED"
    fun label(s: String) = when (s) {
        ASSIGNED -> "کاریگر کے پاس"
        DONE -> "مکمل (واپس آ گیا)"
        CANCELLED -> "منسوخ"
        else -> s
    }
}

object WorkTasks {
    val defaults = listOf("سلائی", "کٹنگ", "کڑھائی", "کاج بٹن", "فنشنگ / استری", "آلٹریشن")
}

@Serializable
@Entity(tableName = "work_jobs", indices = [Index("workerId"), Index("orderId"), Index("itemId")])
data class WorkJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workerId: Long,
    val orderId: Long,
    val itemId: Long,
    val garmentName: String,
    val memberName: String = "",
    val task: String = "سلائی",
    val qty: Int = 1,
    /** Wage for ONE piece. */
    val wage: Long = 0,
    /** qty × wage. */
    val amount: Long = 0,
    val assignedAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val status: String = JobStatus.ASSIGNED,
    val doneAt: Long? = null,
    val notes: String = "",
)

object WorkerPayType {
    const val PAYMENT = "PAYMENT"
    const val ADVANCE = "ADVANCE"
    val all = listOf(PAYMENT, ADVANCE)
    fun label(t: String) = when (t) {
        PAYMENT -> "مزدوری کی ادائیگی"
        ADVANCE -> "ایڈوانس"
        else -> t
    }
}

@Serializable
@Entity(tableName = "worker_payments", indices = [Index("workerId"), Index("date")])
data class WorkerPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workerId: Long,
    val date: Long = System.currentTimeMillis(),
    val type: String = WorkerPayType.PAYMENT,
    val amount: Long,
    val method: String = PayMethod.CASH,
    val notes: String = "",
    /** Cancelled entries are kept (never deleted) and no longer count. */
    val voided: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Pure calculations (also used by unit tests). */
object WorkerCalc {
    fun earned(jobs: List<WorkJob>) = jobs.filter { it.status == JobStatus.DONE }.sumOf { it.amount }
    fun pendingAmount(jobs: List<WorkJob>) = jobs.filter { it.status == JobStatus.ASSIGNED }.sumOf { it.amount }
    fun paid(pays: List<WorkerPayment>) = pays.filter { !it.voided }.sumOf { it.amount }
    fun balance(jobs: List<WorkJob>, pays: List<WorkerPayment>) = earned(jobs) - paid(pays)
}

@Dao
interface WorkerDao {
    @Insert fun insertWorker(w: Worker): Long
    @Update fun updateWorker(w: Worker)
    @Query("SELECT * FROM workers WHERE id = :id") fun worker(id: Long): Worker?
    @Query("SELECT * FROM workers ORDER BY name COLLATE NOCASE") fun workers(): List<Worker>

    @Insert fun insertJob(j: WorkJob): Long
    @Update fun updateJob(j: WorkJob)
    @Query("SELECT * FROM work_jobs WHERE id = :id") fun job(id: Long): WorkJob?
    @Query("SELECT * FROM work_jobs ORDER BY assignedAt DESC, id DESC") fun jobs(): List<WorkJob>
    @Query("SELECT * FROM work_jobs WHERE workerId = :workerId ORDER BY assignedAt DESC, id DESC") fun jobsForWorker(workerId: Long): List<WorkJob>
    @Query("SELECT * FROM work_jobs WHERE orderId = :orderId ORDER BY id") fun jobsForOrder(orderId: Long): List<WorkJob>

    @Insert fun insertPayment(p: WorkerPayment): Long
    @Update fun updatePayment(p: WorkerPayment)
    @Query("SELECT * FROM worker_payments ORDER BY date DESC, id DESC") fun payments(): List<WorkerPayment>
    @Query("SELECT * FROM worker_payments WHERE workerId = :workerId ORDER BY date, id") fun paymentsForWorker(workerId: Long): List<WorkerPayment>
    @Query("SELECT * FROM worker_payments WHERE id = :id") fun payment(id: Long): WorkerPayment?

    // backup / restore / sync
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertWorkers(l: List<Worker>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertJobs(l: List<WorkJob>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertPayments(l: List<WorkerPayment>)
    @Query("DELETE FROM workers") fun clearWorkers()
    @Query("DELETE FROM work_jobs") fun clearJobs()
    @Query("DELETE FROM worker_payments") fun clearPayments()
    @Upsert fun upsertWorker(x: Worker)
    @Upsert fun upsertJob(x: WorkJob)
    @Upsert fun upsertPayment(x: WorkerPayment)

    // sample data
    @Query("SELECT * FROM workers WHERE sample = 1") fun sampleWorkers(): List<Worker>
    @Query("DELETE FROM workers WHERE id IN (:ids)") fun deleteWorkers(ids: List<Long>)
    @Query("SELECT * FROM work_jobs WHERE workerId IN (:workerIds) OR orderId IN (:orderIds)") fun jobsOf(workerIds: List<Long>, orderIds: List<Long>): List<WorkJob>
    @Query("DELETE FROM work_jobs WHERE id IN (:ids)") fun deleteJobs(ids: List<Long>)
    @Query("SELECT * FROM worker_payments WHERE workerId IN (:workerIds)") fun paymentsOf(workerIds: List<Long>): List<WorkerPayment>
    @Query("DELETE FROM worker_payments WHERE id IN (:ids)") fun deletePayments(ids: List<Long>)
}

/** Same idea as [TrackingDao]: device-safe ids + change tracking for the cloud. */
class TrackingWorkerDao(private val d: WorkerDao, private val db: AppDatabase) : WorkerDao by d {
    private fun nextId(table: String) = DeviceIds.next(db.openHelper.writableDatabase, table)

    override fun insertWorker(w: Worker): Long { val x = if (w.id == 0L) w.copy(id = nextId("workers")) else w; d.insertWorker(x); Changes.put(S.workers, x); return x.id }
    override fun insertJob(j: WorkJob): Long { val x = if (j.id == 0L) j.copy(id = nextId("work_jobs")) else j; d.insertJob(x); Changes.put(S.workJobs, x); return x.id }
    override fun insertPayment(p: WorkerPayment): Long { val x = if (p.id == 0L) p.copy(id = nextId("worker_payments")) else p; d.insertPayment(x); Changes.put(S.workerPayments, x); return x.id }
    override fun updateWorker(w: Worker) { d.updateWorker(w); Changes.put(S.workers, w) }
    override fun updateJob(j: WorkJob) { d.updateJob(j); Changes.put(S.workJobs, j) }
    override fun updatePayment(p: WorkerPayment) { d.updatePayment(p); Changes.put(S.workerPayments, p) }

    override fun insertWorkers(l: List<Worker>) { d.insertWorkers(l); Changes.putAll(S.workers, l) }
    override fun insertJobs(l: List<WorkJob>) { d.insertJobs(l); Changes.putAll(S.workJobs, l) }
    override fun insertPayments(l: List<WorkerPayment>) { d.insertPayments(l); Changes.putAll(S.workerPayments, l) }
    override fun clearWorkers() { val o = d.workers(); d.clearWorkers(); Changes.delAll(S.workers, o) }
    override fun clearJobs() { val o = d.jobs(); d.clearJobs(); Changes.delAll(S.workJobs, o) }
    override fun clearPayments() { val o = d.payments(); d.clearPayments(); Changes.delAll(S.workerPayments, o) }

    override fun deleteWorkers(ids: List<Long>) { val o = d.workers().filter { it.id in ids }; d.deleteWorkers(ids); Changes.delAll(S.workers, o) }
    override fun deleteJobs(ids: List<Long>) { val o = d.jobs().filter { it.id in ids }; d.deleteJobs(ids); Changes.delAll(S.workJobs, o) }
    override fun deletePayments(ids: List<Long>) { val o = d.payments().filter { it.id in ids }; d.deletePayments(ids); Changes.delAll(S.workerPayments, o) }
}

// ------------- View models -------------

data class WorkerRow(
    val worker: Worker,
    val earned: Long,
    val paid: Long,
    val balance: Long,
    val pendingJobs: Int,
    val pendingAmount: Long,
)

data class WorkerLedgerEntry(val date: Long, val description: String, val earned: Long, val paid: Long, val balance: Long,
                             val payment: WorkerPayment? = null, val job: WorkJob? = null)

data class WorkerLedger(val worker: Worker, val entries: List<WorkerLedgerEntry>, val balance: Long)

/**
 * All kaarigar business rules. Reached as `repo.workers`. Every write runs in the same kind of
 * tracked transaction as the rest of the app, so it is synced and can be backed up.
 */
class WorkerRepo internal constructor(private val repo: Repository, db: AppDatabase) {
    private val dao: WorkerDao = TrackingWorkerDao(db.workerDao(), db)
    private val main get() = repo.rawDao()

    private suspend fun <T> io(block: () -> T): T = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
    private suspend fun <T> write(block: () -> T): T = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.tx(block) }
    private fun audit(id: Long, action: String, detail: String = "") =
        main.insertAudit(AuditLog(entity = "worker", entityId = id, action = action, detail = detail))

    internal fun rawDao() = dao

    // ---------------- workers ----------------
    suspend fun worker(id: Long) = io { dao.worker(id) }
    suspend fun allWorkers() = io { dao.workers() }

    suspend fun rows(includeInactive: Boolean): List<WorkerRow> = io { rowsSync(includeInactive) }

    internal fun rowsSync(includeInactive: Boolean = true): List<WorkerRow> {
        val jobs = dao.jobs().groupBy { it.workerId }
        val pays = dao.payments().groupBy { it.workerId }
        return dao.workers().filter { includeInactive || it.active }.map { w ->
            val j = jobs[w.id] ?: emptyList(); val p = pays[w.id] ?: emptyList()
            WorkerRow(w, WorkerCalc.earned(j), WorkerCalc.paid(p), WorkerCalc.balance(j, p),
                j.count { it.status == JobStatus.ASSIGNED }, WorkerCalc.pendingAmount(j))
        }
    }

    suspend fun saveWorker(w: Worker): Long {
        if (w.name.isBlank()) throw UserError("کاریگر کا نام لازمی ہے")
        val clean = w.copy(name = w.name.trim(), mobile = w.mobile.trim(), cnic = w.cnic.trim())
        return write {
            if (clean.id == 0L) dao.insertWorker(clean).also { audit(it, "create", clean.name) }
            else { dao.updateWorker(clean); audit(clean.id, "update", clean.name); clean.id }
        }
    }

    suspend fun setActive(id: Long, active: Boolean) = write {
        dao.worker(id)?.let { dao.updateWorker(it.copy(active = active)) }
        audit(id, if (active) "activate" else "deactivate")
    }

    // ---------------- jobs ----------------
    suspend fun job(id: Long) = io { dao.job(id) }
    suspend fun jobsForWorker(workerId: Long) = io { dao.jobsForWorker(workerId) }
    suspend fun jobsForOrder(orderId: Long) = io { dao.jobsForOrder(orderId) }
    suspend fun allJobs() = io { dao.jobs() }

    /** Wage this worker got last time for the same garment and task (to fill the form). */
    suspend fun lastWage(workerId: Long, garmentName: String, task: String): Long? = io {
        val all = dao.jobs().filter { it.status != JobStatus.CANCELLED && it.garmentName == garmentName }
        (all.firstOrNull { it.workerId == workerId && it.task == task } ?: all.firstOrNull { it.task == task })?.wage
    }

    suspend fun assign(itemId: Long, workerId: Long, task: String, qty: Int, wage: Long, dueDate: Long?, notes: String): Long {
        if (task.isBlank()) throw UserError("کام کی قسم لکھیں")
        if (wage < 0) throw UserError("مزدوری منفی نہیں ہو سکتی")
        return write {
            val w = dao.worker(workerId) ?: throw UserError("کاریگر منتخب کریں")
            if (!w.active) throw UserError("یہ کاریگر غیر فعال ہے")
            val item = main.item(itemId) ?: throw UserError("کپڑا نہیں ملا")
            val o = main.order(item.orderId) ?: throw UserError("آرڈر نہیں ملا")
            if (o.status == OrderStatus.CANCELLED) throw UserError("منسوخ آرڈر کا کام نہیں دیا جا سکتا")
            if (item.delivered) throw UserError("یہ کپڑا ڈیلیور ہو چکا ہے")
            if (qty < 1 || qty > item.qty) throw UserError("تعداد 1 سے ${item.qty} تک ہو سکتی ہے")
            val busy = dao.jobsForOrder(o.id).filter { it.itemId == itemId && it.task == task.trim() && it.status == JobStatus.ASSIGNED }
            val busyQty = busy.sumOf { it.qty }
            if (busyQty + qty > item.qty) {
                val names = busy.mapNotNull { dao.worker(it.workerId)?.name }.distinct().joinToString("، ")
                throw UserError("یہ کپڑا اسی کام کے لیے پہلے ہی $names کے پاس ہے")
            }
            val id = dao.insertJob(WorkJob(workerId = workerId, orderId = o.id, itemId = itemId, garmentName = item.garmentName,
                memberName = item.memberName, task = task.trim(), qty = qty, wage = wage, amount = wage * qty,
                dueDate = dueDate ?: o.deliveryDate, notes = notes.trim()))
            main.insertStatusLog(StatusLog(orderId = o.id, itemId = itemId, fromStatus = item.status, toStatus = item.status,
                note = "کاریگر: ${w.name} — ${task.trim()}"))
            audit(id, "assign", "${w.name} item $itemId x$qty wage $wage")
            id
        }
    }

    /** Work came back from the worker: the wage now counts in his account. */
    suspend fun complete(jobId: Long, date: Long = System.currentTimeMillis()) = write {
        val j = dao.job(jobId) ?: throw UserError("کام نہیں ملا")
        if (j.status != JobStatus.ASSIGNED) throw UserError("یہ کام پہلے ہی ${JobStatus.label(j.status)} ہے")
        dao.updateJob(j.copy(status = JobStatus.DONE, doneAt = date))
        audit(jobId, "done", "amount ${j.amount}")
    }

    /** Undo "complete" (mistake): the wage is taken out of the account again. */
    suspend fun reopen(jobId: Long) = write {
        val j = dao.job(jobId) ?: throw UserError("کام نہیں ملا")
        if (j.status != JobStatus.DONE) throw UserError("صرف مکمل کام واپس کھولا جا سکتا ہے")
        dao.updateJob(j.copy(status = JobStatus.ASSIGNED, doneAt = null))
        audit(jobId, "reopen")
    }

    suspend fun cancel(jobId: Long, reason: String) = write {
        val j = dao.job(jobId) ?: throw UserError("کام نہیں ملا")
        if (j.status == JobStatus.DONE) throw UserError("مکمل کام منسوخ نہیں ہو سکتا۔ پہلے 'واپس کھولیں' دبائیں")
        if (j.status == JobStatus.CANCELLED) throw UserError("یہ کام پہلے ہی منسوخ ہے")
        dao.updateJob(j.copy(status = JobStatus.CANCELLED, notes = (j.notes + " [منسوخ: $reason]").trim()))
        audit(jobId, "cancel", reason)
    }

    // ---------------- payments ----------------
    suspend fun payments() = io { dao.payments() }
    suspend fun payment(id: Long) = io { dao.payment(id) }

    suspend fun pay(workerId: Long, type: String, amount: Long, method: String, notes: String, date: Long): Long {
        if (amount <= 0) throw UserError("رقم صفر سے زیادہ ہونی چاہیے")
        if (type !in WorkerPayType.all) throw UserError("غلط قسم")
        return write {
            dao.worker(workerId) ?: throw UserError("کاریگر نہیں ملا")
            val id = dao.insertPayment(WorkerPayment(workerId = workerId, date = date, type = type, amount = amount, method = method, notes = notes.trim()))
            audit(workerId, "pay", "$type $amount")
            id
        }
    }

    /** Never deletes: the entry is kept, marked cancelled, and stops counting. */
    suspend fun voidPayment(paymentId: Long, reason: String) = write {
        val p = dao.payment(paymentId) ?: throw UserError("اندراج نہیں ملا")
        if (p.voided) throw UserError("یہ اندراج پہلے ہی منسوخ ہو چکا ہے")
        dao.updatePayment(p.copy(voided = true, notes = (p.notes + " [منسوخ: $reason]").trim()))
        audit(p.workerId, "void_payment", "${p.id} $reason")
    }

    // ---------------- account statement ----------------
    suspend fun ledger(workerId: Long): WorkerLedger = io { ledgerSync(workerId) }

    internal fun ledgerSync(workerId: Long): WorkerLedger {
        val w = dao.worker(workerId) ?: throw UserError("کاریگر نہیں ملا")
        data class Raw(val date: Long, val desc: String, val earned: Long, val paid: Long, val p: WorkerPayment?, val j: WorkJob?)
        val raws = mutableListOf<Raw>()
        dao.jobsForWorker(workerId).filter { it.status == JobStatus.DONE }.forEach { j ->
            raws += Raw(j.doneAt ?: j.assignedAt, "${j.task}: ${j.garmentName} × ${j.qty} / ${Fmt2.orderNo(j.orderId)}", j.amount, 0, null, j)
        }
        dao.paymentsForWorker(workerId).forEach { p ->
            val label = WorkerPayType.label(p.type) + (if (p.voided) " (منسوخ شدہ)" else "") + (if (p.notes.isNotBlank()) " — ${p.notes}" else "")
            raws += Raw(p.date, label, 0, if (p.voided) 0 else p.amount, p, null)
        }
        var bal = 0L
        val entries = raws.sortedBy { it.date }.map { r ->
            bal += r.earned - r.paid
            WorkerLedgerEntry(r.date, r.desc, r.earned, r.paid, bal, r.p, r.j)
        }
        return WorkerLedger(w, entries, bal)
    }

    // ---------------- sample data ----------------
    internal suspend fun addSample(orderId: Long) {
        val w1 = saveWorker(Worker(name = "استاد رشید (نمونہ)", mobile = "03211234567", skills = "شلوار قمیض، واسکٹ", sample = true))
        val w2 = saveWorker(Worker(name = "شکیل (نمونہ)", mobile = "03331234567", skills = "ٹراؤزر، کڑھائی", sample = true))
        val items = io { main.items(orderId) }
        items.getOrNull(0)?.let { assign(it.id, w1, "سلائی", it.qty, 60000, null, "") }
        items.getOrNull(1)?.let { complete(assign(it.id, w1, "سلائی", it.qty, 50000, null, "")) }
        items.getOrNull(3)?.let { assign(it.id, w2, "سلائی", it.qty, 30000, null, "") }
        pay(w1, WorkerPayType.ADVANCE, 100000, PayMethod.CASH, "نمونہ", System.currentTimeMillis())
    }

    /** Called inside the sample-removal transaction. */
    internal fun removeSampleInTx(sampleOrderIds: List<Long>) {
        val ids = dao.sampleWorkers().map { it.id }
        val jobs = dao.jobsOf(ids, sampleOrderIds).map { it.id }
        if (jobs.isNotEmpty()) dao.deleteJobs(jobs)
        val pays = dao.paymentsOf(ids).map { it.id }
        if (pays.isNotEmpty()) dao.deletePayments(pays)
        if (ids.isNotEmpty()) dao.deleteWorkers(ids)
    }
}
