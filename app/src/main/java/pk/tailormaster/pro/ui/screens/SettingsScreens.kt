package pk.tailormaster.pro.ui.screens

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.tailormaster.pro.BuildConfigInfo
import pk.tailormaster.pro.data.BackupCheck
import pk.tailormaster.pro.data.BackupManager
import pk.tailormaster.pro.data.SampleData
import pk.tailormaster.pro.domain.Dates
import pk.tailormaster.pro.ui.*
import java.io.File

private class Entry(val title: String, val sub: String, val go: () -> Unit)

@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val gate = rememberPinGate()
    val settings by repo.settings.state.collectAsState()
    var pinDialog by remember { mutableStateOf(false) }
    var askSampleRemove by remember { mutableStateOf(false) }

    val entries = listOf(
        Entry("دکان اور عمومی سیٹنگز", "نام، پتہ، کرنسی، تاریخ، ہندسے، تھیم، فونٹ") { nav.go(Screen.ShopSettings) },
        Entry("رسید ڈیزائنر", "لوگو، سرخی، فوٹر، شرائط، حروف کا سائز") { nav.go(Screen.ReceiptDesigner) },
        Entry("بلوٹوتھ پرنٹر", "پرنٹر جوڑیں، 58mm / 80mm، ٹیسٹ پرنٹ") { nav.go(Screen.PrinterSetup) },
        Entry("بیک اپ اور ریسٹور", "ڈیٹا محفوظ کریں یا واپس لائیں") { nav.go(Screen.Backup) },
        Entry("کپڑوں کی اقسام اور ناپ کے خانے", "") { nav.go(Screen.Garments) },
        Entry("ریٹ ماسٹر", "معیاری اور خصوصی ریٹ، سروسز") { nav.go(Screen.Rates) },
        Entry("ڈپلیکیٹ کسٹمر ضم کریں", "") { nav.go(Screen.Merge) },
        Entry("آڈٹ لاگ", "تمام اہم تبدیلیوں کا ریکارڈ") { nav.go(Screen.AuditLog) },
    )
    Page("بیک اپ اور سیٹنگز") { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { e ->
                Card(Modifier.fillMaxWidth().clickable { e.go() }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall)
                        if (e.sub.isNotBlank()) Text(e.sub, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                SectionCard("PIN لاک") {
                    Text(if (settings.pinHash.isEmpty()) "PIN لاک بند ہے" else "PIN لاک فعال ہے۔ ایپ کھولنے، ریکارڈ ضم کرنے، ریسٹور اور اندراج منسوخ کرنے پر PIN مانگا جائے گا۔",
                        style = MaterialTheme.typography.bodyMedium)
                    BigButton(if (settings.pinHash.isEmpty()) "PIN مقرر کریں" else "PIN تبدیل / ختم کریں") {
                        gate.require(repo.settings) { pinDialog = true }
                    }
                }
            }
            item {
                SectionCard("نمونہ (ٹیسٹ) ڈیٹا") {
                    Text("ایپ آزمانے کے لیے نمونہ کسٹمر، آرڈر اور اخراجات۔ اصل کاروبار شروع کرنے سے پہلے انہیں ختم کر دیں۔", style = MaterialTheme.typography.bodySmall)
                    SmallButton("نمونہ ڈیٹا شامل کریں", Modifier.fillMaxWidth(), enabled = !settings.sampleLoaded) {
                        scope.act("نمونہ ڈیٹا شامل ہو گیا") { SampleData.add(repo) }
                    }
                    SmallButton("نمونہ ڈیٹا ختم کریں", Modifier.fillMaxWidth()) { askSampleRemove = true }
                }
            }
            item {
                Text("ٹیلر ماسٹر پرو • ورژن " + BuildConfigInfo.versionName(LocalContext.current), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (pinDialog) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text("نیا PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Input("نیا PIN (4 سے 8 ہندسے)", p1, { p1 = it.filter { c -> c.isDigit() }.take(8) }, keyboard = KeyboardType.NumberPassword)
                    Input("دوبارہ لکھیں", p2, { p2 = it.filter { c -> c.isDigit() }.take(8) }, keyboard = KeyboardType.NumberPassword)
                    Text("PIN بھولنے کی صورت میں ڈیٹا بیک اپ سے ہی واپس آ سکتا ہے۔ PIN کہیں لکھ کر محفوظ رکھیں۔", style = MaterialTheme.typography.bodySmall, color = Semantic.warn)
                }
            },
            confirmButton = {
                Button(onClick = {
                    when {
                        p1.length < 4 -> Msg.err("PIN کم از کم 4 ہندسوں کا ہو")
                        p1 != p2 -> Msg.err("دونوں PIN ایک جیسے نہیں")
                        else -> { repo.settings.setPin(p1); pinDialog = false; Msg.ok("PIN مقرر ہو گیا") }
                    }
                }) { Text("محفوظ") }
            },
            dismissButton = {
                if (settings.pinHash.isNotEmpty()) TextButton(onClick = { repo.settings.setPin(""); pinDialog = false; Msg.ok("PIN لاک ختم") }) { Text("PIN ختم کریں") }
                else TextButton(onClick = { pinDialog = false }) { Text("منسوخ") }
            },
        )
    }
    if (askSampleRemove) Confirm("نمونہ ڈیٹا ختم کریں؟", "صرف '(نمونہ)' والے کسٹمر، ان کے آرڈر، ادائیگیاں، ناپ اور نمونہ اخراجات حذف ہوں گے۔ آپ کا اصل ڈیٹا محفوظ رہے گا۔",
        "ختم کریں", danger = true, onDismiss = { askSampleRemove = false }) {
        gate.require(repo.settings) { scope.act("نمونہ ڈیٹا ختم ہو گیا") { SampleData.remove(repo) } }
    }
}

