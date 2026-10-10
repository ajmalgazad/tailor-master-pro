package pk.tailormaster.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pk.tailormaster.pro.data.Charge
import pk.tailormaster.pro.data.Customer
import pk.tailormaster.pro.data.DraftItem
import pk.tailormaster.pro.data.ExtraService
import pk.tailormaster.pro.data.FamilyMember
import pk.tailormaster.pro.data.Garment
import pk.tailormaster.pro.data.Gender
import pk.tailormaster.pro.data.MeasurementProfile
import pk.tailormaster.pro.data.OrderDraft
import pk.tailormaster.pro.data.OrderStatus
import pk.tailormaster.pro.data.PayMethod
import pk.tailormaster.pro.data.ServiceKind
import pk.tailormaster.pro.data.UserError
import pk.tailormaster.pro.domain.Calc
import pk.tailormaster.pro.domain.Dates
import pk.tailormaster.pro.ui.*

/** All booking form state; kept by the navigator so it survives a trip to "new measurement". */
class BookingState(initial: OrderDraft) {
    val draft: MutableState<OrderDraft> = mutableStateOf(initial)
    val rateText: SnapshotStateMap<Long, String> = mutableStateMapOf<Long, String>().apply {
        initial.items.forEach { put(it.key, Calc.moneyInput(it.rate)) }
    }
    val discountText = mutableStateOf(if (initial.discountInput == 0.0) "" else Calc.moneyInput((initial.discountInput * 100).toLong()))
    val advanceText = mutableStateOf("")
}

