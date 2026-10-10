package pk.tailormaster.pro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pk.tailormaster.pro.data.OrderStatus
import pk.tailormaster.pro.domain.Dates
import pk.tailormaster.pro.ui.*

private data class MenuItem(val num: Int, val title: String, val color: Color, val go: () -> Screen)

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    var day by remember { mutableLongStateOf(Dates.today()) }
    val dash by load(day) { dashboard(day) }
    val s by repo.settings.state.collectAsState()

    val menu = listOf(
        MenuItem(1, "ڈیش بورڈ", Color(0xFF0F4C5C)) { Screen.Home },
        MenuItem(2, "کسٹمر ماسٹر", Color(0xFF1565C0)) { Screen.Customers() },
        MenuItem(3, "فیملی ممبرز", Color(0xFF6A1B9A)) { Screen.FamilyMembers },
        MenuItem(4, "ناپ ماسٹر", Color(0xFF00838F)) { Screen.Measurements },
        MenuItem(5, "کپڑوں کی اقسام", Color(0xFF5D4037)) { Screen.Garments },
        MenuItem(6, "ریٹ ماسٹر", Color(0xFF8A5A00)) { Screen.Rates },
        MenuItem(7, "نئی بکنگ", Color(0xFF2E7D32)) { Screen.Booking() },
        MenuItem(8, "آرڈر مینجمنٹ", Color(0xFF283593)) { Screen.Orders() },
        MenuItem(9, "ڈیلیوری مینجمنٹ", Color(0xFF00695C)) { Screen.Deliveries },
        MenuItem(10, "پیمنٹ اور کھاتہ", Color(0xFFAD1457)) { Screen.Payments() },
        MenuItem(11, "اخراجات", Color(0xFFC62828)) { Screen.Expenses },
        MenuItem(12, "رسید پرنٹنگ", Color(0xFF37474F)) { Screen.PrintCenter },
        MenuItem(13, "رپورٹس", Color(0xFF4527A0)) { Screen.ReportsHome },
        MenuItem(14, "بیک اپ اور سیٹنگز", Color(0xFF455A64)) { Screen.Settings },
    )

    Page(s.shopName.ifBlank { "ٹیلر ماسٹر پرو" }, showBack = false, actions = {
        IconButton(onClick = { nav.go(Screen.Settings) }) { Icon(Icons.Default.Settings, "سیٹنگز") }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val days = if (s.lastBackupAt == 0L) -1 else ((System.currentTimeMillis() - s.lastBackupAt) / Dates.DAY).toInt()
                if (days == -1 || days >= 7) {
                    Card(colors = CardDefaults.cardColors(containerColor = Semantic.warn.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth().clickable { nav.go(Screen.Backup) }) {
                        Text(if (days == -1) "⚠ ابھی تک کوئی بیک اپ نہیں بنایا گیا۔ نیا فون لینے یا ایپ دوبارہ انسٹال کرنے سے پہلے بیک اپ ضرور بنائیں۔ یہاں دبائیں۔"
                            else "⚠ آخری بیک اپ ${Fmt.num(days)} دن پہلے بنا تھا۔ نیا بیک اپ بنانے کے لیے یہاں دبائیں۔",
                            Modifier.padding(12.dp), color = Semantic.warn)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DateInput("تاریخ", day, { day = Dates.startOfDay(it) }, Modifier.weight(1f))
                    if (day != Dates.today()) { Spacer(Modifier.width(8.dp)); SmallButton("آج") { day = Dates.today() } }
                }
            }
            item {
                val d = dash
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("آج کی بکنگ", Fmt.num(d?.todayBookings ?: 0), Color(0xFF1565C0)) { nav.go(Screen.Orders("today")) }
                        Stat("آج کی ڈیلیوری", Fmt.num(d?.todayDeliveries ?: 0), Color(0xFF00695C)) { nav.go(Screen.Orders("due_today")) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("جاری آرڈرز", Fmt.num(d?.openOrders ?: 0), Color(0xFFB26A00)) { nav.go(Screen.Orders("open")) }
                        Stat("تیار آرڈرز", Fmt.num(d?.readyOrders ?: 0), Color(0xFF2E7D32)) { nav.go(Screen.Orders("ready")) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("تاخیر والے آرڈرز", Fmt.num(d?.overdueOrders ?: 0), Color(0xFFC62828)) { nav.go(Screen.Orders("overdue")) }
                        Stat("کل کسٹمرز", Fmt.num(d?.totalCustomers ?: 0), Color(0xFF6A1B9A)) { nav.go(Screen.Customers()) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(if (day == Dates.today()) "آج کی وصولی" else "اس دن کی وصولی", Fmt.money(d?.todayCollection ?: 0), Color(0xFF2E7D32)) { nav.go(Screen.Report(pk.tailormaster.pro.reports.ReportKind.DAILY_COLLECTION)) }
                        Stat("ماہانہ وصولی", Fmt.money(d?.monthCollection ?: 0), Color(0xFF00838F))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("کل بقایا رقم", Fmt.money(d?.totalOutstanding ?: 0), Color(0xFFAD1457)) { nav.go(Screen.Report(pk.tailormaster.pro.reports.ReportKind.OUTSTANDING)) }
                        Stat(if (day == Dates.today()) "آج کے اخراجات" else "اس دن کے اخراجات", Fmt.money(d?.todayExpenses ?: 0), Color(0xFFC62828)) { nav.go(Screen.Expenses) }
                    }
                }
            }
            item {
                SectionCard("فوری کام") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton("نیا کسٹمر", Modifier.weight(1f)) { nav.go(Screen.CustomerEdit()) }
                        BigButton("نیا ناپ", Modifier.weight(1f)) { nav.go(Screen.Measurements) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton("نئی بکنگ", Modifier.weight(1f), color = Semantic.ok) { nav.go(Screen.Booking()) }
                        BigButton("رقم وصول کریں", Modifier.weight(1f)) { nav.go(Screen.Payments()) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton("آرڈر تلاش کریں", Modifier.weight(1f)) { nav.go(Screen.Orders()) }
                        BigButton("رسید پرنٹ", Modifier.weight(1f)) { nav.go(Screen.PrintCenter) }
                    }
                }
            }
            val alerts = dash?.alerts ?: emptyList()
            if (alerts.isNotEmpty()) {
                item {
                    SectionCard("🔔 ڈیلیوری یاد دہانی (${Fmt.num(alerts.size)})") {
                        alerts.take(15).forEach { r ->
                            Row(Modifier.fillMaxWidth().clickable { nav.go(Screen.OrderDetail(r.order.id)) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Fmt.orderNo(r.order.id) + " • " + r.customerName + if (r.order.urgent) " ⚡" else "", style = MaterialTheme.typography.titleSmall)
                                    Text("ڈیلیوری: " + Fmt.date(r.order.deliveryDate) + " • " + OrderStatus.label(r.order.status), style = MaterialTheme.typography.bodySmall)
                                }
                                Chip(if (r.overdue) "تاخیر" else "قریب", if (r.overdue) Semantic.bad else Semantic.warn)
                            }
                        }
                    }
                }
            }
            item {
                Text("مین مینو", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            menu.drop(1).chunked(2).forEach { pair ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { mi ->
                            Card(
                                modifier = Modifier.weight(1f).heightIn(min = 76.dp).clickable { nav.go(mi.go()) },
                                colors = CardDefaults.cardColors(containerColor = mi.color),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(Fmt.num(mi.num), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                    Text(mi.title, color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
