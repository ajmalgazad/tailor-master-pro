package pk.tailormaster.pro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pk.tailormaster.pro.data.*
import pk.tailormaster.pro.domain.Dates
import pk.tailormaster.pro.print.ReceiptBuilder

/**
 * Runs on a real Android runtime (emulator or phone) against a real SQLite database.
 * Covers the "Mandatory testing" list of the specification.
 */
@RunWith(AndroidJUnit4::class)
class BusinessRulesTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun rs(r: Long) = r * 100

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = Repository(db, SettingsStore(ctx))
        repo.seedIfNeeded()
    }

    @After fun tearDown() { db.close() }

    private suspend fun garment(name: String) = repo.garments().first { it.name == name }

    private suspend fun newProfile(cid: Long, mid: Long, g: Garment, name: String, length: String): Long {
        val f = repo.fields(g.id)
        val values = f.map { MeasureValue(it.id, it.label, if (it.label == "لمبائی") length else "10") }
        return repo.saveNewProfile(MeasurementProfile(customerId = cid, memberId = mid, garmentId = g.id, name = name, valuesJson = encodeValues(values)))
    }

    @Test fun familyMembers_and_multipleProfiles_and_snapshots() = runBlocking {
        val cid = repo.saveCustomer(Customer(name = "اسلم", mobile = "03001111111"))
        val self = repo.members(cid).first { it.isSelf }
        val father = repo.saveMember(FamilyMember(customerId = cid, name = "رشید", relation = "والد"))
        val son = repo.saveMember(FamilyMember(customerId = cid, name = "علی", relation = "بیٹا"))
        assertEquals("one customer, three family profiles", 3, repo.members(cid).size)

        val sk = garment("شلوار قمیض"); val wc = garment("واسکٹ")
        val p1 = newProfile(cid, self.id, sk, "عام", "40")
        val p2 = newProfile(cid, self.id, wc, "واسکٹ", "28")
        val p3 = newProfile(cid, son, sk, "علی", "30")
        newProfile(cid, father, sk, "رشید", "42")
        assertEquals(2, repo.profilesForMember(self.id).size)
        assertEquals("measurements are not mixed between members", 1, repo.profilesForMember(son).size)

        val orderId = repo.saveOrder(OrderDraft(customerId = cid, memberId = self.id, items = listOf(
            DraftItem(garmentId = sk.id, garmentName = sk.name, memberId = self.id, memberName = self.name, qty = 2, rate = rs(1500), profileId = p1),
            DraftItem(garmentId = wc.id, garmentName = wc.name, memberId = self.id, memberName = self.name, qty = 1, rate = rs(2500), profileId = p2),
            DraftItem(garmentId = sk.id, garmentName = sk.name, memberId = son, memberName = "علی", qty = 1, rate = rs(900), profileId = p3),
        )))
        val items = repo.orderFull(orderId).items
        assertEquals("different garments use different profiles", setOf(p1, p2, p3), items.map { it.profileId }.toSet())

        // Update the measurements -> new version; old profile and the order snapshot stay unchanged
        val old = repo.profile(p1)!!
        val newVals = decodeValues(old.valuesJson).map { if (it.label == "لمبائی") it.copy(value = "41") else it }
        val p1v2 = repo.saveProfileVersion(old, old.copy(valuesJson = encodeValues(newVals)))
        assertNotEquals(p1, p1v2)
        val oldAfter = repo.profile(p1)!!
        assertTrue(oldAfter.archived)
        assertEquals("40", decodeValues(oldAfter.valuesJson).first { it.label == "لمبائی" }.value)
        val snap = decodeSnapshot(repo.orderFull(orderId).items.first { it.profileId == p1 }.measurementJson)!!
        assertEquals("order keeps booking-time measurements", "40", snap.values.first { it.label == "لمبائی" }.value)
        assertEquals(listOf(oldAfter.id), repo.profileHistory(repo.profile(p1v2)!!).map { it.id })
    }

    @Test fun rates_discounts_and_defaultRateChangeDoesNotTouchOldOrders() = runBlocking {
        val cid = repo.saveCustomer(Customer(name = "خالد"))
        val self = repo.members(cid).first()
        val son = repo.saveMember(FamilyMember(customerId = cid, name = "بیٹا", relation = "بیٹا"))
        val sk = garment("شلوار قمیض")
        assertEquals(rs(1500), repo.resolveRate(sk.id, cid, self.id))
        repo.saveCustomRate(CustomRate(garmentId = sk.id, customerId = cid, rate = rs(1300)))
        repo.saveCustomRate(CustomRate(garmentId = sk.id, customerId = cid, memberId = son, rate = rs(800)))
        assertEquals("customer rate", rs(1300), repo.resolveRate(sk.id, cid, self.id))
        assertEquals("member rate wins", rs(800), repo.resolveRate(sk.id, cid, son))

        val oid = repo.saveOrder(OrderDraft(customerId = cid, memberId = self.id,
            items = listOf(DraftItem(garmentId = sk.id, garmentName = sk.name, memberId = self.id, memberName = "x", qty = 3, rate = rs(1300))),
            charges = listOf(Charge("کڑھائی", rs(500))), discountIsPercent = true, discountInput = 10.0))
        val o = repo.order(oid)!!
        assertEquals(rs(3900), o.itemsTotal)
        assertEquals(rs(440), o.discountAmount) // 10% of 4400
        assertEquals(rs(3960), o.netAmount)

        repo.saveGarment(sk.copy(defaultRate = rs(2000)))
        val after = repo.order(oid)!!
        assertEquals("old order amount unchanged", rs(3960), after.netAmount)
        assertEquals(rs(1300), repo.orderFull(oid).items.first().rate)
        assertTrue(repo.rateHistory().any { it.garmentId == sk.id && it.newRate == rs(2000) })

        try {
            repo.saveOrder(OrderDraft(customerId = cid, memberId = self.id,
                items = listOf(DraftItem(garmentId = sk.id, garmentName = sk.name, memberId = self.id, memberName = "x", qty = 1, rate = -5))))
            fail("negative rate must be rejected")
        } catch (e: UserError) { /* expected */ }
    }

    @Test fun payments_balances_reversals_and_printingCreatesNothing() = runBlocking {
        val cid = repo.saveCustomer(Customer(name = "ناصر", openingBalance = rs(1000)))
        val self = repo.members(cid).first()
        val sk = garment("شلوار قمیض")
        val oid = repo.saveOrder(OrderDraft(customerId = cid, memberId = self.id,
            items = listOf(DraftItem(garmentId = sk.id, garmentName = sk.name, memberId = self.id, memberName = "x", qty = 2, rate = rs(1500))),
            advance = rs(1000)))
        assertEquals(rs(1000 + 3000 - 1000), repo.balance(cid))
        assertEquals(rs(2000), repo.orderFull(oid).outstanding)

        val t = repo.recordTxn(cid, oid, TxnType.PAYMENT, rs(500), PayMethod.CASH, "", "", System.currentTimeMillis())
        assertEquals(rs(2500), repo.balance(cid))
        assertEquals(rs(1500), repo.orderFull(oid).outstanding)
        assertEquals(rs(2500), repo.balanceAfterTxn(t))

        repo.reverseTxn(t, "غلط اندراج")
        assertEquals("reversal restores balance", rs(3000), repo.balance(cid))
        assertNotNull("original kept for audit", repo.txn(t)!!.reversedById)
        try { repo.reverseTxn(t, "again"); fail("double reversal") } catch (e: UserError) { }

        val ordersBefore = repo.orderRows().size
        val txnsBefore = repo.allTxns().size
        listOf(ReceiptKind.BOOKING, ReceiptKind.MEASUREMENT, ReceiptKind.LEDGER, ReceiptKind.CUSTOMER).forEach { k ->
            ReceiptBuilder.build(repo, k, if (k == ReceiptKind.LEDGER || k == ReceiptKind.CUSTOMER) cid else oid)
        }
        ReceiptBuilder.build(repo, ReceiptKind.PAYMENT, t)
        repo.logPrint(ReceiptKind.BOOKING, oid, "test", "SENT", "")
        assertEquals("printing creates no orders", ordersBefore, repo.orderRows().size)
        assertEquals("printing creates no payments", txnsBefore, repo.allTxns().size)
    }

    @Test fun partialDelivery_thenComplete() = runBlocking {
        val cid = repo.saveCustomer(Customer(name = "سلیم"))
        val self = repo.members(cid).first()
        val sk = garment("شلوار قمیض"); val wc = garment("واسکٹ")
        val oid = repo.saveOrder(OrderDraft(customerId = cid, memberId = self.id, items = listOf(
            DraftItem(garmentId = sk.id, garmentName = sk.name, memberId = self.id, memberName = "x", qty = 1, rate = rs(1500)),
            DraftItem(garmentId = wc.id, garmentName = wc.name, memberId = self.id, memberName = "x", qty = 1, rate = rs(2500)))))
        val items = repo.orderFull(oid).items
        repo.deliver(oid, listOf(items[0].id), "", rs(1000), PayMethod.CASH, false)
        val mid = repo.orderFull(oid)
        assertNotEquals("order not delivered while items pending", OrderStatus.DELIVERED, mid.order.status)
        assertEquals(1, mid.items.count { it.delivered })
        assertEquals(rs(3000), mid.outstanding)
        repo.deliver(oid, listOf(items[1].id), "", rs(3000), PayMethod.CASH, false)
        val done = repo.orderFull(oid)
        assertEquals(OrderStatus.DELIVERED, done.order.status)
        assertEquals(0L, done.outstanding)
        assertEquals(2, done.deliveries.size)
    }

    @Test fun cancelledOrder_staysInHistory_and_leavesBalance() = runBlocking {
        val cid = repo.saveCustomer(Customer(name = "کامران"))
        val self = repo.members(cid).first()
        val sk = garment("شلوار قمیض")
        val oid = repo.saveOrder(OrderDraft(customerId = cid, memberId = self.id,
            items = listOf(DraftItem(garmentId = sk.id, garmentName = sk.name, memberId = self.id, memberName = "x", qty = 1, rate = rs(1500)))))
        repo.cancelOrder(oid, "کسٹمر نے منع کیا")
        assertEquals(0L, repo.balance(cid))
        assertTrue(repo.orderRows().any { it.order.id == oid && it.order.status == OrderStatus.CANCELLED })
    }

    @Test fun backup_restore_preservesEverything() = runBlocking {
        SampleData.add(repo)
        val customers = repo.customerRows(true).map { it.customer.id to it.balance }.toSet()
        val orders = repo.orderRows().map { it.order.id to it.outstanding }.toSet()
        val profiles = repo.allProfiles().size
        val text = BackupManager.export(repo)
        val check = BackupManager.check(text)
        assertTrue(check.errors.toString(), check.ok)

        // Restore into a different, empty database
        val db2 = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val repo2 = Repository(db2, SettingsStore(ctx))
        BackupManager.restore(repo2, check.data!!)
        assertEquals(customers, repo2.customerRows(true).map { it.customer.id to it.balance }.toSet())
        assertEquals(orders, repo2.orderRows().map { it.order.id to it.outstanding }.toSet())
        assertEquals(profiles, repo2.allProfiles().size)
        db2.close()

        // Corrupt file is rejected
        assertFalse(BackupManager.check("{ not json").ok)
        val broken = text.replace("\"customerId\":", "\"customerId\":9999")
        assertFalse("broken relations are rejected", BackupManager.check(broken).ok)

        SampleData.remove(repo)
        assertTrue(repo.customerRows(true).none { it.customer.sample })
    }

    @Test fun dataSurvivesRestart() = runBlocking {
        val name = "restart-test-" + System.nanoTime() + ".db"
        val d1 = Room.databaseBuilder(ctx, AppDatabase::class.java, name).build()
        val r1 = Repository(d1, SettingsStore(ctx))
        r1.seedIfNeeded()
        val cid = r1.saveCustomer(Customer(name = "مستقل"))
        d1.close()
        val d2 = Room.databaseBuilder(ctx, AppDatabase::class.java, name).build()
        val r2 = Repository(d2, SettingsStore(ctx))
        assertEquals("مستقل", r2.customer(cid)?.name)
        assertTrue(r2.garments().isNotEmpty())
        d2.close()
        ctx.deleteDatabase(name)
        Unit
    }

    @Test fun dashboard_countsFromDatabase() = runBlocking {
        SampleData.add(repo)
        val d = repo.dashboard(Dates.today())
        assertEquals("sample: one booking today, one 10 days ago", 1, d.todayBookings)
        assertTrue(d.overdueOrders >= 1)
        assertTrue(d.todayCollection > 0)
        assertTrue(d.totalOutstanding > 0)
        assertEquals(2, d.totalCustomers)
    }
}