@Composable
fun BookingScreen(s: Screen.Booking) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val st = nav.screenState(s) {
        BookingState(s.draft ?: OrderDraft(customerId = s.customerId ?: 0, memberId = s.memberId ?: 0))
    }
    var d by st.draft
    var discountText by st.discountText
    var advanceText by st.advanceText
    var pickCustomer by remember { mutableStateOf(false) }
    var addItem by remember { mutableStateOf(false) }
    var addCharge by remember { mutableStateOf(false) }

    val customerRec by load(d.customerId) { if (d.customerId == 0L) null else customer(d.customerId) }
    val memberList by load(d.customerId) { if (d.customerId == 0L) emptyList() else members(d.customerId).filter { it.active } }
    val garmentList by load { garments().filter { it.active } }
    val profileList by load(d.customerId) { if (d.customerId == 0L) emptyList() else profilesForCustomer(d.customerId).filter { !it.archived } }
    val serviceList by load { services().filter { it.active } }
    val prevBalance by load(d.customerId) { if (d.customerId == 0L) 0L else balance(d.customerId) }

    // Default member = the customer himself
    LaunchedEffect(memberList) {
        val ms = memberList ?: return@LaunchedEffect
        if (ms.isNotEmpty() && ms.none { it.id == d.memberId }) d = d.copy(memberId = (ms.firstOrNull { it.isSelf } ?: ms.first()).id)
    }

    fun syncRates() {
        d = d.copy(items = d.items.map { it.copy(rate = Calc.parseMoney(st.rateText[it.key] ?: "") ?: it.rate) })
    }
    val discountValue = Calc.parseDouble(discountText) ?: -1.0
    val totals = Calc.totals(d.items.map { Calc.Line(it.qty, Calc.parseMoney(st.rateText[it.key] ?: "") ?: 0) }, d.charges.map { it.amount },
        d.discountIsPercent, if (d.discountIsPercent) discountValue.coerceAtLeast(0.0) else discountValue.coerceAtLeast(0.0))
    val advance = Calc.parseMoney(advanceText)
    val editing = d.editingOrderId != null

    fun save(asDraft: Boolean) = scope.act {
        syncRates()
        val disc = Calc.parseDouble(discountText) ?: throw UserError("رعایت درست نہیں")
        val adv = Calc.parseMoney(advanceText) ?: throw UserError("ایڈوانس کی رقم درست نہیں")
        if (d.items.any { Calc.parseMoney(st.rateText[it.key] ?: "") == null }) throw UserError("کسی کپڑے کا ریٹ درست نہیں")
        val id = repo.saveOrder(d.copy(isDraft = asDraft, discountInput = disc, advance = if (editing) 0 else adv))
        Msg.ok(if (asDraft) "ڈرافٹ محفوظ ہو گیا" else if (editing) "آرڈر اپ ڈیٹ ہو گیا" else "بکنگ محفوظ ہو گئی — " + Fmt.orderNo(id))
        nav.replace(Screen.OrderDetail(id))
    }

    Page(when { editing && d.isDraft -> "ڈرافٹ آرڈر"; editing -> "آرڈر میں ترمیم " + Fmt.orderNo(d.editingOrderId!!); d.rebookedFromId != null -> "دوبارہ بکنگ"; else -> "نئی بکنگ" }) { pad ->
        FormColumn(pad) {
            // ---------- Customer & member
            SectionCard("کسٹمر") {
                if (customerRec == null) {
                    BigButton("کسٹمر منتخب کریں") { pickCustomer = true }
                    SmallButton("+ نیا کسٹمر رجسٹر کریں", Modifier.fillMaxWidth()) { nav.go(Screen.CustomerEdit()) }
                } else {
                    val c = customerRec!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Text(Fmt.customerCode(c.id) + " • " + Fmt.d(c.mobile), style = MaterialTheme.typography.bodySmall)
                            if ((prevBalance ?: 0) != 0L) Text("پچھلا بقایا: " + Fmt.money(prevBalance ?: 0), color = Semantic.bad, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!editing) TextButton(onClick = { pickCustomer = true }) { Text("تبدیل") }
                    }
                    Select("آرڈر کس کے لیے (فیملی ممبر)", memberList ?: emptyList(), (memberList ?: emptyList()).firstOrNull { it.id == d.memberId },
                        { it.name + " — " + it.relation }, { d = d.copy(memberId = it.id) })
                    SmallButton("+ نیا فیملی ممبر", Modifier.fillMaxWidth()) { nav.go(Screen.MemberEdit(c.id)) }
                }
            }
            // ---------- Dates
            SectionCard("تاریخیں") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateInput("بکنگ", d.bookingDate, { d = d.copy(bookingDate = it) }, Modifier.weight(1f))
                    DateInput("ڈیلیوری", d.deliveryDate, { d = d.copy(deliveryDate = it) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 7, 10, 15).forEach { n ->
                        SmallButton(Fmt.num(n) + " دن", Modifier.weight(1f)) { d = d.copy(deliveryDate = Dates.addDays(d.bookingDate, n)) }
                    }
                }
                SwitchRow("⚡ ارجنٹ آرڈر", d.urgent) { on ->
                    d = d.copy(urgent = on)
                    val urgentSvc = (serviceList ?: emptyList()).firstOrNull { it.kind == ServiceKind.URGENT }
                    if (on && urgentSvc != null && d.charges.none { it.name == urgentSvc.name }) {
                        d = d.copy(charges = d.charges + Charge(urgentSvc.name, urgentSvc.amount))
                        Msg.ok("ارجنٹ چارجز شامل کر دیے گئے (چاہیں تو ہٹا سکتے ہیں)")
                    }
                }
            }
            // ---------- Items
            SectionCard("کپڑے") {
                if (d.items.isEmpty()) Text("ابھی کوئی کپڑا شامل نہیں", style = MaterialTheme.typography.bodyMedium)
                d.items.forEachIndexed { idx, it ->
                    ItemEditor(
                        item = it, rateText = st.rateText[it.key] ?: "",
                        onRate = { t -> st.rateText[it.key] = t },
                        profiles = (profileList ?: emptyList()).filter { p -> p.memberId == it.memberId && p.garmentId == it.garmentId },
                        onChange = { n -> d = d.copy(items = d.items.toMutableList().also { l -> l[idx] = n }) },
                        onRemove = { d = d.copy(items = d.items.filterIndexed { i, _ -> i != idx }); st.rateText.remove(it.key) },
                        onNewProfile = { nav.go(Screen.ProfileEdit(d.customerId, it.memberId, it.garmentId, returnAfterSave = true)) },
                    )
                }
                BigButton("+ کپڑا شامل کریں", enabled = customerRec != null) { addItem = true }
            }
            // ---------- Charges & discount
            SectionCard("اضافی چارجز اور رعایت") {
                d.charges.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, Modifier.weight(1f))
                        Text(Fmt.money(c.amount))
                        IconButton(onClick = { d = d.copy(charges = d.charges.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Default.Delete, "ہٹائیں") }
                    }
                }
                SmallButton("+ اضافی چارج (ارجنٹ، کڑھائی، ڈیزائن...)", Modifier.fillMaxWidth()) { addCharge = true }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Select("رعایت کی قسم", listOf(false, true), d.discountIsPercent, { if (it) "فیصد (%)" else "رقم (روپے)" },
                        { d = d.copy(discountIsPercent = it) }, Modifier.weight(1f))
                    Input("رعایت", discountText, { discountText = it }, Modifier.weight(1f), KeyboardType.Decimal,
                        error = if (discountText.isNotBlank() && (discountValue < 0 || (d.discountIsPercent && discountValue > 100))) "غلط" else null)
                }
            }
            // ---------- Totals
            SectionCard("حساب") {
                KV("کپڑوں کی رقم", Fmt.money(totals.itemsTotal))
                KV("اضافی چارجز", Fmt.money(totals.chargesTotal))
                KV("رعایت", "- " + Fmt.money(totals.discount))
                HorizontalDivider()
                KV("کل رقم", Fmt.money(totals.net), true, MaterialTheme.colorScheme.primary)
                if (!editing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoneyInput("ایڈوانس", advanceText, { advanceText = it }, Modifier.weight(1f))
                        Select("طریقہ", PayMethod.all, d.advanceMethod, { it }, { d = d.copy(advanceMethod = it) }, Modifier.weight(1f))
                    }
                    if (d.advanceMethod != PayMethod.CASH) Input("حوالہ / ٹرانزیکشن آئی ڈی", d.advanceRef, { d = d.copy(advanceRef = it) })
                    KV("بقایا", Fmt.money(totals.net - (advance ?: 0)), true, Semantic.bad)
                    if (advance != null && advance > totals.net) Text("ایڈوانس کل رقم سے زیادہ ہے", color = Semantic.bad)
                } else {
                    Text("ادائیگیاں آرڈر کی تفصیل والی اسکرین سے درج کریں۔", style = MaterialTheme.typography.bodySmall)
                }
            }
            SectionCard("ہدایات") {
                Input("خصوصی ہدایات (رسید پر چھپیں گی)", d.instructions, { d = d.copy(instructions = it) }, singleLine = false)
                Input("اندرونی نوٹس (صرف دکان کے لیے)", d.internalNotes, { d = d.copy(internalNotes = it) }, singleLine = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!editing || d.isDraft) SmallButton("ڈرافٹ محفوظ کریں", Modifier.weight(1f)) { save(true) }
                BigButton(if (editing && !d.isDraft) "تبدیلیاں محفوظ کریں" else "بکنگ کنفرم کریں", Modifier.weight(1.4f), color = Semantic.ok) {
                    save(false)
                }
            }
        }
    }

    if (pickCustomer) CustomerPicker(onDismiss = { pickCustomer = false }) {
        pickCustomer = false
        d = d.copy(customerId = it.id, memberId = 0, items = emptyList())
        st.rateText.clear()
    }

    if (addItem) AddItemDialog(
        members = memberList ?: emptyList(), defaultMember = d.memberId, garments = garmentList ?: emptyList(),
        onDismiss = { addItem = false },
    ) { member, garment, qty ->
        addItem = false
        scope.act {
            val rate = repo.resolveRate(garment.id, d.customerId, member.id)
            val profs = (profileList ?: emptyList()).filter { it.memberId == member.id && it.garmentId == garment.id }
            val prof = profs.firstOrNull { it.isDefault } ?: profs.maxByOrNull { it.date }
            val item = DraftItem(garmentId = garment.id, garmentName = garment.receiptDesc.ifBlank { garment.name }, memberId = member.id,
                memberName = member.name, qty = qty, rate = rate, profileId = prof?.id)
            st.rateText[item.key] = Calc.moneyInput(rate)
            d = d.copy(items = d.items + item)
            if (prof == null) Msg.warn("اس فرد کا ${garment.name} کا ناپ محفوظ نہیں۔ 'نیا ناپ' دبا کر ناپ لیں۔")
        }
    }

    if (addCharge) AddChargeDialog(serviceList ?: emptyList(), onDismiss = { addCharge = false }) { c ->
        addCharge = false
        d = d.copy(charges = d.charges + c)
    }
}

