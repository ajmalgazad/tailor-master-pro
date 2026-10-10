package pk.tailormaster.pro.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.tailormaster.pro.data.AppSettings
import pk.tailormaster.pro.data.ReceiptKind
import pk.tailormaster.pro.data.UserError
import pk.tailormaster.pro.domain.Calc
import pk.tailormaster.pro.print.PrintResult
import pk.tailormaster.pro.print.PrinterState
import pk.tailormaster.pro.print.ReceiptBuilder
import pk.tailormaster.pro.print.ReceiptDoc
import pk.tailormaster.pro.print.ReceiptRenderer
import pk.tailormaster.pro.ui.*
import java.io.File
import java.io.FileOutputStream

private val copyKinds = setOf(ReceiptKind.BOOKING, ReceiptKind.PAYMENT, ReceiptKind.DELIVERY)

@Composable
fun printerStatusText(st: PrinterState): Pair<String, Color> = when (st) {
    is PrinterState.Connected -> ("منسلک: " + st.name) to Semantic.ok
    is PrinterState.Connecting -> ("رابطہ ہو رہا ہے: " + st.name) to Semantic.warn
    is PrinterState.Printing -> ("پرنٹ ہو رہا ہے: " + st.name) to Semantic.warn
    is PrinterState.Error -> ("خرابی: " + st.message) to Semantic.bad
    PrinterState.Disconnected -> "پرنٹر منسلک نہیں" to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun PrintCenterScreen() {
    val nav = LocalNav.current
    val printer = LocalPrinter.current
    val repo = LocalRepo.current
    val st by printer.state.collectAsState()
    val logs by load { printLogs(200) }
    var orderNo by remember { mutableStateOf("") }
    Page("رسید پرنٹنگ") { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard("پرنٹر") {
                    val (t, c) = printerStatusText(st)
                    Text(t, color = c)
                    val s = repo.settings.value
                    Text("منتخب پرنٹر: " + s.printerName.ifBlank { "کوئی نہیں" } + " • " + Fmt.num(s.paperWidthMm) + "mm", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("پرنٹر سیٹ اپ", Modifier.weight(1f)) { nav.go(Screen.PrinterSetup) }
                        SmallButton("ٹیسٹ پرنٹ", Modifier.weight(1f)) { nav.go(Screen.Preview(ReceiptKind.TEST, 0)) }
                        SmallButton("رسید ڈیزائن", Modifier.weight(1f)) { nav.go(Screen.ReceiptDesigner) }
                    }
                }
            }
            item {
                SectionCard("آرڈر نمبر سے رسید") {
                    Input("آرڈر نمبر (مثلاً 12 یا B-00012)", orderNo, { orderNo = it }, keyboard = KeyboardType.Text)
                    val id = if (orderNo.isBlank()) null else pk.tailormaster.pro.data.DeviceIds.parse(orderNo)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("بکنگ رسید", Modifier.weight(1f), enabled = id != null) { nav.go(Screen.Preview(ReceiptKind.BOOKING, id!!)) }
                        SmallButton("ناپ کی پرچی", Modifier.weight(1f), enabled = id != null) { nav.go(Screen.Preview(ReceiptKind.MEASUREMENT, id!!)) }
                        SmallButton("آرڈر کھولیں", Modifier.weight(1f), enabled = id != null) { nav.go(Screen.OrderDetail(id!!)) }
                    }
                }
            }
            item { Text("پچھلی رسیدیں (دوبارہ پرنٹ کے لیے دبائیں)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(logs ?: emptyList(), key = { it.id }) { l ->
                Card(Modifier.fillMaxWidth().clickable {
                    if (l.kind == ReceiptKind.MEASUREMENT && l.refId < 0) nav.go(Screen.Preview(l.kind, -l.refId, profileSlip = true))
                    else nav.go(Screen.Preview(l.kind, l.refId))
                }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.title.ifBlank { ReceiptKind.label(l.kind) }, style = MaterialTheme.typography.titleSmall)
                            Text(Fmt.dateTime(l.at) + (if (l.message.isNotBlank()) " • " + l.message else ""), style = MaterialTheme.typography.bodySmall)
                        }
                        Chip(when (l.status) { PrintResult.STATUS_OK -> "پرنٹ ہوئی"; PrintResult.STATUS_SENT -> "بھیجی گئی"; else -> "ناکام" },
                            when (l.status) { PrintResult.STATUS_OK -> Semantic.ok; PrintResult.STATUS_SENT -> Semantic.warn; else -> Semantic.bad })
                    }
                }
            }
            if (logs != null && logs!!.isEmpty()) item { Empty("ابھی کوئی رسید پرنٹ نہیں ہوئی") }
        }
    }
}

