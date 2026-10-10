package pk.tailormaster.pro.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.tailormaster.pro.data.Customer
import pk.tailormaster.pro.data.Garment
import pk.tailormaster.pro.data.OrderStatus
import pk.tailormaster.pro.domain.Dates
import pk.tailormaster.pro.reports.Exporter
import pk.tailormaster.pro.reports.ReportFilter
import pk.tailormaster.pro.reports.ReportKind
import pk.tailormaster.pro.reports.ReportTable
import pk.tailormaster.pro.reports.Reports
import pk.tailormaster.pro.ui.*

@Composable
fun ReportsHomeScreen() {
    val nav = LocalNav.current
    Page("رپورٹس") { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReportKind.entries.toList()) { k ->
                Card(Modifier.fillMaxWidth().clickable { nav.go(Screen.Report(k)) }) {
                    Text(k.title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                }
            }
            item { Text("ہر رپورٹ PDF اور CSV (ایکسل) میں محفوظ کی جا سکتی ہے۔", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun defaultFilter(k: ReportKind): ReportFilter {
    val now = System.currentTimeMillis()
    return when (k) {
        ReportKind.DAILY_BOOKING, ReportKind.DAILY_COLLECTION -> ReportFilter(Dates.today(), Dates.endOfDay(now))
        ReportKind.RATE_HISTORY, ReportKind.CUSTOMER_HISTORY -> ReportFilter(0, Dates.endOfDay(now))
        else -> ReportFilter(Dates.startOfMonth(now), Dates.endOfDay(now))
    }
}

@Composable
fun ReportScreen(kind: ReportKind) {
    val ctx = LocalContext.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var f by remember { mutableStateOf(defaultFilter(kind)) }
    var cust by remember { mutableStateOf<Customer?>(null) }
    var picking by remember { mutableStateOf(false) }
    val garmentList by load { garments() }
    val table by load(f) { Reports.build(this, kind, f) }
    var pending by remember { mutableStateOf<ReportTable?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val t = pending ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.act("PDF محفوظ ہو گئی") {
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri)?.use { Exporter.writePdf(ctx, repo.settings.value, t, it) } ?: throw Exception("فائل نہیں کھل سکی")
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val t = pending ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.act("CSV محفوظ ہو گئی") {
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri)?.use { Exporter.writeCsv(t, it) } ?: throw Exception("فائل نہیں کھل سکی")
            }
        }
    }
    val fileBase = "report-" + kind.name.lowercase() + "-" + Dates.format(System.currentTimeMillis(), "yyyyMMdd")

    Page(kind.title) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard("فلٹر") {
                    if (kind.usesDates) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val now = System.currentTimeMillis()
                            SmallButton("آج", Modifier.weight(1f)) { f = f.copy(from = Dates.today(), to = Dates.endOfDay(now)) }
                            SmallButton("یہ مہینہ", Modifier.weight(1f)) { f = f.copy(from = Dates.startOfMonth(now), to = Dates.endOfDay(now)) }
                            SmallButton("پچھلا مہینہ", Modifier.weight(1f)) {
                                val pm = Dates.startOfMonth(Dates.addDays(Dates.startOfMonth(now), -1))
                                f = f.copy(from = pm, to = Dates.endOfMonth(pm))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateInput("سے", if (f.from == 0L) Dates.today() else f.from, { f = f.copy(from = Dates.startOfDay(it)) }, Modifier.weight(1f))
                            DateInput("تک", f.to, { f = f.copy(to = Dates.endOfDay(it)) }, Modifier.weight(1f))
                        }
                        if (f.from == 0L) Text("شروع سے", style = MaterialTheme.typography.bodySmall)
                    }
                    if (kind.usesCustomer) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("کسٹمر: " + (cust?.name ?: "تمام"), Modifier.weight(1f)) { picking = true }
                            if (cust != null) SmallButton("ہٹائیں") { cust = null; f = f.copy(customerId = null) }
                        }
                    }
                    if (kind.usesGarment) Select("کپڑا", listOf<Garment?>(null) + (garmentList ?: emptyList()), (garmentList ?: emptyList()).firstOrNull { it.id == f.garmentId },
                        { it?.name ?: "تمام کپڑے" }, { f = f.copy(garmentId = it?.id) })
                    if (kind.usesStatus) Select("اسٹیٹس", listOf<String?>(null) + OrderStatus.all, f.status, { it?.let { s -> OrderStatus.label(s) } ?: "تمام" }, { f = f.copy(status = it) })
                }
            }
            val t = table
            if (t == null) item { Text("رپورٹ تیار ہو رہی ہے...") } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton("PDF محفوظ کریں", Modifier.weight(1f)) { pending = t; pdfLauncher.launch("$fileBase.pdf") }
                        BigButton("CSV (ایکسل)", Modifier.weight(1f)) { pending = t; csvLauncher.launch("$fileBase.csv") }
                    }
                }
                item {
                    if (t.subtitle.isNotBlank()) Text(t.subtitle, style = MaterialTheme.typography.bodySmall)
                    Text("ریکارڈ: " + Fmt.num(t.rows.size), style = MaterialTheme.typography.bodySmall)
                }
                item { ReportGrid(t) }
            }
        }
    }
    if (picking) CustomerPicker(onDismiss = { picking = false }) { cust = it; f = f.copy(customerId = it.id); picking = false }
}

@Composable
private fun ReportGrid(t: ReportTable) {
    val scroll = rememberScrollState()
    val colW = 120.dp
    Column(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Row(Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
            t.headers.forEach { h -> Text(h, Modifier.width(colW).padding(6.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall) }
        }
        if (t.rows.isEmpty()) Text("کوئی ریکارڈ نہیں", Modifier.padding(12.dp))
        t.rows.take(500).forEach { r ->
            Row {
                r.forEach { c -> Text(c, Modifier.width(colW).padding(6.dp), style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider()
        }
        if (t.rows.size > 500) Text("اسکرین پر پہلے ۵۰۰ ریکارڈ۔ مکمل رپورٹ کے لیے PDF / CSV محفوظ کریں۔", Modifier.padding(8.dp))
        t.totals?.let { tot ->
            Row(Modifier.background(MaterialTheme.colorScheme.secondaryContainer)) {
                tot.forEach { c -> Text(c, Modifier.width(colW).padding(6.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
