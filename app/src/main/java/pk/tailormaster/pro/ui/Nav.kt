package pk.tailormaster.pro.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import pk.tailormaster.pro.data.OrderDraft
import pk.tailormaster.pro.reports.ReportKind

/** Every screen of the app. */
sealed class Screen {
    object Home : Screen()
    data class Customers(val pickFor: String? = null) : Screen()
    data class CustomerEdit(val id: Long? = null) : Screen()
    data class CustomerProfile(val id: Long, val tab: Int = 0) : Screen()
    data class MemberEdit(val customerId: Long, val memberId: Long? = null) : Screen()
    object FamilyMembers : Screen()
    object Measurements : Screen()
    /** mode: "new", "version" (save changes as new version), "meta" (edit notes/name only) */
    data class ProfileEdit(val customerId: Long, val memberId: Long, val garmentId: Long? = null, val profileId: Long? = null, val mode: String = "new", val returnAfterSave: Boolean = false) : Screen()
    data class ProfileView(val id: Long) : Screen()
    object Garments : Screen()
    data class GarmentEdit(val id: Long? = null) : Screen()
    object Rates : Screen()
    data class Booking(val draft: OrderDraft? = null, val customerId: Long? = null, val memberId: Long? = null) : Screen()
    data class Orders(val filter: String? = null) : Screen()
    data class OrderDetail(val id: Long) : Screen()
    object Deliveries : Screen()
    data class Deliver(val orderId: Long) : Screen()
    data class Payments(val customerId: Long? = null, val orderId: Long? = null) : Screen()
    data class Ledger(val customerId: Long) : Screen()
    object Expenses : Screen()
    object PrintCenter : Screen()
    data class Preview(val kind: String, val refId: Long, val profileSlip: Boolean = false) : Screen()
    object ReportsHome : Screen()
    data class Report(val kind: ReportKind) : Screen()
    object Settings : Screen()
    object ShopSettings : Screen()
    object ReceiptDesigner : Screen()
    object PrinterSetup : Screen()
    object Backup : Screen()
    object AuditLog : Screen()
    object Merge : Screen()
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()

    /** Per-screen state that survives going forward to another screen and coming back. */
    private val states = java.util.IdentityHashMap<Screen, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> screenState(s: Screen, init: () -> T): T = states.getOrPut(s) { init() } as T

    private fun pop() { val s = stack.removeAt(stack.lastIndex); if (stack.none { it === s }) states.remove(s) }

    fun go(s: Screen) { stack.add(s) }
    fun replace(s: Screen) { if (stack.size > 1) pop(); stack.add(s) }
    fun back(): Boolean = if (stack.size > 1) { pop(); true } else false
    fun home() { while (stack.size > 1) pop() }
}

val LocalNav = staticCompositionLocalOf<Navigator> { error("no nav") }

/** Lets automated tests drive navigation. */
object NavHolder { @Volatile var nav: Navigator? = null }