/** Shows the receipt exactly as it will print, then prints it on request. */
@Composable
fun PreviewScreen(kind: String, refId: Long, profileSlip: Boolean) {
    val ctx = LocalContext.current
    val repo = LocalRepo.current
    val printer = LocalPrinter.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.state.collectAsState()
    var doc by remember { mutableStateOf<ReceiptDoc?>(null) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var withShopCopy by remember { mutableStateOf(settings.printShopCopy) }
    val st by printer.state.collectAsState()
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) Msg.ok("اجازت مل گئی۔ دوبارہ پرنٹ دبائیں") else Msg.err("بلوٹوتھ کی اجازت کے بغیر پرنٹ نہیں ہو سکتا")
    }

    LaunchedEffect(kind, refId, settings) {
        try {
            val d = if (profileSlip) ReceiptBuilder.profileSlip(repo, refId) else ReceiptBuilder.build(repo, kind, refId)
            doc = d
            bmp = withContext(Dispatchers.Default) { ReceiptRenderer.render(ctx, d, settings) }
            error = null
        } catch (e: UserError) { error = e.message } catch (e: Exception) { error = "رسید نہیں بن سکی: " + e.message }
    }

    Page(doc?.title ?: "رسید") { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            error?.let { item { Text(it, color = Semantic.bad) } }
            item {
                val (t, c) = printerStatusText(st)
                Text(t + " • " + settings.printerName.ifBlank { "پرنٹر منتخب نہیں" }, color = c, style = MaterialTheme.typography.bodySmall)
            }
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (kind in copyKinds && !profileSlip) SwitchRow("دکان کی کاپی بھی پرنٹ کریں", withShopCopy) { withShopCopy = it }
                    BigButton(if (busy) "پرنٹ ہو رہا ہے..." else "پرنٹ کریں", enabled = !busy && doc != null && bmp != null, color = Semantic.ok) {
                        if (!printer.hasPermissions()) { permLauncher.launch(printer.requiredPermissions()); return@BigButton }
                        if (settings.printerAddress.isBlank()) { Msg.err("پہلے پرنٹر منتخب کریں (رسید پرنٹنگ ← پرنٹر سیٹ اپ)"); return@BigButton }
                        busy = true
                        scope.act {
                            try {
                                val pages = mutableListOf(bmp!!)
                                if (withShopCopy && kind in copyKinds && !profileSlip) {
                                    val d2 = ReceiptBuilder.build(repo, kind, refId, "دکان کاپی")
                                    pages += withContext(Dispatchers.Default) { ReceiptRenderer.render(ctx, d2, settings) }
                                }
                                val r = printer.print(settings.printerAddress, settings.printerName, pages, settings.feedLines, settings.cutPaper)
                                repo.logPrint(kind, if (profileSlip) -refId else refId, doc?.title ?: "", r.status, r.message)
                                when (r.status) {
                                    PrintResult.STATUS_OK -> Msg.ok(r.message)
                                    PrintResult.STATUS_SENT -> Msg.warn(r.message)
                                    else -> Msg.err(r.message)
                                }
                            } finally { busy = false }
                        }
                    }
                    Text("رسید پرنٹ کرنے سے کوئی آرڈر یا وصولی دوبارہ درج نہیں ہوتی۔", style = MaterialTheme.typography.bodySmall)
                }
            }
            bmp?.let { b ->
                item {
                    Box(Modifier.background(Color.White).border(1.dp, Color.LightGray).padding(4.dp)) {
                        Image(b.asImageBitmap(), "رسید", Modifier.width(320.dp), contentScale = ContentScale.FillWidth)
                    }
                }
            }
        }
    }
}

