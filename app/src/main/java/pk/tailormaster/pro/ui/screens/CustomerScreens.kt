package pk.tailormaster.pro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pk.tailormaster.pro.data.Customer
import pk.tailormaster.pro.data.FamilyMember
import pk.tailormaster.pro.data.OrderStatus
import pk.tailormaster.pro.data.ReceiptKind
import pk.tailormaster.pro.domain.Calc
import pk.tailormaster.pro.ui.*

@Composable
fun CustomersScreen() {
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    var showInactive by remember { mutableStateOf(false) }
    val rows by load(showInactive) { customerRows(showInactive) }
    Page("کسٹمر ماسٹر", actions = {
        TextButton(onClick = { nav.go(Screen.Merge) }) { Text("ضم کریں", color = MaterialTheme.colorScheme.onPrimary) }
    }, floating = {
        ExtendedFloatingActionButton(onClick = { nav.go(Screen.CustomerEdit()) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("نیا کسٹمر") })
    }) { pad ->
        val list = (rows ?: emptyList()).filter { matchesCustomer(it.customer, q) }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SearchBox(q, { q = it }, "نام، موبائل، کوڈ یا پتہ سے تلاش") }
            item { SwitchRow("غیر فعال کسٹمر بھی دکھائیں", showInactive) { showInactive = it } }
            item { Text("کل: " + Fmt.num(list.size), style = MaterialTheme.typography.bodySmall) }
            items(list, key = { it.customer.id }) { r ->
                Card(Modifier.fillMaxWidth().clickable { nav.go(Screen.CustomerProfile(r.customer.id)) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.customer.name + if (!r.customer.active) " (غیر فعال)" else "", style = MaterialTheme.typography.titleSmall)
                            Text(Fmt.customerCode(r.customer.id) + " • " + Fmt.d(r.customer.mobile) +
                                (if (r.customer.area.isNotBlank()) " • " + r.customer.area else ""), style = MaterialTheme.typography.bodySmall)
                            Text("فیملی ممبرز: " + Fmt.num(r.members), style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("بقایا", style = MaterialTheme.typography.labelSmall)
                            Text(Fmt.money(r.balance), color = if (r.balance > 0) Semantic.bad else Semantic.ok, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            if (rows != null && list.isEmpty()) item { Empty("کوئی کسٹمر نہیں ملا") }
        }
    }
}

@Composable
fun CustomerEditScreen(id: Long?) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == null) }
    var c by remember { mutableStateOf(Customer(name = "")) }
    var opening by remember { mutableStateOf("") }
    var dups by remember { mutableStateOf<List<Customer>>(emptyList()) }
    LaunchedEffect(id) {
        if (id != null) repo.customer(id)?.let { c = it; opening = if (it.openingBalance == 0L) "" else Calc.moneyInput(it.openingBalance) }
        loaded = true
    }
    fun doSave() = scope.act("کسٹمر محفوظ ہو گیا") {
        val ob = Calc.parseMoney(opening) ?: throw pk.tailormaster.pro.data.UserError("ابتدائی بیلنس درست نہیں")
        val newId = repo.saveCustomer(c.copy(openingBalance = ob))
        if (id == null) nav.replace(Screen.CustomerProfile(newId)) else nav.back()
    }
    Page(if (id == null) "نیا کسٹمر" else "کسٹمر میں ترمیم") { pad ->
        if (!loaded) return@Page
        FormColumn(pad) {
            if (id != null) Text("کسٹمر کوڈ: " + Fmt.customerCode(id), style = MaterialTheme.typography.titleSmall)
            Input("کسٹمر کا نام *", c.name, { c = c.copy(name = it) })
            Input("والد کا نام", c.fatherName, { c = c.copy(fatherName = it) })
            Input("موبائل نمبر", c.mobile, { c = c.copy(mobile = it) }, keyboard = KeyboardType.Phone)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Input("واٹس ایپ نمبر", c.whatsapp, { c = c.copy(whatsapp = it) }, Modifier.weight(1f), KeyboardType.Phone)
                TextButton(onClick = { c = c.copy(whatsapp = c.mobile) }) { Text("موبائل والا") }
            }
            Input("مکمل پتہ", c.address, { c = c.copy(address = it) }, singleLine = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Input("شہر", c.city, { c = c.copy(city = it) }, Modifier.weight(1f))
                Input("علاقہ", c.area, { c = c.copy(area = it) }, Modifier.weight(1f))
            }
            Select("کسٹمر کی قسم", listOf("عام", "مستقل", "وی آئی پی", "ادارہ / اسکول", "دیگر"), c.category.ifBlank { null }, { it }, { c = c.copy(category = it) })
            DateInput("رجسٹریشن تاریخ", c.regDate, { c = c.copy(regDate = it) })
            MoneyInput("ابتدائی بیلنس (پرانا بقایا)", opening, { opening = it })
            Input("نوٹس", c.notes, { c = c.copy(notes = it) }, singleLine = false, minLines = 2)
            SwitchRow("فعال", c.active) { c = c.copy(active = it) }
            BigButton("محفوظ کریں") {
                scope.act {
                    if (c.name.isBlank()) throw pk.tailormaster.pro.data.UserError("کسٹمر کا نام لازمی ہے")
                    val d = repo.findDuplicates(c)
                    if (d.isNotEmpty()) dups = d else doSave()
                }
            }
        }
    }
    if (dups.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { dups = emptyList() },
            title = { Text("ملتا جلتا کسٹمر پہلے سے موجود ہے") },
            text = {
                Column {
                    Text("یہ کسٹمر شاید پہلے سے رجسٹرڈ ہے:")
                    dups.forEach { d ->
                        TextButton(onClick = { dups = emptyList(); nav.replace(Screen.CustomerProfile(d.id)) }) {
                            Text(d.name + " • " + Fmt.d(d.mobile) + " • " + Fmt.customerCode(d.id))
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { dups = emptyList(); doSave() }) { Text("پھر بھی نیا محفوظ کریں") } },
            dismissButton = { TextButton(onClick = { dups = emptyList() }) { Text("واپس") } },
        )
    }
}

