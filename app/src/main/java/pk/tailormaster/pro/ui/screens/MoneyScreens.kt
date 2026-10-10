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
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import pk.tailormaster.pro.data.Customer
import pk.tailormaster.pro.data.Expense
import pk.tailormaster.pro.data.ExpenseCategories
import pk.tailormaster.pro.data.OrderRow
import pk.tailormaster.pro.data.OrderStatus
import pk.tailormaster.pro.data.PayMethod
import pk.tailormaster.pro.data.ReceiptKind
import pk.tailormaster.pro.data.Txn
import pk.tailormaster.pro.data.TxnType
import pk.tailormaster.pro.data.UserError
import pk.tailormaster.pro.domain.Calc
import pk.tailormaster.pro.domain.Dates
import pk.tailormaster.pro.reports.ReportKind
import pk.tailormaster.pro.ui.*

@Composable
fun PaymentsScreen(customerId: Long?, orderId: Long?) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var cust by remember { mutableStateOf<Customer?>(null) }
    var order by remember { mutableStateOf<OrderRow?>(null) }
    var type by remember { mutableStateOf(TxnType.PAYMENT) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PayMethod.CASH) }
    var ref by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var picking by remember { mutableStateOf(false) }
    var from by remember { mutableLongStateOf(Dates.today()) }
    var to by remember { mutableLongStateOf(Dates.today()) }

    LaunchedEffect(customerId) { if (customerId != null) cust = repo.customer(customerId) }
    val custOrders by load(cust?.id) { cust?.let { c -> orderRows().filter { it.order.customerId == c.id && !it.order.isDraft } } ?: emptyList() }
    LaunchedEffect(custOrders) {
        if (orderId != null && order == null) {
            order = custOrders?.firstOrNull { it.order.id == orderId }
            order?.let { if (it.outstanding > 0) amount = Calc.moneyInput(it.outstanding) }
        }
    }
    val bal by load(cust?.id) { cust?.let { balance(it.id) } ?: 0L }
    val rows by load { customerRows(false) }
    val txns by load { allTxns() }

    Page("پیمنٹ اور کھاتہ") { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { TabBar(listOf("رقم وصول / درج کریں", "بقایا کسٹمرز", "تمام لین دین"), tab) { tab = it } }
            when (tab) {
                0 -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionCard("کسٹمر") {
                            Text(cust?.let { it.name + " • " + Fmt.customerCode(it.id) } ?: "منتخب نہیں", style = MaterialTheme.typography.titleSmall)
                            if (cust != null) KV("موجودہ بقایا", Fmt.money(bal ?: 0), true, if ((bal ?: 0) > 0) Semantic.bad else Semantic.ok)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallButton("کسٹمر منتخب کریں", Modifier.weight(1f)) { picking = true }
                                if (cust != null) SmallButton("کھاتہ دیکھیں", Modifier.weight(1f)) { nav.go(Screen.Ledger(cust!!.id)) }
                            }
                        }
                        if (cust != null) {
                            SectionCard("اندراج") {
                                Select("قسم", listOf(TxnType.PAYMENT, TxnType.REFUND, TxnType.ADJ_CREDIT, TxnType.ADJ_DEBIT), type, { TxnType.label(it) }, { type = it })
                                val open = (custOrders ?: emptyList()).filter { it.order.status != OrderStatus.CANCELLED || it.paid > 0 }
                                Select("آرڈر (اختیاری)", listOf<OrderRow?>(null) + open, order,
                                    { it?.let { r -> Fmt.orderNo(r.order.id) + " • بقایا " + Fmt.money(r.outstanding) } ?: "عام کھاتہ (کسی آرڈر کے بغیر)" },
                                    { order = it; if (it != null && it.outstanding > 0 && type == TxnType.PAYMENT) amount = Calc.moneyInput(it.outstanding) })
                                MoneyInput("رقم (روپے)", amount, { amount = it })
                                if (TxnType.isCash(type)) {
                                    Select("طریقہ ادائیگی", PayMethod.all, method, { it }, { method = it })
                                    Input("حوالہ / ٹرانزیکشن آئی ڈی", ref, { ref = it })
                                }
                                DateInput("تاریخ", date, { date = it })
                                Input("تفصیل", notes, { notes = it })
                                BigButton("محفوظ کریں", color = Semantic.ok) {
                                    scope.act {
                                        val c = cust ?: throw UserError("کسٹمر منتخب کریں")
                                        val a = Calc.parseMoney(amount) ?: throw UserError("رقم درست نہیں")
                                        val o = order
                                        if (type == TxnType.PAYMENT && o != null && a > o.outstanding && o.outstanding >= 0)
                                            Msg.warn("رقم آرڈر کے بقایا سے زیادہ ہے؛ اضافی رقم کسٹمر کے کھاتے میں جمع ہوگی")
                                        val keepTime = if (Dates.startOfDay(date) == Dates.today()) System.currentTimeMillis() else date
                                        val tid = repo.recordTxn(c.id, o?.order?.id, type, a, if (TxnType.isCash(type)) method else "", ref, notes, keepTime)
                                        Msg.ok("محفوظ ہو گیا — " + Fmt.txnNo(tid))
                                        nav.replace(Screen.Preview(ReceiptKind.PAYMENT, tid))
                                    }
                                }
                                Text("رسید پرنٹ کرنے سے کوئی نئی وصولی درج نہیں ہوتی۔", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                1 -> {
                    val list = (rows ?: emptyList()).filter { it.balance > 0 }.sortedByDescending { it.balance }
                    item { KV("کل بقایا", Fmt.money(list.sumOf { it.balance }), true, Semantic.bad) }
                    items(list, key = { it.customer.id }) { r ->
                        Card(Modifier.fillMaxWidth().clickable { nav.go(Screen.Ledger(r.customer.id)) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.customer.name, style = MaterialTheme.typography.titleSmall)
                                    Text(Fmt.d(r.customer.mobile), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(Fmt.money(r.balance), color = Semantic.bad, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    if (rows != null && list.isEmpty()) item { Empty("کسی کسٹمر کا بقایا نہیں") }
                }
                else -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateInput("سے", from, { from = Dates.startOfDay(it) }, Modifier.weight(1f))
                            DateInput("تک", to, { to = Dates.startOfDay(it) }, Modifier.weight(1f))
                        }
                    }
                    val names = (rows ?: emptyList()).associate { it.customer.id to it.customer.name }
                    val list = (txns ?: emptyList()).filter { it.date in from..Dates.endOfDay(to) }
                    item { KV("اس مدت میں نقد وصولی", Fmt.money(-list.filter { it.cash }.sumOf { it.signedEffect }), true, Semantic.ok) }
                    items(list, key = { it.id }) { t -> TxnRow(t, names[t.customerId] ?: "-") { nav.go(Screen.Preview(ReceiptKind.PAYMENT, t.id)) } }
                    if (txns != null && list.isEmpty()) item { Empty("اس مدت میں کوئی لین دین نہیں") }
                    item { SmallButton("روزانہ وصولی رپورٹ (PDF / CSV)", Modifier.fillMaxWidth()) { nav.go(Screen.Report(ReportKind.DAILY_COLLECTION)) } }
                }
            }
        }
    }
    if (picking) CustomerPicker(onDismiss = { picking = false }) { cust = it; order = null; picking = false }
}

@Composable
fun TxnRow(t: Txn, customerName: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (t.reversedById != null || t.type == TxnType.REVERSAL) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(TxnType.label(t.type) + " • " + Fmt.txnNo(t.id) + if (t.reversedById != null) " (منسوخ شدہ)" else "", style = MaterialTheme.typography.titleSmall)
                Text(customerName + (t.orderId?.let { " • " + Fmt.orderNo(it) } ?: "") + " • " + Fmt.dateTime(t.date), style = MaterialTheme.typography.bodySmall)
                if (t.method.isNotBlank() || t.reference.isNotBlank()) Text(t.method + (if (t.reference.isNotBlank()) " • " + t.reference else ""), style = MaterialTheme.typography.bodySmall)
            }
            Text(Fmt.money(t.amount), style = MaterialTheme.typography.titleSmall,
                color = if (t.signedEffect < 0) Semantic.ok else Semantic.bad)
        }
    }
}

