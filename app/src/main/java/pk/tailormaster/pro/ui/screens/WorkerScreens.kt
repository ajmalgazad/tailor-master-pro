package pk.tailormaster.pro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pk.tailormaster.pro.data.JobStatus
import pk.tailormaster.pro.data.OrderItem
import pk.tailormaster.pro.data.PayMethod
import pk.tailormaster.pro.data.ReceiptKind
import pk.tailormaster.pro.data.UserError
import pk.tailormaster.pro.data.WorkJob
import pk.tailormaster.pro.data.WorkTasks
import pk.tailormaster.pro.data.Worker
import pk.tailormaster.pro.data.WorkerPayType
import pk.tailormaster.pro.domain.Calc
import pk.tailormaster.pro.domain.Digits
import pk.tailormaster.pro.reports.ReportKind
import pk.tailormaster.pro.ui.*

private fun jobColor(s: String): Color = when (s) {
    JobStatus.DONE -> Semantic.ok
    JobStatus.CANCELLED -> Color(0xFF757575)
    else -> Color(0xFFB26A00)
}

/** Positive = shop owes the worker (shown as "باقی مزدوری"), negative = advance with the worker. */
private fun balanceText(b: Long): String = when {
    b > 0 -> "باقی مزدوری: " + Fmt.money(b)
    b < 0 -> "ایڈوانس (کاریگر کے ذمے): " + Fmt.money(-b)
    else -> "حساب برابر"
}

// =====================================================================================
@Composable
fun WorkersScreen() {
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    var showInactive by remember { mutableStateOf(false) }
    val rows by load(showInactive) { workers.rows(showInactive) }
    Page("کاریگر", floating = {
        ExtendedFloatingActionButton(onClick = { nav.go(Screen.WorkerEdit()) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("نیا کاریگر") })
    }) { pad ->
        val t = Digits.toLatin(q.trim()).lowercase()
        val list = (rows ?: emptyList()).filter { r ->
            t.isBlank() || r.worker.name.lowercase().contains(t) || r.worker.mobile.contains(t) || r.worker.skills.contains(t) ||
                pk.tailormaster.pro.data.DeviceIds.code("K", r.worker.id, 3).lowercase().contains(t)
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SearchBox(q, { q = it }, "نام، موبائل یا کام") }
            item { CheckRow("غیر فعال کاریگر بھی دکھائیں", showInactive) { showInactive = it } }
            item {
                val all = rows ?: emptyList()
                SectionCard("خلاصہ") {
                    KV("کاریگروں کے پاس کام", Fmt.num(all.sumOf { it.pendingJobs }))
                    KV("کل باقی مزدوری (دکان کے ذمے)", Fmt.money(all.sumOf { maxOf(0L, it.balance) }), true, Semantic.bad)
                    KV("کل ایڈوانس (کاریگروں کے ذمے)", Fmt.money(all.sumOf { maxOf(0L, -it.balance) }))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("کاریگر حساب رپورٹ", Modifier.weight(1f)) { nav.go(Screen.Report(ReportKind.WORKERS)) }
                        SmallButton("دیا گیا کام", Modifier.weight(1f)) { nav.go(Screen.Report(ReportKind.WORKER_JOBS)) }
                    }
                }
            }
            items(list, key = { it.worker.id }) { r ->
                Card(Modifier.fillMaxWidth().clickable { nav.go(Screen.WorkerDetail(r.worker.id)) },
                    colors = CardDefaults.cardColors(containerColor = if (r.worker.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.worker.name + if (!r.worker.active) " (غیر فعال)" else "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (r.pendingJobs > 0) Chip("پاس کام: " + Fmt.num(r.pendingJobs), Color(0xFFB26A00))
                        }
                        Text(Fmt.workerCode(r.worker.id) + (if (r.worker.mobile.isNotBlank()) " • " + Fmt.d(r.worker.mobile) else "") +
                            (if (r.worker.skills.isNotBlank()) " • " + r.worker.skills else ""), style = MaterialTheme.typography.bodySmall)
                        Text(balanceText(r.balance), style = MaterialTheme.typography.bodyMedium,
                            color = if (r.balance > 0) Semantic.bad else if (r.balance < 0) Semantic.warn else Semantic.ok)
                    }
                }
            }
            if (rows != null && list.isEmpty()) item { Empty("کوئی کاریگر نہیں۔ 'نیا کاریگر' دبا کر شامل کریں") }
        }
    }
}