@Composable
fun TabBar(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEachIndexed { i, t ->
            if (i == selected) Button(onClick = { onSelect(i) }, modifier = Modifier.heightIn(min = 44.dp)) { Text(t) }
            else OutlinedButton(onClick = { onSelect(i) }, modifier = Modifier.heightIn(min = 44.dp)) { Text(t) }
        }
    }
}

@Composable
fun CustomerProfileScreen(id: Long, initialTab: Int) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(initialTab) }
    val c by load(id) { customer(id) }
    val bal by load(id) { balance(id) }
    val memberList by load(id) { members(id) }
    val profileList by load(id) { profilesForCustomer(id) }
    val orderList by load(id) { orderRows().filter { it.order.customerId == id } }
    val garmentList by load { garments() }
    var confirmDeact by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }

    Page(c?.name ?: "کسٹمر", actions = {
        IconButton(onClick = { nav.go(Screen.CustomerEdit(id)) }) { Icon(Icons.Default.Edit, "ترمیم") }
    }) { pad ->
        val cu = c ?: return@Page
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard {
                    if (cu.mergedIntoId != null) Text("یہ ریکارڈ کسی دوسرے کسٹمر میں ضم ہو چکا ہے", color = Semantic.warn)
                    KV("کوڈ", Fmt.customerCode(cu.id), true)
                    if (cu.fatherName.isNotBlank()) KV("ولدیت", cu.fatherName)
                    if (cu.mobile.isNotBlank()) KV("موبائل", Fmt.d(cu.mobile))
                    if (cu.whatsapp.isNotBlank()) KV("واٹس ایپ", Fmt.d(cu.whatsapp))
                    if (cu.address.isNotBlank()) KV("پتہ", cu.address)
                    val ca = listOf(cu.area, cu.city).filter { it.isNotBlank() }.joinToString("، ")
                    if (ca.isNotBlank()) KV("علاقہ", ca)
                    if (cu.category.isNotBlank()) KV("قسم", cu.category)
                    KV("رجسٹریشن", Fmt.date(cu.regDate))
                    KV("حیثیت", if (cu.active) "فعال" else "غیر فعال", color = if (cu.active) Semantic.ok else Semantic.bad)
                    if (cu.notes.isNotBlank()) Text("نوٹس: " + cu.notes, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    KV("موجودہ بقایا", Fmt.money(bal ?: 0), true, if ((bal ?: 0) > 0) Semantic.bad else Semantic.ok)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton("نئی بکنگ", Modifier.weight(1f), color = Semantic.ok) { nav.go(Screen.Booking(customerId = id)) }
                        BigButton("رقم وصول", Modifier.weight(1f)) { nav.go(Screen.Payments(customerId = id)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("کھاتہ", Modifier.weight(1f)) { nav.go(Screen.Ledger(id)) }
                        SmallButton("پرنٹ معلومات", Modifier.weight(1f)) { nav.go(Screen.Preview(ReceiptKind.CUSTOMER, id)) }
                        SmallButton(if (cu.active) "غیر فعال کریں" else "فعال کریں", Modifier.weight(1f)) {
                            if (cu.active) confirmDeact = true else scope.act("کسٹمر فعال ہو گیا") { repo.setCustomerActive(id, true) }
                        }
                    }
                }
            }
            item { TabBar(listOf("فیملی ممبرز", "ناپ", "آرڈرز"), tab) { tab = it } }
            when (tab) {
                0 -> {
                    item { BigButton("+ نیا فیملی ممبر") { nav.go(Screen.MemberEdit(id)) } }
                    items(memberList ?: emptyList(), key = { it.id }) { m ->
                        val count = (profileList ?: emptyList()).count { it.memberId == m.id && !it.archived }
                        val orderCount = (orderList ?: emptyList()).count { o -> o.order.memberId == m.id }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(m.name + " — " + m.relation + if (!m.active) " (غیر فعال)" else "", style = MaterialTheme.typography.titleSmall)
                                        Text(Fmt.memberCode(m.id) + (if (m.ageGroup.isNotBlank()) " • " + m.ageGroup else "") +
                                            (if (m.mobile.isNotBlank()) " • " + Fmt.d(m.mobile) else ""), style = MaterialTheme.typography.bodySmall)
                                        if (m.fittingPref.isNotBlank()) Text("فٹنگ: " + m.fittingPref, style = MaterialTheme.typography.bodySmall)
                                        Text("ناپ: ${Fmt.num(count)} • آرڈرز: ${Fmt.num(orderCount)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { nav.go(Screen.MemberEdit(id, m.id)) }) { Icon(Icons.Default.Edit, "ترمیم") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SmallButton("نیا ناپ", Modifier.weight(1f)) { nav.go(Screen.ProfileEdit(id, m.id)) }
                                    SmallButton("اس کی بکنگ", Modifier.weight(1f)) { nav.go(Screen.Booking(customerId = id, memberId = m.id)) }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item { SwitchRow("پرانے ورژن بھی دکھائیں", showArchived) { showArchived = it } }
                    val gmap = (garmentList ?: emptyList()).associateBy { it.id }
                    val mmap = (memberList ?: emptyList()).associateBy { it.id }
                    val list = (profileList ?: emptyList()).filter { showArchived || !it.archived }
                    if (list.isEmpty()) item { Empty("ابھی کوئی ناپ محفوظ نہیں") }
                    items(list, key = { it.id }) { p ->
                        ProfileCard(p, mmap[p.memberId]?.name ?: "-", gmap[p.garmentId]?.name ?: "-") { nav.go(Screen.ProfileView(p.id)) }
                    }
                }
                else -> {
                    val list = orderList ?: emptyList()
                    if (list.isEmpty()) item { Empty("ابھی کوئی آرڈر نہیں") }
                    items(list, key = { it.order.id }) { r -> OrderRowCard(r) { nav.go(Screen.OrderDetail(r.order.id)) } }
                }
            }
        }
    }
    if (confirmDeact) Confirm("کسٹمر غیر فعال کریں؟", "کسٹمر کا تمام ریکارڈ محفوظ رہے گا، صرف فہرست سے ہٹ جائے گا۔", "غیر فعال کریں",
        danger = true, onDismiss = { confirmDeact = false }) { scope.act("کسٹمر غیر فعال ہو گیا") { repo.setCustomerActive(id, false) } }
}

@Composable
fun ProfileCard(p: pk.tailormaster.pro.data.MeasurementProfile, memberName: String, garmentName: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (p.archived) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (p.isDefault) Chip("ترجیحی ★", Semantic.ok)
                if (p.archived) Chip("پرانا ورژن", Semantic.warn)
            }
            Text("$garmentName • $memberName", style = MaterialTheme.typography.bodyMedium)
            Text("تاریخ: " + Fmt.date(p.date) + " • " + p.unit + (p.lastUsed?.let { " • آخری استعمال: " + Fmt.date(it) } ?: ""),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun OrderRowCard(r: pk.tailormaster.pro.data.OrderRow, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (r.overdue) Semantic.bad.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.orderNo(r.order.id) + (if (r.order.urgent) " ⚡ارجنٹ" else "") + (if (r.order.isDraft) " (ڈرافٹ)" else ""),
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Chip(OrderStatus.label(r.order.status), statusColor(r.order.status))
            }
            Text(r.customerName + " • " + r.memberName + " • " + Fmt.d(r.customerMobile), style = MaterialTheme.typography.bodyMedium)
            Text("بکنگ: " + Fmt.date(r.order.bookingDate) + " • ڈیلیوری: " + Fmt.date(r.order.deliveryDate) +
                (if (r.overdue) " • تاخیر!" else ""), style = MaterialTheme.typography.bodySmall,
                color = if (r.overdue) Semantic.bad else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("کپڑے: ${Fmt.num(r.itemCount)} (باقی ${Fmt.num(r.pendingItems)}) • کل ${Fmt.money(r.order.netAmount)} • بقایا ${Fmt.money(r.outstanding)}",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val relations = listOf("ذاتی استعمال", "والد", "والدہ", "بھائی", "بہن", "بیٹا", "بیٹی", "شوہر", "بیوی", "دادا", "پوتا", "بھتیجا", "دیگر")
private val ageGroups = listOf("بچہ", "نوجوان", "بالغ", "بزرگ")

@Composable
fun MemberEditScreen(customerId: Long, memberId: Long?) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var m by remember { mutableStateOf(FamilyMember(customerId = customerId, name = "", relation = "بیٹا")) }
    var loaded by remember { mutableStateOf(memberId == null) }
    var customRel by remember { mutableStateOf("") }
    LaunchedEffect(memberId) {
        if (memberId != null) repo.member(memberId)?.let { m = it; if (it.relation !in relations) customRel = it.relation }
        loaded = true
    }
    Page(if (memberId == null) "نیا فیملی ممبر" else "فیملی ممبر میں ترمیم") { pad ->
        if (!loaded) return@Page
        FormColumn(pad) {
            if (memberId != null) Text("ممبر کوڈ: " + Fmt.memberCode(memberId), style = MaterialTheme.typography.titleSmall)
            Input("نام یا عرفیت *", m.name, { m = m.copy(name = it) })
            Select("رشتہ", relations, if (m.relation in relations) m.relation else "دیگر", { it }, { m = m.copy(relation = it) })
            if (m.relation == "دیگر" || m.relation !in relations) Input("رشتہ (اپنا لکھیں)", customRel, { customRel = it })
            Select("عمر کا گروپ (اختیاری)", ageGroups, m.ageGroup.ifBlank { null }, { it }, { m = m.copy(ageGroup = it) })
            Input("موبائل (اختیاری)", m.mobile, { m = m.copy(mobile = it) }, keyboard = KeyboardType.Phone)
            Input("فٹنگ کی پسند (مثلاً کھلی، تنگ، سلم)", m.fittingPref, { m = m.copy(fittingPref = it) }, singleLine = false)
            Input("نوٹس", m.notes, { m = m.copy(notes = it) }, singleLine = false)
            SwitchRow("فعال", m.active) { m = m.copy(active = it) }
            BigButton("محفوظ کریں") {
                scope.act("فیملی ممبر محفوظ ہو گیا") {
                    val rel = if ((m.relation == "دیگر" || m.relation !in relations) && customRel.isNotBlank()) customRel.trim() else m.relation
                    repo.saveMember(m.copy(relation = rel))
                    nav.back()
                }
            }
        }
    }
}

@Composable
fun FamilyMembersScreen() {
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf(false) }
    val rows by load { membersWithCustomers() }
    Page("فیملی ممبرز", floating = {
        ExtendedFloatingActionButton(onClick = { pick = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("نیا ممبر") })
    }) { pad ->
        val t = q.trim()
        val list = (rows ?: emptyList()).filter { r ->
            t.isBlank() || r.first.name.contains(t) || r.first.relation.contains(t) || matchesCustomer(r.second, t)
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SearchBox(q, { q = it }, "ممبر یا کسٹمر کا نام / موبائل") }
            item { Text("ایک کسٹمر کے کئی افراد ہو سکتے ہیں۔ ہر فرد کا ناپ الگ رہتا ہے۔", style = MaterialTheme.typography.bodySmall) }
            items(list, key = { it.first.id }) { r ->
                Card(Modifier.fillMaxWidth().clickable { nav.go(Screen.CustomerProfile(r.first.customerId, 0)) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.first.name + " — " + r.first.relation, style = MaterialTheme.typography.titleSmall)
                        Text("کسٹمر: " + r.second.name + " • " + Fmt.memberCode(r.first.id), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (rows != null && list.isEmpty()) item { Empty("کوئی ممبر نہیں ملا") }
        }
    }
    if (pick) CustomerPicker(onDismiss = { pick = false }) { pick = false; nav.go(Screen.MemberEdit(it.id)) }
}

@Composable
fun MergeScreen() {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val gate = rememberPinGate()
    var from by remember { mutableStateOf<Customer?>(null) }
    var to by remember { mutableStateOf<Customer?>(null) }
    var picking by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    Page("ڈپلیکیٹ کسٹمر ضم کریں") { pad ->
        FormColumn(pad) {
            Text("اگر ایک ہی کسٹمر غلطی سے دو بار رجسٹر ہو گیا ہو تو یہاں دونوں کو ایک کریں۔ تمام ممبرز، ناپ، آرڈرز اور ادائیگیاں صحیح ریکارڈ میں منتقل ہو جائیں گی۔ کچھ بھی ڈیلیٹ نہیں ہوگا۔",
                style = MaterialTheme.typography.bodyMedium)
            SectionCard("۱۔ ڈپلیکیٹ (جو ختم کرنا ہے)") {
                Text(from?.let { it.name + " • " + Fmt.d(it.mobile) + " • " + Fmt.customerCode(it.id) } ?: "منتخب نہیں")
                SmallButton("منتخب کریں") { picking = 1 }
            }
            SectionCard("۲۔ اصل ریکارڈ (جس میں ملانا ہے)") {
                Text(to?.let { it.name + " • " + Fmt.d(it.mobile) + " • " + Fmt.customerCode(it.id) } ?: "منتخب نہیں")
                SmallButton("منتخب کریں") { picking = 2 }
            }
            BigButton("ضم کریں", enabled = from != null && to != null && from?.id != to?.id) { confirm = true }
        }
    }
    if (picking != 0) CustomerPicker(onDismiss = { picking = 0 }) { if (picking == 1) from = it else to = it; picking = 0 }
    if (confirm) Confirm("کیا آپ کو یقین ہے؟", "${from?.name} کا سارا ریکارڈ ${to?.name} میں منتقل ہو جائے گا۔", "ضم کریں",
        onDismiss = { confirm = false }) {
        gate.require(repo.settings) {
            scope.act("ریکارڈ ضم ہو گئے") { repo.mergeCustomers(from!!.id, to!!.id); nav.replace(Screen.CustomerProfile(to!!.id)) }
        }
    }
}
