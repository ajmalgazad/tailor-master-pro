package pk.tailormaster.pro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pk.tailormaster.pro.data.Repository
import pk.tailormaster.pro.print.PrinterManager
import pk.tailormaster.pro.ui.screens.*

@Composable
fun AppRoot(repo: Repository, printer: PrinterManager, sync: pk.tailormaster.pro.sync.SyncManager? = null) {
    val settings by repo.settings.state.collectAsState()
    Fmt.settings = settings
    val nav = remember { Navigator() }
    NavHolder.nav = nav
    var unlocked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { repo.seedIfNeeded() }

    TailorTheme(settings.theme, settings.uiFont) {
        CompositionLocalProvider(LocalRepo provides repo, LocalPrinter provides printer, LocalNav provides nav, LocalSync provides sync) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    if (settings.pinHash.isNotEmpty() && !unlocked) {
                        LockScreen { pin ->
                            if (repo.settings.checkPin(pin)) unlocked = true else Msg.err("غلط PIN")
                        }
                    } else {
                        BackHandler(enabled = nav.stack.size > 1) { nav.back() }
                        // Key by stack depth + screen so each screen keeps its own state
                        androidx.compose.runtime.key(nav.stack.size, nav.current) {
                            ScreenHost(nav.current)
                        }
                    }
                    val msg by Msg.flow.collectAsState()
                    msg?.let { m ->
                        LaunchedEffect(m.id) { delay(if (m.type == MsgType.ERROR) 6000 else 3000); if (Msg.flow.value?.id == m.id) Msg.flow.value = null }
                        Box(Modifier.align(Alignment.BottomCenter)) { MessageBanner(m) { Msg.flow.value = null } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenHost(s: Screen) {
    when (s) {
        Screen.Home -> HomeScreen()
        is Screen.Customers -> CustomersScreen()
        is Screen.CustomerEdit -> CustomerEditScreen(s.id)
        is Screen.CustomerProfile -> CustomerProfileScreen(s.id, s.tab)
        is Screen.MemberEdit -> MemberEditScreen(s.customerId, s.memberId)
        Screen.FamilyMembers -> FamilyMembersScreen()
        Screen.Measurements -> MeasurementsScreen()
        is Screen.ProfileEdit -> ProfileEditScreen(s)
        is Screen.ProfileView -> ProfileViewScreen(s.id)
        Screen.Garments -> GarmentsScreen()
        is Screen.GarmentEdit -> GarmentEditScreen(s.id)
        Screen.Rates -> RatesScreen()
        is Screen.Booking -> BookingScreen(s)
        is Screen.Orders -> OrdersScreen(s.filter)
        is Screen.OrderDetail -> OrderDetailScreen(s.id)
        Screen.Deliveries -> DeliveriesScreen()
        is Screen.Deliver -> DeliverScreen(s.orderId)
        is Screen.Payments -> PaymentsScreen(s.customerId, s.orderId)
        is Screen.Ledger -> LedgerScreen(s.customerId)
        Screen.Expenses -> ExpensesScreen()
        Screen.PrintCenter -> PrintCenterScreen()
        is Screen.Preview -> PreviewScreen(s.kind, s.refId, s.profileSlip)
        Screen.ReportsHome -> ReportsHomeScreen()
        is Screen.Report -> ReportScreen(s.kind)
        Screen.Settings -> SettingsScreen()
        Screen.ShopSettings -> ShopSettingsScreen()
        Screen.ReceiptDesigner -> ReceiptDesignerScreen()
        Screen.PrinterSetup -> PrinterSetupScreen()
        Screen.Backup -> BackupScreen()
        Screen.AuditLog -> AuditLogScreen()
        Screen.Merge -> MergeScreen()
        Screen.Cloud -> CloudScreen()
    }
}

@Composable
private fun LockScreen(onPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ٹیلر ماسٹر پرو", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text("ایپ کھولنے کے لیے PIN درج کریں", style = MaterialTheme.typography.bodyLarge)
        Input("PIN", pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, Modifier.width(240.dp), KeyboardType.NumberPassword)
        BigButton("کھولیں", Modifier.fillMaxWidth()) { onPin(pin); pin = "" }
    }
}