// =====================================================================================
@Composable
fun WorkerEditScreen(id: Long?) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val existing by load(id) { id?.let { workers.worker(it) } }
    var loaded by remember { mutableStateOf(id == null) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var cnic by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    LaunchedEffect(existing) {
        val w = existing
        if (w != null && !loaded) {
            name = w.name; mobile = w.mobile; cnic = w.cnic; skills = w.skills; address = w.address; notes = w.notes; active = w.active
            loaded = true
        }
    }
    val garments by load { garments() }
    Page(if (id == null) "نیا کاریگر" else "کاریگر میں ترمیم") { pad ->
        if (!loaded) return@Page
        FormColumn(pad) {
            SectionCard {
                if (id != null) Text("کاریگر کوڈ: " + Fmt.workerCode(id), style = MaterialTheme.typography.bodySmall)
                Input("کاریگر کا نام *", name, { name = it })
                Input("موبائل نمبر", mobile, { mobile = it }, keyboard = KeyboardType.Phone)
                Input("شناختی کارڈ نمبر (CNIC)", cnic, { cnic = it }, keyboard = KeyboardType.Number)
                Input("کیا بناتا ہے (کام کی قسم)", skills, { skills = it })
                // quick-add garment names / tasks
                val chips = ((garments ?: emptyList()).filter { it.active }.map { it.name } + WorkTasks.defaults).distinct()
                Text("جلدی شامل کریں:", style = MaterialTheme.typography.labelMedium)
                chips.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { c ->
                            val on = skills.split("،", ",").map { it.trim() }.contains(c)
                            SmallButton((if (on) "✓ " else "+ ") + c, Modifier.weight(1f)) {
                                val parts = skills.split("،", ",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                                if (on) parts.remove(c) else parts.add(c)
                                skills = parts.joinToString("، ")
                            }
                        }
                        repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                    }
                }
                Input("پتہ", address, { address = it })
                Input("نوٹس", notes, { notes = it }, singleLine = false, minLines = 2)
                SwitchRow("فعال", active) { active = it }
            }
            BigButton("محفوظ کریں", color = Semantic.ok) {
                scope.act("کاریگر محفوظ ہو گیا") {
                    val base = existing ?: Worker(name = "")
                    val newId = repo.workers.saveWorker(base.copy(name = name, mobile = mobile, cnic = cnic, skills = skills,
                        address = address, notes = notes, active = active))
                    if (id == null) nav.replace(Screen.WorkerDetail(newId)) else nav.back()
                }
            }
        }
    }
}