@Composable
fun ShopSettingsScreen() {
    val repo = LocalRepo.current
    val s by repo.settings.state.collectAsState()
    fun up(f: (pk.tailormaster.pro.data.AppSettings) -> pk.tailormaster.pro.data.AppSettings) = repo.settings.update(f)
    Page("دکان اور عمومی سیٹنگز") { pad ->
        FormColumn(pad) {
            SectionCard("دکان") {
                Input("دکان کا نام", s.shopName, { v -> up { it.copy(shopName = v) } })
                Input("رابطہ نمبر", s.shopPhone, { v -> up { it.copy(shopPhone = v) } }, keyboard = KeyboardType.Phone)
                Input("پتہ", s.shopAddress, { v -> up { it.copy(shopAddress = v) } }, singleLine = false)
                Input("ڈیلیوری کی شرائط", s.deliveryTerms, { v -> up { it.copy(deliveryTerms = v) } }, singleLine = false)
            }
            SectionCard("عمومی") {
                Select("کرنسی کا نشان", listOf("روپے", "Rs", "PKR", "₨"), s.currencyLabel, { it }, { v -> up { it.copy(currencyLabel = v) } })
                Select("تاریخ کا انداز", listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd MMM yyyy"), s.dateFormat,
                    { it + "  →  " + Dates.format(System.currentTimeMillis(), it) }, { v -> up { it.copy(dateFormat = v) } })
                Select("ہندسے", listOf(false, true), s.urduDigits, { if (it) "اردو ہندسے (۱۲۳)" else "انگریزی ہندسے (123)" }, { v -> up { it.copy(urduDigits = v) } })
                Select("ناپ کی ڈیفالٹ اکائی", listOf("انچ", "سینٹی میٹر"), s.defaultUnit, { it }, { v -> up { it.copy(defaultUnit = v) } })
                Select("ڈیلیوری یاد دہانی (دن پہلے)", listOf(0, 1, 2, 3), s.dueSoonDays, { Fmt.num(it) + " دن" }, { v -> up { it.copy(dueSoonDays = v) } })
            }
            SectionCard("ظاہری شکل") {
                Select("تھیم", listOf("system", "light", "dark"), s.theme, { when (it) { "light" -> "روشن"; "dark" -> "تاریک (ڈارک)"; else -> "فون کے مطابق" } },
                    { v -> up { it.copy(theme = v) } })
                Select("ایپ کا فونٹ", listOf("system", "naskh", "nastaliq"), s.uiFont,
                    { when (it) { "naskh" -> "نسخ"; "nastaliq" -> "نستعلیق"; else -> "فون کا فونٹ" } }, { v -> up { it.copy(uiFont = v) } })
            }
        }
    }
}

