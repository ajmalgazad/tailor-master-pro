package pk.tailormaster.pro

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pk.tailormaster.pro.data.*
import pk.tailormaster.pro.print.ReceiptBuilder

/** Kaarigar module: jobs, wages, payments, receipts, backup, and the v1 -> v2 database update. */
@RunWith(AndroidJUnit4::class)
class WorkersTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun rs(r: Long) = r * 100

    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = Repository(db, SettingsStore(ctx, "workers_test"))
        repo.seedIfNeeded()
    }

    @After fun tearDown() { db.close() }

    private suspend fun order(): Pair<Long, List<OrderItem>> {
        val cid = repo.saveCustomer(Customer(name = "اسلم"))
        val self = repo.members(cid).first { it.isSelf }
        val sk = repo.garments().first { it.name == "شلوار قمیض" }
        val oid = repo.saveOrder(OrderDraft(customerId = cid, memberId = self.id, items = listOf(
            DraftItem(garmentId = sk.id, garmentName = sk.name, memberId = self.id, memberName = self.name, qty = 3, rate = rs(1500)))))
        return oid to repo.orderFull(oid).items
    }

    @Test fun jobs_wages_payments_balance() = runBlocking {
        val (oid, items) = order()
        val w = repo.workers.saveWorker(Worker(name = "رشید", mobile = "0321"))
        val j1 = repo.workers.assign(items[0].id, w, "سلائی", 2, rs(500), null, "")
        // only 1 piece left for the same task
        try { repo.workers.assign(items[0].id, w, "سلائی", 2, rs(500), null, ""); fail("over-assigned") } catch (e: UserError) { }
        val j2 = repo.workers.assign(items[0].id, w, "سلائی", 1, rs(500), null, "")
        assertEquals("pending work does not count yet", 0L, repo.workers.ledger(w).balance)

        repo.workers.complete(j1)
        assertEquals(rs(1000), repo.workers.ledger(w).balance)
        repo.workers.pay(w, WorkerPayType.ADVANCE, rs(1500), PayMethod.CASH, "", System.currentTimeMillis())
        assertEquals("advance bigger than earned -> negative", -rs(500), repo.workers.ledger(w).balance)
        repo.workers.complete(j2)
        assertEquals(0L, repo.workers.ledger(w).balance)

        // cancelled payment stops counting but is kept
        val p = repo.workers.pay(w, WorkerPayType.PAYMENT, rs(200), PayMethod.CASH, "", System.currentTimeMillis())
        repo.workers.voidPayment(p, "غلطی")
        assertEquals(0L, repo.workers.ledger(w).balance)
        assertEquals(2, repo.workers.payments().size)

        // done job cannot be cancelled; reopen first
        try { repo.workers.cancel(j1, "x"); fail("cancelled done job") } catch (e: UserError) { }
        repo.workers.reopen(j1)
        repo.workers.cancel(j1, "کپڑا واپس")
        assertEquals(-rs(1000), repo.workers.ledger(w).balance)
        assertEquals(rs(500), repo.workers.lastWage(w, "شلوار قمیض", "سلائی"))

        val row = repo.workers.rows(true).first { it.worker.id == w }
        assertEquals(0, row.pendingJobs)

        // receipts build from saved data
        assertTrue(ReceiptBuilder.build(repo, ReceiptKind.WORKER_SLIP, j2).lines.isNotEmpty())
        assertTrue(ReceiptBuilder.build(repo, ReceiptKind.WORKER_LEDGER, w).lines.isNotEmpty())
        assertTrue(ReceiptBuilder.build(repo, ReceiptKind.WORKER_PAYMENT, p).lines.isNotEmpty())
        assertTrue(repo.orderFull(oid).log.any { it.note.contains("رشید") })
    }

    @Test fun delivered_or_cancelled_cannot_be_given() = runBlocking {
        val (oid, items) = order()
        val w = repo.workers.saveWorker(Worker(name = "شکیل"))
        repo.cancelOrder(oid, "test")
        try { repo.workers.assign(items[0].id, w, "سلائی", 1, 0, null, ""); fail() } catch (e: UserError) { }
        try { repo.workers.saveWorker(Worker(name = " ")); fail() } catch (e: UserError) { }
    }

    @Test fun backup_and_sample_data_include_workers() = runBlocking {
        SampleData.add(repo)
        val before = repo.workers.rows(true)
        assertEquals(2, before.size)
        val json = BackupManager.export(repo)
        val check = BackupManager.check(json)
        assertTrue(check.errors.toString(), check.ok)
        assertEquals(2, check.data!!.workers.size)
        SampleData.remove(repo)
        assertEquals(0, repo.workers.rows(true).size)
        assertEquals(0, repo.workers.allJobs().size)
        BackupManager.restore(repo, check.data!!)
        assertEquals(before.map { it.balance }, repo.workers.rows(true).map { it.balance })
    }

    /** A phone with the old app (database v1) must keep all its data after the update. */
    @Test fun migration_1_to_2_keeps_data() {
        val name = "migration-test.db"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO customers (id, name, fatherName, mobile, whatsapp, address, city, area, category, regDate, openingBalance, notes, active, mergedIntoId, sample) " +
                "VALUES (7, 'پرانا کسٹمر', '', '0300', '', '', '', '', '', 0, 500, '', 1, NULL, 0)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true).close()
        val newDb = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*AppMigrations.all).build()
        runBlocking {
            val r = Repository(newDb, SettingsStore(ctx, "workers_mig"))
            assertEquals("پرانا کسٹمر", r.customer(7)?.name)
            val w = r.workers.saveWorker(Worker(name = "نیا کاریگر"))
            assertEquals("نیا کاریگر", r.workers.worker(w)?.name)
        }
        newDb.close()
    }
}