@Composable
fun LedgerScreen(customerId: Long) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val gate = rememberPinGate()
    val l by load(customerId) { ledger(customerId) }
    var selected by remember { mutableStateOf<Txn?>(null) }
    var reverseOf by remember { mutableStateOf<Txn?>(null) }
    Page("کھاتہ") { pad ->
        val led = l ?: return@Page
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                SectionCard {
                    Text(led.customer.name + " • " + Fmt.customerCode(led.customer.id), style = MaterialTheme.typography.titleMedium)
                    KV("موجودہ بقایا", Fmt.money(led.balance), true, if (led.balance > 0) Semantic.bad else Semantic.ok)
                    Text("مثبت بقایا = کسٹمر کے ذمے رقم۔ منفی = کسٹمر کی پیشگی رقم دکان کے پاس۔", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("رقم وصول", Modifier.weight(1f), color = Semantic.ok) { nav.go(Screen.Payments(customerId)) }
                    BigButton("اسٹیٹمنٹ پرنٹ", Modifier.weight(1f)) { nav.go(Screen.Preview(ReceiptKind.LEDGER, customerId)) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text("تفصیل", Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                    Text("واجب", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("جمع", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("بیلنس", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
            }
            items(led.entries) { e ->
                Column(Modifier.fillMaxWidth().clickable(enabled = e.txn != null || e.orderId != null) {
                    if (e.txn != null) selected = e.txn else e.orderId?.let { nav.go(Screen.OrderDetail(it)) }
                }.padding(vertical = 6.dp, horizontal = 4.dp)) {
                    Row {
                        Column(Modifier.weight(2f)) {
                            Text(e.description, style = MaterialTheme.typography.bodyMedium)
                            Text(Fmt.date(e.date), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (e.debit > 0) Fmt.moneyPlain(e.debit) else "", Modifier.weight(1f), color = Semantic.bad)
                        Text(if (e.credit > 0) Fmt.moneyPlain(e.credit) else "", Modifier.weight(1f), color = Semantic.ok)
                        Text(Fmt.moneyPlain(e.balance), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    selected?.let { t ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(TxnType.label(t.type) + " " + Fmt.txnNo(t.id)) },
            text = {
                Column {
                    KV("رقم", Fmt.money(t.amount), true)
                    KV("تاریخ", Fmt.dateTime(t.date))
                    if (t.method.isNotBlank()) KV("طریقہ", t.method)
                    if (t.reference.isNotBlank()) KV("حوالہ", t.reference)
                    if (t.notes.isNotBlank()) Text(t.notes)
                    if (t.reversedById != null) Text("یہ اندراج منسوخ ہو چکا ہے (" + Fmt.txnNo(t.reversedById) + ")", color = Semantic.warn)
                }
            },
            confirmButton = { Button(onClick = { selected = null; nav.go(Screen.Preview(ReceiptKind.PAYMENT, t.id)) }) { Text("رسید") } },
            dismissButton = {
                if (t.reversedById == null && t.type != TxnType.REVERSAL) TextButton(onClick = { selected = null; reverseOf = t }) { Text("منسوخ (ریورس) کریں") }
                else TextButton(onClick = { selected = null }) { Text("بند کریں") }
            },
        )
    }
    reverseOf?.let { t ->
        TextPrompt("اندراج منسوخ کریں", "وجہ (لازمی)", onDismiss = { reverseOf = null }) { reason ->
            gate.require(repo.settings) {
                scope.act("اندراج منسوخ ہو گیا۔ مخالف اندراج ریکارڈ میں محفوظ ہے") {
                    if (reason.isBlank()) throw UserError("وجہ لکھنا ضروری ہے")
                    repo.reverseTxn(t.id, reason)
                }
            }
        }
    }
}

@Composable
fun ExpensesScreen() {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var month by remember { mutableLongStateOf(Dates.startOfMonth(System.currentTimeMillis())) }
    var edit by remember { mutableStateOf<Expense?>(null) }
    var voidOf by remember { mutableStateOf<Expense?>(null) }
    val all by load { expenses() }
    Page("اخراجات", floating = {
        ExtendedFloatingActionButton(onClick = { edit = Expense(category = ExpenseCategories.defaults.first(), amount = 0) },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("نیا خرچ") })
    }) { pad ->
        val list = (all ?: emptyList()).filter { it.date in month..Dates.endOfMonth(month) }
        val active = list.filter { !it.voided }
        val today = Dates.today()
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { DateInput("مہینہ", month, { month = Dates.startOfMonth(it) }) }
            item {
                SectionCard("خلاصہ") {
                    KV("آج کے اخراجات", Fmt.money((all ?: emptyList()).filter { !it.voided && it.date in today..Dates.endOfDay(today) }.sumOf { it.amount }))
                    KV("اس مہینے کے اخراجات", Fmt.money(active.sumOf { it.amount }), true, Semantic.bad)
                    active.groupBy { it.category }.forEach { (k, v) -> KV(k, Fmt.money(v.sumOf { it.amount })) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("اخراجات رپورٹ", Modifier.weight(1f)) { nav.go(Screen.Report(ReportKind.EXPENSES)) }
                        SmallButton("آمدن بمقابلہ خرچ", Modifier.weight(1f)) { nav.go(Screen.Report(ReportKind.INCOME_EXPENSE)) }
                    }
                }
            }
            items(list, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth().clickable(enabled = !e.voided) { edit = e },
                    colors = CardDefaults.cardColors(containerColor = if (e.voided) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.category + if (e.voided) " (منسوخ)" else "", style = MaterialTheme.typography.titleSmall)
                            Text(Fmt.date(e.date) + " • " + e.method + (if (e.notes.isNotBlank()) " • " + e.notes else ""), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(Fmt.money(e.amount), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (all != null && list.isEmpty()) item { Empty("اس مہینے کوئی خرچ درج نہیں") }
        }
    }
    edit?.let { e ->
        var cat by remember(e) { mutableStateOf(e.category) }
        var custom by remember(e) { mutableStateOf(if (e.category in ExpenseCategories.defaults) "" else e.category) }
        var amount by remember(e) { mutableStateOf(if (e.amount == 0L) "" else Calc.moneyInput(e.amount)) }
        var method by remember(e) { mutableStateOf(e.method) }
        var notes by remember(e) { mutableStateOf(e.notes) }
        var date by remember(e) { mutableLongStateOf(e.date) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (e.id == 0L) "نیا خرچ" else "خرچ میں ترمیم") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DateInput("تاریخ", date, { date = it })
                    Select("قسم", ExpenseCategories.defaults, if (cat in ExpenseCategories.defaults) cat else "دیگر اخراجات", { it }, { cat = it })
                    if (cat == "دیگر اخراجات" || cat !in ExpenseCategories.defaults) Input("قسم کا نام (اختیاری)", custom, { custom = it })
                    MoneyInput("رقم (روپے)", amount, { amount = it })
                    Select("طریقہ", PayMethod.all, method, { it }, { method = it })
                    Input("تفصیل", notes, { notes = it })
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.act("خرچ محفوظ ہو گیا") {
                        val a = Calc.parseMoney(amount) ?: throw UserError("رقم درست نہیں")
                        val c = if ((cat == "دیگر اخراجات" || cat !in ExpenseCategories.defaults) && custom.isNotBlank()) custom.trim() else cat
                        repo.saveExpense(e.copy(category = c, amount = a, method = method, notes = notes, date = date))
                        edit = null
                    }
                }) { Text("محفوظ") }
            },
            dismissButton = {
                Row {
                    if (e.id != 0L) TextButton(onClick = { voidOf = e; edit = null }) { Text("منسوخ کریں", color = Semantic.bad) }
                    TextButton(onClick = { edit = null }) { Text("بند") }
                }
            },
        )
    }
    voidOf?.let { e ->
        TextPrompt("خرچ منسوخ کریں", "وجہ", onDismiss = { voidOf = null }) { r ->
            scope.act("خرچ منسوخ ہو گیا (ریکارڈ محفوظ ہے)") { repo.voidExpense(e, r.ifBlank { "منسوخ" }) }
        }
    }
}