@Composable
fun PrinterSetupScreen() {
    val repo = LocalRepo.current
    val printer = LocalPrinter.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.state.collectAsState()
    val st by printer.state.collectAsState()
    val found by printer.found.collectAsState()
    val scanning by printer.scanning.collectAsState()
    var refresh by remember { mutableStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        refresh++
        if (r.values.all { it }) Msg.ok("اجازت مل گئی") else Msg.err("اجازت نہیں ملی۔ فون کی سیٹنگز ← ایپس ← ٹیلر ماسٹر ← اجازتیں سے دیں")
    }
    val bonded = remember(refresh, st) { if (printer.hasPermissions()) printer.bondedDevices() else emptyList() }

    Page("پرنٹر سیٹ اپ") { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard("حالت") {
                    val (t, c) = printerStatusText(st)
                    Text(t, color = c, style = MaterialTheme.typography.titleSmall)
                    KV("منتخب پرنٹر", settings.printerName.ifBlank { "کوئی نہیں" })
                    if (!printer.isSupported()) Text("اس فون میں بلوٹوتھ نہیں ہے", color = Semantic.bad)
                    if (!printer.hasPermissions()) {
                        Text("بلوٹوتھ پرنٹر کے لیے اجازت درکار ہے", color = Semantic.warn)
                        BigButton("اجازت دیں") { permLauncher.launch(printer.requiredPermissions()) }
                    } else if (!printer.isEnabled()) {
                        Text("فون کا بلوٹوتھ بند ہے۔ اوپر سے بلوٹوتھ آن کریں۔", color = Semantic.warn)
                        SmallButton("دوبارہ چیک کریں") { refresh++ }
                    }
                    if (settings.printerAddress.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("رابطہ جانچیں", Modifier.weight(1f)) {
                                scope.act { printer.connect(settings.printerAddress, settings.printerName)?.let { throw UserError(it) }; Msg.ok("پرنٹر سے رابطہ ہو گیا") }
                            }
                            SmallButton("منقطع کریں", Modifier.weight(1f)) { scope.act { printer.disconnect() } }
                            SmallButton("ٹیسٹ پرنٹ", Modifier.weight(1f)) { nav.go(Screen.Preview(ReceiptKind.TEST, 0)) }
                        }
                    }
                }
            }
            item {
                SectionCard("کاغذ") {
                    Select("کاغذ کی چوڑائی", listOf(58, 80), settings.paperWidthMm, { Fmt.num(it) + " ملی میٹر" }, { w ->
                        repo.settings.update { it.copy(paperWidthMm = w, printableDots = if (w == 80) 576 else 384, cutPaper = if (w == 80) it.cutPaper else false) }
                    })
                    Text("پرنٹ ہونے والی چوڑائی (ڈاٹس): " + Fmt.num(settings.printableDots), style = MaterialTheme.typography.bodyMedium)
                    Slider(value = settings.printableDots.toFloat(), onValueChange = { v -> repo.settings.update { it.copy(printableDots = (v / 8).toInt() * 8) } },
                        valueRange = 256f..640f)
                    Text("عام طور پر 58mm = 384 اور 80mm = 576۔ اگر رسید کنارے سے کٹ رہی ہو تو کم کریں (مثلاً 360 یا 512)۔", style = MaterialTheme.typography.bodySmall)
                    SwitchRow("پرنٹ کے بعد کاغذ کاٹیں (صرف کٹر والے پرنٹر)", settings.cutPaper) { v -> repo.settings.update { it.copy(cutPaper = v) } }
                }
            }
            item { Text("جوڑے گئے (Paired) پرنٹر", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(bonded, key = { "b" + it.address }) { d ->
                Card(Modifier.fillMaxWidth().clickable {
                    repo.settings.update { it.copy(printerAddress = d.address, printerName = d.name) }
                    Msg.ok("پرنٹر منتخب ہو گیا: " + d.name)
                }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleSmall)
                            Text(d.address, style = MaterialTheme.typography.bodySmall)
                        }
                        if (d.address == settings.printerAddress) Chip("منتخب", Semantic.ok)
                    }
                }
            }
            if (bonded.isEmpty()) item { Text("کوئی جوڑا گیا آلہ نہیں۔ پرنٹر آن کریں اور نیچے 'نئے پرنٹر تلاش کریں' دبائیں، یا فون کی بلوٹوتھ سیٹنگ سے جوڑیں (PIN اکثر 0000 یا 1234)۔",
                style = MaterialTheme.typography.bodySmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(if (scanning) "تلاش جاری ہے..." else "نئے پرنٹر تلاش کریں", Modifier.weight(1f), enabled = !scanning) {
                        if (!printer.hasPermissions()) permLauncher.launch(printer.requiredPermissions())
                        else printer.startScan()?.let { Msg.err(it) }
                    }
                    if (scanning) SmallButton("روکیں") { printer.stopScan() }
                }
            }
            items(found.filter { f -> bonded.none { it.address == f.address } }, key = { "f" + it.address }) { d ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleSmall)
                            Text(d.address, style = MaterialTheme.typography.bodySmall)
                        }
                        SmallButton("جوڑیں") { printer.pair(d.address)?.let { Msg.err(it) } ?: Msg.ok("جوڑنے کی درخواست بھیج دی گئی۔ PIN مانگے تو 0000 یا 1234 لکھیں"); refresh++ }
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptDesignerScreen() {
    val ctx = LocalContext.current
    val repo = LocalRepo.current
    val sync = LocalSync.current
    val settings by repo.settings.state.collectAsState()
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var sampleKind by remember { mutableStateOf("TEST") }
    val latestOrder by load { orderRows().firstOrNull { !it.order.isDraft }?.order?.id }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: throw Exception("تصویر نہیں کھل سکی")
                val scale = minOf(1f, 384f / src.width, 200f / src.height)
                val b = Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
                FileOutputStream(File(ctx.filesDir, "logo.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                repo.settings.update { it.copy(logoFile = "logo.png", showLogo = true) }
                sync?.pushLogo()
                Msg.ok("لوگو محفوظ ہو گیا")
            } catch (e: Exception) { Msg.err("لوگو محفوظ نہیں ہو سکا: " + e.message) }
        }
    }
    LaunchedEffect(settings, sampleKind, latestOrder) {
        try {
            val d = if (sampleKind == ReceiptKind.BOOKING && latestOrder != null) ReceiptBuilder.build(repo, ReceiptKind.BOOKING, latestOrder!!)
            else ReceiptBuilder.test(settings)
            preview = withContext(Dispatchers.Default) { ReceiptRenderer.render(ctx, d, settings) }
        } catch (e: Exception) { preview = null }
    }
    fun up(f: (AppSettings) -> AppSettings) = repo.settings.update(f)

    Page("رسید ڈیزائنر") { pad ->
        FormColumn(pad) {
            SectionCard("دکان کی معلومات (رسید کے اوپر)") {
                Input("دکان کا نام", settings.shopName, { v -> up { it.copy(shopName = v) } })
                Input("رسید کی سرخی (مثلاً جینٹس اینڈ لیڈیز ٹیلرز)", settings.receiptHeading, { v -> up { it.copy(receiptHeading = v) } })
                Input("موبائل نمبر", settings.shopPhone, { v -> up { it.copy(shopPhone = v) } }, keyboard = KeyboardType.Phone)
                Input("پتہ", settings.shopAddress, { v -> up { it.copy(shopAddress = v) } }, singleLine = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("لوگو منتخب کریں", Modifier.weight(1f)) { logoPicker.launch("image/*") }
                    if (settings.logoFile.isNotBlank()) SmallButton("لوگو ہٹائیں", Modifier.weight(1f)) { up { it.copy(logoFile = "") }; File(ctx.filesDir, "logo.png").delete(); sync?.pushLogo() }
                }
                SwitchRow("لوگو دکھائیں", settings.showLogo) { v -> up { it.copy(showLogo = v) } }
            }
            SectionCard("نیچے کا حصہ") {
                Input("فوٹر پیغام", settings.footer, { v -> up { it.copy(footer = v) } })
                Input("شرائط و ضوابط", settings.terms, { v -> up { it.copy(terms = v) } }, singleLine = false)
                Input("ڈیلیوری کی شرائط", settings.deliveryTerms, { v -> up { it.copy(deliveryTerms = v) } }, singleLine = false)
                SwitchRow("شرائط دکھائیں", settings.showTerms) { v -> up { it.copy(showTerms = v) } }
                SwitchRow("دستخط کی لائنیں", settings.showSignature) { v -> up { it.copy(showSignature = v) } }
            }
            SectionCard("اختیاری خانے") {
                SwitchRow("ولدیت دکھائیں", settings.showFatherName) { v -> up { it.copy(showFatherName = v) } }
                SwitchRow("پتہ دکھائیں", settings.showAddress) { v -> up { it.copy(showAddress = v) } }
                SwitchRow("بکنگ رسید پر ناپ بھی دکھائیں", settings.showMeasurementsOnBooking) { v -> up { it.copy(showMeasurementsOnBooking = v) } }
                Select("کاپی کی قسم", listOf("کسٹمر کاپی", "دکان کاپی", "اصل", ""), settings.copyType, { it.ifBlank { "کچھ نہ لکھیں" } }, { v -> up { it.copy(copyType = v) } })
                SwitchRow("ہمیشہ دکان کی کاپی بھی پرنٹ کریں", settings.printShopCopy) { v -> up { it.copy(printShopCopy = v) } }
            }
            SectionCard("حروف اور کاغذ") {
                Select("فونٹ", listOf("naskh", "nastaliq", "system"), settings.receiptFont,
                    { when (it) { "naskh" -> "نسخ (صاف، تجویز کردہ)"; "nastaliq" -> "نستعلیق"; else -> "فون کا فونٹ" } }, { v -> up { it.copy(receiptFont = v) } })
                Text("حروف کا سائز: " + Fmt.num(settings.receiptFontSize))
                Slider(value = settings.receiptFontSize.toFloat(), onValueChange = { v -> up { it.copy(receiptFontSize = v.toInt()) } }, valueRange = 16f..40f)
                Text("کنارے کا فاصلہ (ڈاٹس): " + Fmt.num(settings.marginDots))
                Slider(value = settings.marginDots.toFloat(), onValueChange = { v -> up { it.copy(marginDots = v.toInt()) } }, valueRange = 0f..40f)
                Text("آخر میں خالی لائنیں: " + Fmt.num(settings.feedLines))
                Slider(value = settings.feedLines.toFloat(), onValueChange = { v -> up { it.copy(feedLines = v.toInt()) } }, valueRange = 0f..8f)
                Select("کاغذ", listOf(58, 80), settings.paperWidthMm, { Fmt.num(it) + " ملی میٹر" }, { w ->
                    up { it.copy(paperWidthMm = w, printableDots = if (w == 80) 576 else 384) }
                })
            }
            SectionCard("نمونہ") {
                Select("نمونہ رسید", listOf("TEST", ReceiptKind.BOOKING), sampleKind, { if (it == "TEST") "ٹیسٹ رسید" else "آخری بکنگ رسید" }, { sampleKind = it })
                preview?.let { b ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.background(Color.White).border(1.dp, Color.LightGray).padding(4.dp)) {
                            Image(b.asImageBitmap(), "نمونہ", Modifier.width(300.dp), contentScale = ContentScale.FillWidth)
                        }
                    }
                }
            }
        }
    }
}
