package pk.tailormaster.pro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pk.tailormaster.pro.data.AppDatabase
import pk.tailormaster.pro.data.DeviceIds
import pk.tailormaster.pro.data.PayMethod
import pk.tailormaster.pro.data.Repository
import pk.tailormaster.pro.data.SampleData
import pk.tailormaster.pro.data.SettingsStore
import pk.tailormaster.pro.data.TxnType
import pk.tailormaster.pro.sync.LinkPlan
import pk.tailormaster.pro.sync.SyncManager
import java.net.HttpURLConnection
import java.net.URL

/**
 * Two "phones" in one test, both talking to the Firebase emulator running on the CI machine
 * (10.0.2.2 from inside the Android emulator). Skipped when the emulator is not running.
 */
@RunWith(AndroidJUnit4::class)
class SyncTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val host = "10.0.2.2"
    private val options = FirebaseOptions.Builder().setProjectId("demo-tailor").setApplicationId("1:1:android:1").setApiKey("fake-api-key").build()

    private fun emulatorUp(): Boolean = try {
        (URL("http://$host:9099/").openConnection() as HttpURLConnection).run { connectTimeout = 2000; responseCode; true }
    } catch (e: Exception) { false }

    private suspend fun waitFor(what: String, timeoutMs: Long = 30_000, cond: suspend () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return; delay(300) }
        throw AssertionError("timeout waiting for: $what")
    }

    private class Device(val repo: Repository, val sync: SyncManager, val db: AppDatabase)

    private fun device(ctx: Context, name: String): Device {
        ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val repo = Repository(db, SettingsStore(ctx, name))
        val sync = SyncManager(ctx, repo, db, name)
        sync.init(options, host)
        return Device(repo, sync, db)
    }

    @Test fun phoneAndSecondDevice_shareTheSameData() = runBlocking {
        assumeTrue("Firebase emulator not running", emulatorUp())
        val email = "shop" + System.nanoTime() + "@test.pk"
        val password = "secret123"
        // create the shop account
        val tmpApp = FirebaseApp.initializeApp(ctx, options, "creator" + System.nanoTime())
        FirebaseAuth.getInstance(tmpApp).apply { useEmulator(host, 9099) }.createUserWithEmailAndPassword(email, password).await()

        // Phone A: existing shop data, links first -> uploads everything
        DeviceIds.device = 0
        val a = device(ctx, "devA" + System.nanoTime())
        a.repo.seedIfNeeded()
        SampleData.add(a.repo)
        val planA = a.sync.signIn(email, password)
        assertEquals(LinkPlan.CLOUD_EMPTY_UPLOAD, planA)
        a.sync.completeLink(planA)
        a.sync.awaitPendingWrites()
        assertEquals(0, a.repo.settings.value.deviceNo)

        // Device B: fresh install, links second -> takes the cloud data
        val b = device(ctx, "devB" + System.nanoTime())
        b.repo.seedIfNeeded()
        val planB = b.sync.signIn(email, password)
        assertEquals(LinkPlan.CLOUD_HAS_DATA_REPLACE_LOCAL, planB)
        b.sync.completeLink(planB)
        assertEquals(1, b.repo.settings.value.deviceNo)
        val expectedCustomers = a.repo.customerRows(true).map { it.customer.id to it.balance }.toSet()
        waitFor("B receives A's customers and balances") { b.repo.customerRows(true).map { it.customer.id to it.balance }.toSet() == expectedCustomers }
        waitFor("B receives orders") { b.repo.orderRows().size == a.repo.orderRows().size }
        waitFor("B receives garments") { b.repo.garments().size == a.repo.garments().size }

        // B records a payment -> A sees it
        val order = b.repo.orderRows().first { it.outstanding > 0 }
        val tid = b.repo.recordTxn(order.order.customerId, order.order.id, TxnType.PAYMENT, 50000, PayMethod.CASH, "", "sync test", System.currentTimeMillis())
        assertTrue("device 1 creates ids in its own range", tid >= DeviceIds.SPAN)
        b.sync.awaitPendingWrites()
        waitFor("A receives B's payment") { a.repo.txn(tid) != null }
        assertEquals(b.repo.balance(order.order.customerId), a.repo.balance(order.order.customerId))

        // Shop settings travel too
        DeviceIds.device = 0
        a.repo.settings.update { it.copy(shopName = "نئی دکان") }
        a.sync.awaitPendingWrites()
        waitFor("B receives shop name") { b.repo.settings.value.shopName == "نئی دکان" }

        // A deletes sample data -> deletions reach B
        SampleData.remove(a.repo)
        a.sync.awaitPendingWrites()
        waitFor("B receives deletions") { b.repo.customerRows(true).none { it.customer.sample } }

        a.sync.stop(); b.sync.stop(); a.db.close(); b.db.close()
    }

    @Test fun otherShopsDataIsNotReadable() = runBlocking {
        assumeTrue("Firebase emulator not running", emulatorUp())
        val app = FirebaseApp.initializeApp(ctx, options, "intruder" + System.nanoTime())
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, 9099) }
        auth.createUserWithEmailAndPassword("intruder" + System.nanoTime() + "@test.pk", "secret123").await()
        val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance(app).apply { useEmulator(host, 8080) }
        var denied = false
        try {
            fs.collection("shops").document("someone-else").collection("customers").get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            denied = e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("security rules must block other shops", denied)
    }
}