@Composable
fun BackupScreen() {
    val ctx = LocalContext.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val gate = rememberPinGate()
    val s by repo.settings.state.collectAsState()
    var check by remember { mutableStateOf<BackupCheck?>(null) }
    var askRestore by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.act("بیک اپ محفوظ ہو گیا") {
            withContext(Dispatchers.IO) {
                val logo = File(ctx.filesDir, "logo.png").takeIf { it.exists() }?.readBytes()?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""
                val text = BackupManager.export(repo, logo)
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw Exception("فائل نہیں کھل سکی")
                // Verify by reading it back
                val back = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                if (!BackupManager.check(back).ok) throw Exception("بیک اپ کی تصدیق ناکام۔ دوبارہ کوشش کریں")
            }
            repo.settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.act {
            val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "" }
            check = BackupManager.check(text)
        }
    }

    Page("بیک اپ اور ریسٹور") { pad ->
        FormColumn(pad) {
            SectionCard("⚠ ضروری ہدایت") {
                Text("تمام ڈیٹا صرف اسی فون میں محفوظ ہے۔ فون خراب ہونے، گم ہونے، نیا فون لینے یا ایپ ان انسٹال کرنے سے پہلے بیک اپ ضرور بنائیں اور فائل کو گوگل ڈرائیو، واٹس ایپ یا کمپیوٹر پر بھیج دیں۔",
                    color = Semantic.warn)
                KV("آخری بیک اپ", if (s.lastBackupAt == 0L) "کبھی نہیں" else Fmt.dateTime(s.lastBackupAt), true)
            }
            SectionCard("بیک اپ بنائیں") {
                Text("فائل کہاں محفوظ کرنی ہے، یہ آپ خود منتخب کریں گے (مثلاً Downloads یا Google Drive)۔", style = MaterialTheme.typography.bodySmall)
                BigButton("بیک اپ فائل بنائیں", color = Semantic.ok) {
                    exportLauncher.launch("tailor-master-backup-" + Dates.format(System.currentTimeMillis(), "yyyyMMdd-HHmm") + ".json")
                }
            }
            SectionCard("بیک اپ سے واپس لائیں (ریسٹور)") {
                Text("ریسٹور کرنے سے اس فون کا موجودہ تمام ڈیٹا بیک اپ والے ڈیٹا سے بدل جائے گا۔ پہلے موجودہ ڈیٹا کا بیک اپ بنا لیں۔", style = MaterialTheme.typography.bodySmall, color = Semantic.bad)
                SmallButton("بیک اپ فائل منتخب کریں", Modifier.fillMaxWidth()) { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
                check?.let { c ->
                    HorizontalDivider()
                    if (c.errors.isNotEmpty()) {
                        Text("یہ فائل ریسٹور نہیں ہو سکتی:", color = Semantic.bad)
                        c.errors.forEach { Text("• $it", color = Semantic.bad) }
                    } else {
                        Text("فائل درست ہے ✓", color = Semantic.ok)
                        c.data?.let { Text("بیک اپ کی تاریخ: " + Fmt.dateTime(it.exportedAt)) }
                        c.summary.forEach { (k, v) -> KV(k, Fmt.num(v)) }
                        BigButton("ریسٹور کریں", color = Semantic.bad) { askRestore = true }
                    }
                }
            }
        }
    }
    if (askRestore) Confirm("کیا آپ کو یقین ہے؟", "اس فون کا موجودہ تمام ڈیٹا ختم ہو کر بیک اپ والا ڈیٹا آ جائے گا۔ یہ عمل واپس نہیں ہو سکتا۔",
        "ہاں، ریسٹور کریں", danger = true, onDismiss = { askRestore = false }) {
        gate.require(repo.settings) {
            scope.act("ڈیٹا کامیابی سے ریسٹور ہو گیا") {
                val data = check?.data ?: return@act
                BackupManager.restore(repo, data)
                withContext(Dispatchers.IO) {
                    val f = File(ctx.filesDir, "logo.png")
                    if (data.logoBase64.isNotBlank()) f.writeBytes(Base64.decode(data.logoBase64, Base64.DEFAULT))
                }
                check = null
            }
        }
    }
}

@Composable
fun AuditLogScreen() {
    val logs by load { audits(500) }
    Page("آڈٹ لاگ") { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp)) {
            items(logs ?: emptyList(), key = { it.id }) { a ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(Fmt.dateTime(a.at) + " • " + a.entity + " #" + a.entityId + " • " + a.action, style = MaterialTheme.typography.bodyMedium)
                    if (a.detail.isNotBlank()) Text(a.detail, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                }
            }
            if (logs != null && logs!!.isEmpty()) item { Empty("کوئی ریکارڈ نہیں") }
        }
    }
}