// =====================================================================================
@Composable
fun WorkerDetailScreen(id: Long, initialTab: Int) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val w by load(id) { workers.worker(id) }
    val jobs by load(id) { workers.jobsForWorker(id) }
    val ledger by load(id) { workers.ledger(id) }
    var tab by remember { mutableIntStateOf(initialTab) }
    var payAsk by remember { mutableStateOf(false) }
    var cancelJob by remember { mutableStateOf<WorkJob?>(null) }
    var voidPay by remember { mutableStateOf<Long?>(null) }
    var deactivateAsk by remember { mutableStateOf(false) }
    val pin = rememberPinGate()

    Page(w?.name ?: "کاریگر", actions = {
        TextButton(onClick = { nav.go(Screen.WorkerEdit(id)) }) { Text("ترمیم", color = MaterialTheme.colorScheme.onPrimary) }
    }) { pad ->
        val worker = w ?: return@Page
        val l = ledger
        val all = jobs ?: emptyList()
        val pending = all.filter { it.status == JobStatus.ASSIGNED }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard {
                    KV("کوڈ", Fmt.workerCode(worker.id))
                    if (worker.mobile.isNotBlank()) KV("موبائل", Fmt.d(worker.mobile))
                    if (worker.cnic.isNotBlank()) KV("شناختی کارڈ", Fmt.d(worker.cnic))
                    if (worker.skills.isNotBlank()) KV("کام", worker.skills)
                    if (worker.address.isNotBlank()) KV("پتہ", worker.address)
                    if (worker.notes.isNotBlank()) Text("نوٹس: " + worker.notes, style = MaterialTheme.typography.bodySmall)
                    if (!worker.active) Chip("غیر فعال", Semantic.bad)
                }
            }
            item {
                SectionCard("حساب") {
                    KV("مکمل کام کی کمائی", Fmt.money(l?.entries?.sumOf { it.earned } ?: 0))
                    KV("ادائیگی + ایڈوانس", Fmt.money(l?.entries?.sumOf { it.paid } ?: 0))
                    KV("ابھی پاس کام کی مزدوری", Fmt.money(pending.sumOf { it.amount }))
                    val b = l?.balance ?: 0
                    KV(if (b >= 0) "باقی مزدوری (دکان کے ذمے)" else "ایڈوانس (کاریگر کے ذمے)", Fmt.money(kotlin.math.abs(b)), true,
                        if (b > 0) Semantic.bad else if (b < 0) Semantic.warn else Semantic.ok)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton("ادائیگی / ایڈوانس دیں", Modifier.weight(1f), color = Semantic.ok, enabled = worker.active) { payAsk = true }
                        SmallButton("حساب پرنٹ", Modifier.weight(1f)) { nav.go(Screen.Preview(ReceiptKind.WORKER_LEDGER, id)) }
                    }
                    SmallButton(if (worker.active) "غیر فعال کریں" else "فعال کریں", Modifier.fillMaxWidth()) {
                        if (worker.active) deactivateAsk = true else scope.act("کاریگر فعال ہو گیا") { repo.workers.setActive(id, true) }
                    }
                }
            }
            item {
                TabRow(selectedTabIndex = tab) {
                    listOf("پاس کام (${Fmt.num(pending.size)})", "مکمل کام", "حساب").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
            }
            when (tab) {
                0 -> {
                    items(pending, key = { it.id }) { j -> JobCard(j, showWorker = null, onCancel = { cancelJob = j }) }
                    if (jobs != null && pending.isEmpty()) item { Empty("اس کاریگر کے پاس ابھی کوئی کام نہیں۔ آرڈر کھول کر کپڑا دیں۔") }
                }
                1 -> {
                    val done = all.filter { it.status != JobStatus.ASSIGNED }
                    items(done, key = { it.id }) { j -> JobCard(j, showWorker = null, onCancel = null) }
                    if (jobs != null && done.isEmpty()) item { Empty("ابھی کوئی مکمل کام نہیں") }
                }
                else -> {
                    val entries = (l?.entries ?: emptyList()).reversed()
                    items(entries.size) { i ->
                        val e = entries[i]
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.description, style = MaterialTheme.typography.bodyMedium)
                                    Text(Fmt.date(e.date) + " • بیلنس " + Fmt.moneyPlain(e.balance), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(if (e.earned > 0) "+" + Fmt.moneyPlain(e.earned) else if (e.paid > 0) "−" + Fmt.moneyPlain(e.paid) else "0",
                                    color = if (e.earned > 0) Semantic.ok else Semantic.bad, style = MaterialTheme.typography.titleSmall)
                            }
                            e.payment?.let { p ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SmallButton("پرچی", Modifier.weight(1f)) { nav.go(Screen.Preview(ReceiptKind.WORKER_PAYMENT, p.id)) }
                                    if (!p.voided) SmallButton("منسوخ کریں", Modifier.weight(1f)) { voidPay = p.id }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    if (l != null && entries.isEmpty()) item { Empty("ابھی کوئی اندراج نہیں") }
                }
            }
        }
    }
    if (payAsk) WorkerPayDialog(id, ledger?.balance ?: 0, onDismiss = { payAsk = false }) { pid -> nav.go(Screen.Preview(ReceiptKind.WORKER_PAYMENT, pid)) }
    cancelJob?.let { j ->
        TextPrompt("کام منسوخ کریں", "وجہ (مثلاً کپڑا واپس لے لیا)", onDismiss = { cancelJob = null }) { r ->
            scope.act("کام منسوخ ہو گیا") { repo.workers.cancel(j.id, r.ifBlank { "منسوخ" }) }
        }
    }
    voidPay?.let { pid ->
        TextPrompt("ادائیگی منسوخ کریں", "وجہ (لازمی)", onDismiss = { voidPay = null }) { r ->
            if (r.isBlank()) Msg.err("وجہ لکھنا لازمی ہے")
            else pin.require(repo.settings) { scope.act("اندراج منسوخ ہو گیا (ریکارڈ محفوظ ہے)") { repo.workers.voidPayment(pid, r) } }
        }
    }
    if (deactivateAsk) Confirm("کاریگر غیر فعال کریں؟", "اس کا سارا حساب محفوظ رہے گا، صرف نیا کام نہیں دیا جا سکے گا۔", onDismiss = { deactivateAsk = false }) {
        scope.act("کاریگر غیر فعال ہو گیا") { repo.workers.setActive(id, false) }
    }
}

