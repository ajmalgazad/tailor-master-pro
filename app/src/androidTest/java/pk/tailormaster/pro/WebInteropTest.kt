package pk.tailormaster.pro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pk.tailormaster.pro.data.AppDatabase
import pk.tailormaster.pro.data.PayMethod
import pk.tailormaster.pro.data.Repository
import pk.tailormaster.pro.data.SettingsStore
import pk.tailormaster.pro.data.TxnType
import pk.tailormaster.pro.sync.LinkPlan
import pk.tailormaster.pro.sync.SyncManager

/**
 * Runs only in CI between the two halves of the web test: the web app created a shop with sample data;
 * this phone links to it, must see the same customers and balances, and records a payment the web must then show.
 */
@RunWith(AndroidJUnit4::class)
class WebInteropTest {
    @Test fun phoneReadsWebShop_andWritesPaymentBack() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val email = args.getString("webEmail"); val password = args.getString("webPassword")
        assumeTrue("web credentials not given", email != null && password != null)
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val name = "interop" + System.nanoTime()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val repo = Repository(db, SettingsStore(ctx, name))
        val sync = SyncManager(ctx, repo, db, name)
        sync.init(FirebaseOptions.Builder().setProjectId("demo-tailor").setApplicationId("1:1:android:1").setApiKey("fake-api-key").build(), "10.0.2.2")
        val plan = sync.signIn(email!!, password!!)
        assertEquals(LinkPlan.CLOUD_HAS_DATA_REPLACE_LOCAL, plan)
        sync.completeLink(plan)
        val end = System.currentTimeMillis() + 40_000
        while (System.currentTimeMillis() < end && (repo.customerRows(true).size < 2 || repo.orderRows().size < 2 || repo.garments().size < 26)) delay(300)
        val aslam = repo.customerRows(true).first { it.customer.name == "محمد اسلم (نمونہ)" }
        val ayesha = repo.customerRows(true).first { it.customer.name == "عائشہ بی بی (نمونہ)" }
        // Same sample data and same rules as the phone's own sample -> identical balances
        assertEquals(600000L, aslam.balance)
        assertEquals(232500L, ayesha.balance)
        assertEquals(26, repo.garments().size)
        val order = repo.orderRows().first { it.order.customerId == aslam.customer.id }
        repo.recordTxn(aslam.customer.id, order.order.id, TxnType.PAYMENT, 10000, PayMethod.CASH, "", "از فون", System.currentTimeMillis())
        sync.awaitPendingWrites()
        assertEquals(590000L, repo.balance(aslam.customer.id))
        sync.stop(); db.close()
    }
}
