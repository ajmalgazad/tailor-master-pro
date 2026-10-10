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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pk.tailormaster.pro.data.OrderRow
import pk.tailormaster.pro.data.OrderStatus
import pk.tailormaster.pro.data.PayMethod
import pk.tailormaster.pro.data.ReceiptKind
import pk.tailormaster.pro.data.TxnType
import pk.tailormaster.pro.data.UserError
import pk.tailormaster.pro.data.decodeCharges
import pk.tailormaster.pro.data.decodeIds
import pk.tailormaster.pro.data.decodeSnapshot
import pk.tailormaster.pro.domain.Calc
import pk.tailormaster.pro.domain.Dates
import pk.tailormaster.pro.domain.Digits
import pk.tailormaster.pro.ui.*

private val quickFilters = listOf(
    "all" to "تمام", "today" to "آج کی بکنگ", "due_today" to "آج ڈیلیوری", "open" to "جاری",
    "ready" to "تیار", "overdue" to "تاخیر", "urgent" to "ارجنٹ", "draft" to "ڈرافٹ", "delivered" to "ڈیلیور شدہ", "cancelled" to "منسوخ",
)

private fun matchesOrder(r: OrderRow, q: String): Boolean {
    if (q.isBlank()) return true
    val t = Digits.toLatin(q.trim()).lowercase()
    val no = pk.tailormaster.pro.data.DeviceIds.code("B", r.order.id, 5).lowercase()
    return no.contains(t) || pk.tailormaster.pro.data.DeviceIds.parse(t) == r.order.id || r.customerName.lowercase().contains(t) ||
        r.customerMobile.contains(t) || r.memberName.contains(t)
}

@Composable
fun OrdersScreen(initialFilter: String?) {
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    var quick by remember { mutableStateOf(initialFilter ?: "all") }
    var status by remember { mutableStateOf<String?>(null) }
    var useDates by remember { mutableStateOf(false) }
    var byDelivery by remember { mutableStateOf(false) }
    var from by remember { mutableLongStateOf(Dates.startOfMonth(System.currentTimeMillis())) }
    var to by remember { mutableLongStateOf(Dates.today()) }
    val rows by load { orderRows() }
    Page("آرڈر مینجمنٹ") { pad ->
        val today = Dates.today()
        val list = (rows ?: emptyList()).filter { r ->
            val o = r.order
            val qOk = when (quick) {
                "today" -> !o.isDraft && o.bookingDate in today..Dates.endOfDay(today)
                "due_today" -> !o.isDraft && o.deliveryDate in today..Dates.endOfDay(today) && o.status != OrderStatus.CANCELLED && o.status != OrderStatus.DELIVERED
                "open" -> !o.isDraft && o.status in OrderStatus.open
                "ready" -> !o.isDraft && o.status == OrderStatus.READY
                "overdue" -> r.overdue
                "urgent" -> o.urgent && o.status != OrderStatus.DELIVERED && o.status != OrderStatus.CANCELLED
                "draft" -> o.isDraft
                "delivered" -> o.status == OrderStatus.DELIVERED
                "cancelled" -> o.status == OrderStatus.CANCELLED
                else -> true
            }
            val d = if (byDelivery) o.deliveryDate else o.bookingDate
            qOk && (status == null || o.status == status) && (!useDates || d in Dates.startOfDay(from)..Dates.endOfDay(to)) && matchesOrder(r, q)
        }.sortedWith(compareByDescending<OrderRow> { it.order.urgent && it.order.status != OrderStatus.DELIVERED }.thenByDescending { it.order.id })
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SearchBox(q, { q = it }, "آرڈر نمبر، کسٹمر یا موبائل") }
            item { TabBar(quickFilters.map { it.second }, quickFilters.indexOfFirst { it.first == quick }.coerceAtLeast(0)) { quick = quickFilters[it].first } }
            item { Select("اسٹیٹس", listOf<String?>(null) + OrderStatus.all, status, { it?.let { s -> OrderStatus.label(s) } ?: "تمام اسٹیٹس" }, { status = it }) }
            item {
                SwitchRow("تاریخ سے فلٹر", useDates) { useDates = it }
                if (useDates) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Select("تاریخ کی قسم", listOf(false, true), byDelivery, { if (it) "ڈیلیوری تاریخ" else "بکنگ تاریخ" }, { byDelivery = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateInput("سے", from, { from = it }, Modifier.weight(1f))
                            DateInput("تک", to, { to = it }, Modifier.weight(1f))
                        }
                    }
                }
            }
            item { Text("کل: " + Fmt.num(list.size), style = MaterialTheme.typography.bodySmall) }
            items(list, key = { it.order.id }) { r -> OrderRowCard(r) { nav.go(Screen.OrderDetail(r.order.id)) } }
            if (rows != null && list.isEmpty()) item { Empty("کوئی آرڈر نہیں ملا") }
        }
    }
}