/** One job line with its actions (used in worker screen and order screen). */
@Composable
fun JobCard(j: WorkJob, showWorker: String?, onCancel: (() -> Unit)?) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((showWorker?.let { "$it — " } ?: "") + j.task + ": " + j.garmentName + " × " + Fmt.num(j.qty),
                        style = MaterialTheme.typography.titleSmall)
                    Text(Fmt.jobNo(j.id) + " • " + Fmt.orderNo(j.orderId) + (if (j.memberName.isNotBlank()) " • " + j.memberName else ""),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { nav.go(Screen.OrderDetail(j.orderId)) })
                    Text("دیا: " + Fmt.date(j.assignedAt) + " • واپسی: " + Fmt.date(j.dueDate) +
                        (j.doneAt?.let { " • مکمل: " + Fmt.date(it) } ?: ""), style = MaterialTheme.typography.bodySmall)
                    Text("مزدوری: " + Fmt.moneyPlain(j.wage) + " × " + Fmt.num(j.qty) + " = " + Fmt.money(j.amount), style = MaterialTheme.typography.bodySmall)
                    if (j.notes.isNotBlank()) Text("نوٹ: " + j.notes, style = MaterialTheme.typography.bodySmall)
                }
                Chip(JobStatus.label(j.status), jobColor(j.status))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("پرچی", Modifier.weight(1f)) { nav.go(Screen.Preview(ReceiptKind.WORKER_SLIP, j.id)) }
                when (j.status) {
                    JobStatus.ASSIGNED -> {
                        SmallButton("✓ واپس آ گیا", Modifier.weight(1f)) { scope.act("کام مکمل — مزدوری حساب میں شامل") { repo.workers.complete(j.id) } }
                        if (onCancel != null) SmallButton("منسوخ", Modifier.weight(1f)) { onCancel() }
                    }
                    JobStatus.DONE -> SmallButton("واپس کھولیں", Modifier.weight(1f)) { scope.act("کام دوبارہ کھل گیا") { repo.workers.reopen(j.id) } }
                }
            }
        }
    }
}