@Composable
private fun ItemEditor(
    item: DraftItem, rateText: String, onRate: (String) -> Unit, profiles: List<MeasurementProfile>,
    onChange: (DraftItem) -> Unit, onRemove: () -> Unit, onNewProfile: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.garmentName, style = MaterialTheme.typography.titleSmall)
                    Text("فرد: " + item.memberName + if (item.status != OrderStatus.NEW) " • " + OrderStatus.label(item.status) else "",
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "ہٹائیں") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallButton("−") { if (item.qty > 1) onChange(item.copy(qty = item.qty - 1)) }
                Text(Fmt.num(item.qty), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                SmallButton("+") { onChange(item.copy(qty = item.qty + 1)) }
                MoneyInput("ریٹ", rateText, onRate, Modifier.weight(1f))
            }
            val r = Calc.parseMoney(rateText) ?: 0
            Text("رقم: " + Fmt.num(item.qty) + " × " + Fmt.moneyPlain(r) + " = " + Fmt.money(Calc.lineTotal(item.qty, r)), style = MaterialTheme.typography.bodyMedium)
            val sel = profiles.firstOrNull { it.id == item.profileId }
            Select("ناپ", listOf<MeasurementProfile?>(null) + profiles, sel,
                { p -> p?.let { it.name + " • " + Fmt.date(it.date) + if (it.isDefault) " ★" else "" } ?: "بغیر ناپ" },
                { p -> onChange(item.copy(profileId = p?.id)) })
            if (item.existingSnapshot != null && item.profileId == item.existingProfileId)
                Text("بکنگ کے وقت کا محفوظ شدہ ناپ استعمال ہوگا", style = MaterialTheme.typography.bodySmall, color = Semantic.ok)
            SmallButton("نیا ناپ لیں", Modifier.fillMaxWidth(), onClick = onNewProfile)
        }
    }
}