@Composable
fun OrderDetailScreen(id: Long) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val f by load(id) { orderFull(id) }
    var cancelAsk by remember { mutableStateOf(false) }
    var notesEdit by remember { mutableStateOf(false) }
    var statusAsk by remember { mutableStateOf<String?>(null) }

    Page("آرڈر " + Fmt.orderNo(id)) { pad ->
        val full = f ?: return@Page
        val o = full.order
        val anyDelivered = full.items.any { it.delivered }
        val closed = o.status == OrderStatus.CANCELLED || o.status == OrderStatus.DELIVERED
        FormColumn(pad) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.orderNo(o.id) + if (o.urgent) "  ⚡ارجنٹ" else "", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Chip(if (o.isDraft) "ڈرافٹ" else OrderStatus.label(o.status), if (o.isDraft) Semantic.warn else statusColor(o.status))
                }
                Text(full.customer.name + " (" + Fmt.customerCode(full.customer.id) + ")", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.clickable { nav.go(Screen.CustomerProfile(full.customer.id)) })
                full.member?.let { KV("فیملی ممبر", it.name + " — " + it.relation) }
                KV("موبائل", Fmt.d(full.customer.mobile))
                KV("بکنگ", Fmt.date(o.bookingDate))
                KV("ڈیلیوری", Fmt.date(o.deliveryDate), true,
                    if (!closed && o.deliveryDate < Dates.today()) Semantic.bad else androidx.compose.ui.graphics.Color.Unspecified)
                o.rebookedFromId?.let { KV("دوبارہ بکنگ از", Fmt.orderNo(it)) }
                if (o.instructions.isNotBlank()) Text("ہدایات: " + o.instructions)
            }
            if (o.isDraft) {
                BigButton("ڈرافٹ کو کنفرم کریں / ترمیم", color = Semantic.ok) {
                    scope.act { nav.go(Screen.Booking(draft = repo.buildDraftFromOrder(id, false))) }
                }
            }
            SectionCard("کپڑے") {
                full.items.forEach { it ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(it.garmentName + " × " + Fmt.num(it.qty), style = MaterialTheme.typography.titleSmall)
                                Text("فرد: " + it.memberName + " • " + Fmt.moneyPlain(it.rate) + " = " + Fmt.money(it.total), style = MaterialTheme.typography.bodySmall)
                                val snap = decodeSnapshot(it.measurementJson)
                                Text(if (snap == null) "ناپ منسلک نہیں" else "ناپ: " + snap.profileName + " (" + Fmt.date(snap.date) + ")",
                                    style = MaterialTheme.typography.bodySmall, color = if (snap == null) Semantic.warn else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Chip(OrderStatus.label(it.status), statusColor(it.status))
                        }
                        if (!it.delivered && !closed && !o.isDraft) {
                            Select("اس کپڑے کا اسٹیٹس", OrderStatus.workflow.filter { s -> s != OrderStatus.DELIVERED }, it.status, { s -> OrderStatus.label(s) }, { s ->
                                scope.act("اسٹیٹس تبدیل") { repo.setItemStatus(it.id, s) }
                            })
                        }
                        if (it.delivered) Text("ڈیلیور: " + Fmt.dateTime(it.deliveredAt), style = MaterialTheme.typography.bodySmall, color = Semantic.ok)
                        HorizontalDivider()
                    }
                }
            }
            SectionCard("حساب") {
                KV("کپڑوں کی رقم", Fmt.money(o.itemsTotal))
                decodeCharges(o.chargesJson).forEach { KV(it.name, Fmt.money(it.amount)) }
                if (o.discountAmount > 0) KV("رعایت", "- " + Fmt.money(o.discountAmount))
                KV("کل رقم", Fmt.money(o.netAmount), true)
                KV("وصول شدہ", Fmt.money(full.paid), color = Semantic.ok)
                KV("بقایا", Fmt.money(full.outstanding), true, if (full.outstanding > 0) Semantic.bad else Semantic.ok)
                if (o.status == OrderStatus.CANCELLED) Text("منسوخ آرڈر کی رقم کھاتے میں شامل نہیں۔" + if (full.paid > 0) " وصول شدہ رقم کسٹمر کے کھاتے میں جمع ہے، چاہیں تو ریفنڈ درج کریں۔" else "",
                    color = Semantic.warn, style = MaterialTheme.typography.bodySmall)
                KV("کسٹمر کا کل بقایا", Fmt.money(full.customerBalance))
            }
            if (!o.isDraft) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("رقم وصول کریں", Modifier.weight(1f), enabled = o.status != OrderStatus.CANCELLED) { nav.go(Screen.Payments(full.customer.id, id)) }
                    BigButton("ڈیلیوری", Modifier.weight(1f), enabled = !closed, color = Semantic.ok) { nav.go(Screen.Deliver(id)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("بکنگ رسید", Modifier.weight(1f)) { nav.go(Screen.Preview(ReceiptKind.BOOKING, id)) }
                SmallButton("ناپ کی پرچی", Modifier.weight(1f)) { nav.go(Screen.Preview(ReceiptKind.MEASUREMENT, id)) }
            }
            if (!o.isDraft && !closed) {
                SectionCard("آرڈر کا اسٹیٹس") {
                    Select("نیا اسٹیٹس (تمام باقی کپڑوں پر)", OrderStatus.open + OrderStatus.READY, o.status, { OrderStatus.label(it) }, { statusAsk = it })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("ترمیم", Modifier.weight(1f), enabled = !anyDelivered && o.status != OrderStatus.CANCELLED) {
                    scope.act { nav.go(Screen.Booking(draft = repo.buildDraftFromOrder(id, false))) }
                }
                SmallButton("دوبارہ بکنگ", Modifier.weight(1f)) { scope.act { nav.go(Screen.Booking(draft = repo.buildDraftFromOrder(id, true))) } }
                SmallButton("منسوخ", Modifier.weight(1f), enabled = !anyDelivered && o.status != OrderStatus.CANCELLED) { cancelAsk = true }
            }
            SectionCard("اندرونی نوٹس (ورکشاپ)") {
                Text(o.internalNotes.ifBlank { "کوئی نوٹ نہیں" })
                SmallButton("نوٹ لکھیں / تبدیل کریں") { notesEdit = true }
            }
            if (full.txns.isNotEmpty()) {
                SectionCard("ادائیگیاں") {
                    full.txns.forEach { t ->
                        Row(Modifier.fillMaxWidth().clickable { nav.go(Screen.Preview(ReceiptKind.PAYMENT, t.id)) }.padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(TxnType.label(t.type) + " • " + Fmt.txnNo(t.id) + if (t.reversedById != null) " (منسوخ شدہ)" else "", style = MaterialTheme.typography.bodyMedium)
                                Text(Fmt.dateTime(t.date) + " • " + t.method, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(Fmt.money(t.amount), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            if (full.deliveries.isNotEmpty()) {
                SectionCard("ڈیلیوریاں") {
                    full.deliveries.forEach { dl ->
                        Row(Modifier.fillMaxWidth().clickable { nav.go(Screen.Preview(ReceiptKind.DELIVERY, dl.id)) }.padding(vertical = 4.dp)) {
                            Text(Fmt.deliveryNo(dl.id) + " • " + Fmt.dateTime(dl.date), Modifier.weight(1f))
                            Text(Fmt.num(decodeIds(dl.itemIdsJson).size) + " آئٹم")
                        }
                    }
                }
            }
            SectionCard("اسٹیٹس ہسٹری") {
                full.log.forEach { l ->
                    val itemName = l.itemId?.let { iid -> full.items.firstOrNull { it.id == iid }?.let { " (" + it.garmentName + ")" } } ?: ""
                    Text(Fmt.dateTime(l.at) + ": " + (if (l.fromStatus.isNotBlank() && l.fromStatus != l.toStatus) OrderStatus.label(l.fromStatus) + " ← " else "") +
                        OrderStatus.label(l.toStatus) + itemName + (if (l.note.isNotBlank()) " — " + l.note else ""), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    statusAsk?.let { st ->
        Confirm("اسٹیٹس تبدیل کریں؟", "آرڈر اور تمام باقی کپڑوں کا اسٹیٹس '${OrderStatus.label(st)}' ہو جائے گا۔", "تبدیل کریں",
            onDismiss = { statusAsk = null }) { scope.act("اسٹیٹس تبدیل ہو گیا") { repo.setOrderStatus(id, st, true) } }
    }
    if (cancelAsk) TextPrompt("آرڈر منسوخ کریں", "منسوخی کی وجہ", onDismiss = { cancelAsk = false }) { reason ->
        scope.act("آرڈر منسوخ ہو گیا") { repo.cancelOrder(id, reason.ifBlank { "منسوخ" }) }
    }
    if (notesEdit) TextPrompt("اندرونی نوٹس", "نوٹ", f?.order?.internalNotes ?: "", onDismiss = { notesEdit = false }) { t ->
        scope.act("نوٹ محفوظ") { repo.saveInternalNotes(id, t) }
    }
}

@Composable
fun DeliveriesScreen() {
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    val rows by load { orderRows() }
    val recent by load { allDeliveries().take(100) }
    Page("ڈیلیوری مینجمنٹ") { pad ->
        val all = rows ?: emptyList()
        val pending = all.filter { !it.order.isDraft && it.order.status != OrderStatus.DELIVERED && it.order.status != OrderStatus.CANCELLED && matchesOrder(it, q) }
            .sortedWith(compareByDescending<OrderRow> { it.order.status == OrderStatus.READY }.thenBy { it.order.deliveryDate })
        val byId = all.associateBy { it.order.id }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { TabBar(listOf("ڈیلیوری کے لیے (${Fmt.num(pending.size)})", "ہو چکی ڈیلیوریاں"), tab) { tab = it } }
            if (tab == 0) {
                item { SearchBox(q, { q = it }, "آرڈر نمبر، کسٹمر یا موبائل") }
                items(pending, key = { it.order.id }) { r ->
                    Column {
                        OrderRowCard(r) { nav.go(Screen.OrderDetail(r.order.id)) }
                        SmallButton("ڈیلیور کریں", Modifier.fillMaxWidth()) { nav.go(Screen.Deliver(r.order.id)) }
                    }
                }
                if (rows != null && pending.isEmpty()) item { Empty("کوئی آرڈر ڈیلیوری کے لیے باقی نہیں") }
            } else {
                items(recent ?: emptyList(), key = { "d" + it.id }) { d ->
                    val r = byId[d.orderId]
                    Card(Modifier.fillMaxWidth().clickable { nav.go(Screen.Preview(ReceiptKind.DELIVERY, d.id)) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(Fmt.deliveryNo(d.id) + " • " + Fmt.orderNo(d.orderId), style = MaterialTheme.typography.titleSmall)
                            Text((r?.customerName ?: "-") + " • " + Fmt.dateTime(d.date) + " • " + Fmt.num(decodeIds(d.itemIdsJson).size) + " آئٹم",
                                style = MaterialTheme.typography.bodySmall)
                            Text("رسید دوبارہ پرنٹ کرنے کے لیے دبائیں", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeliverScreen(orderId: Long) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val f by load(orderId) { orderFull(orderId) }
    val selected = remember { mutableStateListOf<Long>() }
    var initDone by remember { mutableStateOf(false) }
    var payText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PayMethod.CASH) }
    var notes by remember { mutableStateOf("") }
    var askForce by remember { mutableStateOf(false) }

    fun submit(force: Boolean) = scope.act {
        val pay = Calc.parseMoney(payText) ?: throw UserError("رقم درست نہیں")
        val did = repo.deliver(orderId, selected.toList(), notes, pay, method, force)
        Msg.ok("ڈیلیوری محفوظ ہو گئی")
        nav.replace(Screen.Preview(ReceiptKind.DELIVERY, did))
    }

    androidx.compose.runtime.LaunchedEffect(f) {
        val full = f ?: return@LaunchedEffect
        if (!initDone) {
            val pend = full.items.filter { !it.delivered }
            selected.clear(); selected.addAll(pend.filter { it.status == OrderStatus.READY }.map { it.id })
            if (selected.isEmpty()) selected.addAll(pend.map { it.id })
            payText = if (full.outstanding > 0) Calc.moneyInput(full.outstanding) else ""
            initDone = true
        }
    }

    Page("ڈیلیوری " + Fmt.orderNo(orderId)) { pad ->
        val full = f ?: return@Page
        val pending = full.items.filter { !it.delivered }
        FormColumn(pad) {
            SectionCard {
                KV("کسٹمر", full.customer.name, true)
                full.member?.let { KV("فیملی ممبر", it.name) }
                KV("ڈیلیوری تاریخ", Fmt.date(full.order.deliveryDate))
            }
            SectionCard("کون سے کپڑے دیے جا رہے ہیں؟") {
                pending.forEach { it ->
                    CheckRow(it.garmentName + " × " + Fmt.num(it.qty) + " — " + it.memberName + " (" + OrderStatus.label(it.status) + ")", it.id in selected) { on ->
                        if (on) selected.add(it.id) else selected.remove(it.id)
                    }
                }
                full.items.filter { it.delivered }.forEach {
                    Text("✓ " + it.garmentName + " — پہلے ڈیلیور ہو چکا", style = MaterialTheme.typography.bodySmall, color = Semantic.ok)
                }
                val remaining = pending.count { it.id !in selected }
                if (remaining > 0) Text("ابھی ${Fmt.num(remaining)} کپڑے باقی رہیں گے (جزوی ڈیلیوری)", color = Semantic.warn)
            }
            SectionCard("وصولی") {
                KV("کل رقم", Fmt.money(full.order.netAmount))
                KV("پہلے وصول", Fmt.money(full.paid))
                KV("بقایا", Fmt.money(full.outstanding), true, Semantic.bad)
                MoneyInput("ابھی وصول ہونے والی رقم", payText, { payText = it })
                Select("طریقہ", PayMethod.all, method, { it }, { method = it })
                Input("ڈیلیوری نوٹ", notes, { notes = it })
            }
            BigButton("ڈیلیوری محفوظ کریں", color = Semantic.ok, enabled = selected.isNotEmpty()) { submit(false) }
            if (pending.any { it.id !in selected }) {
                SmallButton("باقی کپڑے بھی مکمل مان کر پورا آرڈر بند کریں", Modifier.fillMaxWidth()) { askForce = true }
            }
        }
    }
    if (askForce) Confirm("پورا آرڈر ڈیلیور مانیں؟", "جو کپڑے منتخب نہیں، وہ بھی ڈیلیور شدہ درج ہو جائیں گے۔ صرف تب کریں جب واقعی کسٹمر کو سب کچھ مل چکا ہو۔",
        "ہاں، مکمل کریں", danger = true, onDismiss = { askForce = false }) { submit(true) }
}