@Composable
fun WorkerPayDialog(workerId: Long, balance: Long, onDismiss: () -> Unit, onSaved: (Long) -> Unit) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(if (balance > 0) WorkerPayType.PAYMENT else WorkerPayType.ADVANCE) }
    var amount by remember { mutableStateOf(if (balance > 0) Calc.moneyInput(balance) else "") }
    var method by remember { mutableStateOf(PayMethod.CASH) }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کاریگر کو ادائیگی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(balanceText(balance), style = MaterialTheme.typography.bodyMedium)
                Select("قسم", WorkerPayType.all, type, { WorkerPayType.label(it) }, { type = it })
                MoneyInput("رقم (روپے)", amount, { amount = it })
                Select("طریقہ", PayMethod.all, method, { it }, { method = it })
                DateInput("تاریخ", date, { date = it })
                Input("تفصیل", notes, { notes = it })
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.act("ادائیگی محفوظ ہو گئی") {
                    val a = Calc.parseMoney(amount) ?: throw UserError("رقم درست نہیں")
                    val id = repo.workers.pay(workerId, type, a, method, notes, date)
                    onDismiss(); onSaved(id)
                }
            }) { Text("محفوظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("منسوخ") } },
    )
}

/** "Give to kaarigar" for one garment of an order. */
@Composable
fun AssignJobDialog(item: OrderItem, onDismiss: () -> Unit, onSaved: (Long) -> Unit) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val rows by load { workers.rows(false) }
    var workerId by remember { mutableStateOf<Long?>(null) }
    var task by remember { mutableStateOf(WorkTasks.defaults.first()) }
    var qty by remember { mutableStateOf(item.qty.toString()) }
    var wage by remember { mutableStateOf("") }
    var wageTouched by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var setStitching by remember { mutableStateOf(true) }
    // fill the wage from the last time this worker did the same garment and task
    LaunchedEffect(workerId, task) {
        val w = workerId ?: return@LaunchedEffect
        if (!wageTouched) repo.workers.lastWage(w, item.garmentName, task)?.let { wage = Calc.moneyInput(it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کاریگر کو دیں: " + item.garmentName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val list = rows ?: emptyList()
                if (rows != null && list.isEmpty()) Text("پہلے کاریگر شامل کریں (مین مینو ← کاریگر)", color = Semantic.bad)
                Select("کاریگر", list.map { it.worker.id }, workerId, { wid -> list.firstOrNull { it.worker.id == wid }?.let { r ->
                    r.worker.name + if (r.pendingJobs > 0) " (پاس کام: ${Fmt.num(r.pendingJobs)})" else "" } ?: "-" }, { workerId = it })
                Select("کام", WorkTasks.defaults, task, { it }, { task = it })
                if (item.qty > 1) Input("تعداد (کل ${Fmt.num(item.qty)})", qty, { qty = it.filter { c -> c.isDigit() } }, keyboard = KeyboardType.Number)
                MoneyInput("مزدوری فی پیس (روپے)", wage, { wage = it; wageTouched = true })
                Input("کاریگر کے لیے نوٹ", notes, { notes = it })
                if (task == "سلائی" && item.status in listOf(pk.tailormaster.pro.data.OrderStatus.NEW, pk.tailormaster.pro.data.OrderStatus.MEASURED, pk.tailormaster.pro.data.OrderStatus.CUTTING))
                    CheckRow("کپڑے کا اسٹیٹس 'سلائی جاری' کریں", setStitching) { setStitching = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.act("کام کاریگر کو دے دیا گیا") {
                    val w = workerId ?: throw UserError("کاریگر منتخب کریں")
                    val q = qty.toIntOrNull() ?: throw UserError("تعداد درست نہیں")
                    val a = if (wage.isBlank()) 0L else Calc.parseMoney(wage) ?: throw UserError("مزدوری درست نہیں")
                    val id = repo.workers.assign(item.id, w, task, q, a, null, notes)
                    if (setStitching && task == "سلائی" && item.status in listOf(pk.tailormaster.pro.data.OrderStatus.NEW, pk.tailormaster.pro.data.OrderStatus.MEASURED, pk.tailormaster.pro.data.OrderStatus.CUTTING)) {
                        try { repo.setItemStatus(item.id, pk.tailormaster.pro.data.OrderStatus.STITCHING) } catch (_: UserError) { }
                    }
                    onDismiss(); onSaved(id)
                }
            }) { Text("دیں اور پرچی") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("منسوخ") } },
    )
}