@Composable
private fun AddItemDialog(
    members: List<FamilyMember>, defaultMember: Long, garments: List<Garment>,
    onDismiss: () -> Unit, onAdd: (FamilyMember, Garment, Int) -> Unit,
) {
    var member by remember { mutableStateOf(members.firstOrNull { it.id == defaultMember } ?: members.firstOrNull()) }
    var gender by remember { mutableStateOf<String?>(null) }
    var garment by remember { mutableStateOf<Garment?>(null) }
    var qty by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کپڑا شامل کریں") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Select("فرد", members, member, { it.name + " — " + it.relation }, { member = it })
                Select("گروپ", listOf<String?>(null) + Gender.all, gender, { it?.let { g -> Gender.label(g) } ?: "تمام" }, { gender = it })
                Select("کپڑا", garments.filter { gender == null || it.gender == gender }, garment, { it.name }, { garment = it })
                Input("تعداد", qty, { qty = it.filter { c -> c.isDigit() || c in '۰'..'۹' }.take(3) }, keyboard = KeyboardType.Number)
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = Calc.parseInt(qty) ?: 0
                val m = member; val g = garment
                when {
                    m == null -> Msg.err("فرد منتخب کریں")
                    g == null -> Msg.err("کپڑا منتخب کریں")
                    q <= 0 -> Msg.err("تعداد کم از کم ۱ ہونی چاہیے")
                    else -> onAdd(m, g, q)
                }
            }) { Text("شامل کریں") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("منسوخ") } },
    )
}

@Composable
private fun AddChargeDialog(services: List<ExtraService>, onDismiss: () -> Unit, onAdd: (Charge) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اضافی چارج") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Select("سروس منتخب کریں", services, null as ExtraService?, { it.name + " — " + Fmt.money(it.amount) }, {
                    name = it.name; amount = Calc.moneyInput(it.amount)
                })
                Input("نام", name, { name = it })
                MoneyInput("رقم (روپے)", amount, { amount = it })
            }
        },
        confirmButton = {
            Button(onClick = {
                val a = Calc.parseMoney(amount)
                when {
                    name.isBlank() -> Msg.err("چارج کا نام لکھیں")
                    a == null || a <= 0 -> Msg.err("درست رقم لکھیں")
                    else -> onAdd(Charge(name.trim(), a))
                }
            }) { Text("شامل کریں") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("منسوخ") } },
    )
}
