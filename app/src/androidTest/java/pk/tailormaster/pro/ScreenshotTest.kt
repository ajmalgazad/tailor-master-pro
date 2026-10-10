package pk.tailormaster.pro

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pk.tailormaster.pro.data.ReceiptKind
import pk.tailormaster.pro.data.SampleData
import pk.tailormaster.pro.print.ReceiptBuilder
import pk.tailormaster.pro.print.ReceiptRenderer
import pk.tailormaster.pro.reports.ReportKind
import pk.tailormaster.pro.ui.NavHolder
import pk.tailormaster.pro.ui.Screen
import java.io.File
import java.io.FileOutputStream

/**
 * Opens every main screen of the real app (with sample data) and saves screenshots,
 * and renders every receipt type to PNG exactly as it would be sent to the thermal printer.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app: TailorApp get() = ApplicationProvider.getApplicationContext()
    private val out: File by lazy { File(app.getExternalFilesDir(null), "shots").apply { mkdirs() } }

    private fun save(name: String, bmp: Bitmap) {
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun settle() {
        rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle()
    }

    private fun shot(name: String) {
        settle()
        save(name, rule.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun go(name: String, s: Screen) {
        rule.runOnUiThread { NavHolder.nav!!.home(); if (s != Screen.Home) NavHolder.nav!!.go(s) }
        shot(name)
    }

    private fun ensureSample() = runBlocking {
        app.repo.seedIfNeeded()
        if (app.repo.customerRows(true).isEmpty()) SampleData.add(app.repo)
    }

    @Test fun walkThroughAllScreens() {
        ensureSample()
        val repo = app.repo
        val (orderId, customerId, profileId) = runBlocking {
            val o = repo.orderRows().first { !it.order.isDraft }
            Triple(o.order.id, o.order.customerId, repo.allProfiles().first().id)
        }
        go("01_home", Screen.Home)
        go("02_customers", Screen.Customers())
        go("03_customer_profile", Screen.CustomerProfile(customerId))
        go("04_family_members", Screen.FamilyMembers)
        go("05_measurements", Screen.Measurements)
        go("06_profile_view", Screen.ProfileView(profileId))
        go("07_garments", Screen.Garments)
        go("08_rates", Screen.Rates)
        go("09_booking_new", Screen.Booking(customerId = customerId))
        go("10_orders", Screen.Orders())
        go("11_order_detail", Screen.OrderDetail(orderId))
        go("12_deliveries", Screen.Deliveries)
        go("13_deliver", Screen.Deliver(orderId))
        go("14_payments", Screen.Payments(customerId))
        go("15_ledger", Screen.Ledger(customerId))
        go("16_expenses", Screen.Expenses)
        go("17_print_center", Screen.PrintCenter)
        go("18_preview_booking", Screen.Preview(ReceiptKind.BOOKING, orderId))
        go("19_reports", Screen.ReportsHome)
        go("20_report_pending", Screen.Report(ReportKind.PENDING))
        go("21_settings", Screen.Settings)
        go("22_receipt_designer", Screen.ReceiptDesigner)
        go("23_printer_setup", Screen.PrinterSetup)
        go("24_backup", Screen.Backup)
        go("25_edit_booking", Screen.Booking(draft = runBlocking { repo.buildDraftFromOrder(orderId, false) }))
        go("26_profile_edit", runBlocking { repo.profile(profileId)!! }.let { Screen.ProfileEdit(it.customerId, it.memberId, it.garmentId, it.id, "version") })
        repo.settings.update { it.copy(theme = "dark", urduDigits = true) }
        go("27_home_dark_urdu_digits", Screen.Home)
        go("28_order_detail_dark", Screen.OrderDetail(orderId))
        repo.settings.update { it.copy(theme = "system", urduDigits = false) }
    }

    /** Same app in English (left-to-right): main screens + receipts. */
    @Test fun englishScreens() {
        ensureSample()
        val repo = app.repo
        val (orderId, customerId) = runBlocking {
            val o = repo.orderRows().first { !it.order.isDraft }
            o.order.id to o.order.customerId
        }
        rule.runOnUiThread { repo.settings.update { it.copy(language = "en") }; pk.tailormaster.pro.i18n.I18n.setLanguage("en") }
        try {
            go("en_01_home", Screen.Home)
            go("en_02_customers", Screen.Customers())
            go("en_03_customer_profile", Screen.CustomerProfile(customerId))
            go("en_04_booking_new", Screen.Booking(customerId = customerId))
            go("en_05_order_detail", Screen.OrderDetail(orderId))
            go("en_06_deliver", Screen.Deliver(orderId))
            go("en_07_ledger", Screen.Ledger(customerId))
            go("en_08_settings", Screen.Settings)
            go("en_09_shop_settings", Screen.ShopSettings)
            go("en_10_garments", Screen.Garments)
            go("en_11_report_pending", Screen.Report(ReportKind.PENDING))
            go("en_12_preview_booking", Screen.Preview(ReceiptKind.BOOKING, orderId))
            runBlocking {
                val s = repo.settings.value.copy(paperWidthMm = 58, printableDots = 384, receiptFont = "naskh")
                save("en_receipt_booking", ReceiptRenderer.render(app, ReceiptBuilder.build(repo, ReceiptKind.BOOKING, orderId), s))
                save("en_receipt_ledger", ReceiptRenderer.render(app, ReceiptBuilder.build(repo, ReceiptKind.LEDGER, customerId), s))
                val t = pk.tailormaster.pro.reports.Reports.build(repo, ReportKind.DAILY_BOOKING, pk.tailormaster.pro.reports.ReportFilter())
                FileOutputStream(File(out, "en_report_bookings.pdf")).use { pk.tailormaster.pro.reports.Exporter.writePdf(app, s, t, it) }
            }
        } finally {
            rule.runOnUiThread { repo.settings.update { it.copy(language = "ur") }; pk.tailormaster.pro.i18n.I18n.setLanguage("ur") }
        }
    }

    @Test fun renderAllReceipts() {
        ensureSample()
        val repo = app.repo
        runBlocking {
            val o = repo.orderRows().first { !it.order.isDraft }
            val txn = repo.allTxns().first()
            val base = repo.settings.value
            val profileId = repo.allProfiles().first().id
            val configs = listOf(
                "58mm_naskh" to base.copy(paperWidthMm = 58, printableDots = 384, receiptFont = "naskh"),
                "80mm_naskh" to base.copy(paperWidthMm = 80, printableDots = 576, receiptFont = "naskh"),
                "58mm_nastaliq" to base.copy(paperWidthMm = 58, printableDots = 384, receiptFont = "nastaliq"),
            )
            for ((label, s) in configs) {
                fun render(name: String, d: pk.tailormaster.pro.print.ReceiptDoc) = save("receipt_${label}_$name", ReceiptRenderer.render(app, d, s))
                render("test", ReceiptBuilder.test(s))
                render("booking", ReceiptBuilder.build(repo, ReceiptKind.BOOKING, o.order.id))
                if (label == "58mm_naskh") {
                    render("measurement_slip", ReceiptBuilder.build(repo, ReceiptKind.MEASUREMENT, o.order.id))
                    render("profile_slip", ReceiptBuilder.profileSlip(repo, profileId))
                    render("payment", ReceiptBuilder.build(repo, ReceiptKind.PAYMENT, txn.id))
                    render("ledger", ReceiptBuilder.build(repo, ReceiptKind.LEDGER, o.order.customerId))
                    render("customer", ReceiptBuilder.build(repo, ReceiptKind.CUSTOMER, o.order.customerId))
                }
            }
            // Delivery receipt: partially deliver the first item of a fresh copy of the order
            val full = repo.orderFull(o.order.id)
            val pending = full.items.filter { !it.delivered }
            if (pending.size >= 2) {
                val did = repo.deliver(o.order.id, listOf(pending.first().id), "نمونہ ڈیلیوری", 0, pk.tailormaster.pro.data.PayMethod.CASH, false)
                save("receipt_58mm_naskh_delivery_partial", ReceiptRenderer.render(app, ReceiptBuilder.build(repo, ReceiptKind.DELIVERY, did), base))
            }
            // PDF + CSV export smoke test
            val t = pk.tailormaster.pro.reports.Reports.build(repo, ReportKind.DAILY_BOOKING, pk.tailormaster.pro.reports.ReportFilter())
            FileOutputStream(File(out, "report_bookings.pdf")).use { pk.tailormaster.pro.reports.Exporter.writePdf(app, base, t, it) }
            FileOutputStream(File(out, "report_bookings.csv")).use { pk.tailormaster.pro.reports.Exporter.writeCsv(t, it) }
        }
    }
}
