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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import pk.tailormaster.pro.data.Garment
import pk.tailormaster.pro.data.Gender
import pk.tailormaster.pro.data.MeasureValue
import pk.tailormaster.pro.data.MeasurementField
import pk.tailormaster.pro.data.MeasurementProfile
import pk.tailormaster.pro.data.ReceiptKind
import pk.tailormaster.pro.data.UserError
import pk.tailormaster.pro.data.decodeValues
import pk.tailormaster.pro.data.encodeValues
import pk.tailormaster.pro.ui.*

/** Customer → member picker used before creating a measurement or booking. */
@Composable
fun MemberPickerFlow(onDismiss: () -> Unit, onPicked: (Customer, FamilyMember) -> Unit) {
    var customer by remember { mutableStateOf<Customer?>(null) }
    val c = customer
    if (c == null) {
        CustomerPicker(onDismiss = onDismiss) { customer = it }
    } else {
        val memberList by load(c.id) { members(c.id).filter { it.active } }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("فرد منتخب کریں — " + c.name) },
            text = {
                LazyColumn {
                    items(memberList ?: emptyList(), key = { it.id }) { m ->
                        Text(m.name + " — " + m.relation, style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.fillMaxWidth().clickable { onPicked(c, m) }.padding(vertical = 12.dp))
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("بند کریں") } },
        )
    }
}

@Composable
fun MeasurementsScreen() {
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    var garmentFilter by remember { mutableStateOf<Garment?>(null) }
    var picking by remember { mutableStateOf(false) }
    val profiles by load { allProfiles().filter { !it.archived } }
    val garmentList by load { garments() }
    val pairs by load { membersWithCustomers() }
    Page("ناپ ماسٹر", floating = {
        ExtendedFloatingActionButton(onClick = { picking = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("نیا ناپ") })
    }) { pad ->
        val gmap = (garmentList ?: emptyList()).associateBy { it.id }
        val mmap = (pairs ?: emptyList()).associateBy { it.first.id }
        val t = q.trim()
        val list = (profiles ?: emptyList()).filter { p ->
            val mc = mmap[p.memberId]
            (garmentFilter == null || p.garmentId == garmentFilter?.id) &&
                (t.isBlank() || p.name.contains(t) || (gmap[p.garmentId]?.name ?: "").contains(t) ||
                    (mc?.first?.name ?: "").contains(t) || (mc?.second?.let { matchesCustomer(it, t) } ?: false))
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SearchBox(q, { q = it }, "کسٹمر، فرد یا کپڑے سے تلاش") }
            item {
                Select("کپڑے کی قسم", listOf<Garment?>(null) + (garmentList ?: emptyList()), garmentFilter, { it?.name ?: "تمام" }, { garmentFilter = it })
            }
            items(list, key = { it.id }) { p ->
                val mc = mmap[p.memberId]
                ProfileCard(p, (mc?.first?.name ?: "-") + " (" + (mc?.second?.name ?: "-") + ")", gmap[p.garmentId]?.name ?: "-") {
                    nav.go(Screen.ProfileView(p.id))
                }
            }
            if (profiles != null && list.isEmpty()) item { Empty("کوئی ناپ نہیں ملا") }
        }
    }
    if (picking) MemberPickerFlow(onDismiss = { picking = false }) { c, m -> picking = false; nav.go(Screen.ProfileEdit(c.id, m.id)) }
}

private data class ValueRow(val fieldId: Long, val label: String, val value: String, val required: Boolean = false, val custom: Boolean = false)

@Composable
fun ProfileEditScreen(s: Screen.ProfileEdit) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf<MeasurementProfile?>(null) }
    var garment by remember { mutableStateOf<Garment?>(null) }
    var member by remember { mutableStateOf<FamilyMember?>(null) }
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(repo.settings.value.defaultUnit) }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var fitting by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    val rows = remember { mutableStateListOf<ValueRow>() }
    var newLabel by remember { mutableStateOf("") }
    val garmentList by load { garments().filter { it.active } }

    suspend fun loadFields(g: Garment, existing: List<MeasureValue>) {
        val fields: List<MeasurementField> = repo.fields(g.id).filter { it.active }
        rows.clear()
        fields.forEach { f -> rows += ValueRow(f.id, f.label + (if (f.unit.isNotBlank()) " (${f.unit})" else ""), existing.firstOrNull { it.fieldId == f.id }?.value ?: "", f.required) }
        existing.filter { e -> fields.none { it.id == e.fieldId } }.forEach { e -> rows += ValueRow(e.fieldId, e.label, e.value, custom = true) }
    }

    LaunchedEffect(s) {
        member = repo.member(s.memberId)
        if (s.profileId != null) {
            val p = repo.profile(s.profileId)
            if (p != null) {
                old = p
                name = p.name; unit = p.unit; fitting = p.fitting; instructions = p.instructions; notes = p.notes; isDefault = p.isDefault
                garment = repo.garment(p.garmentId)
                garment?.let { loadFields(it, decodeValues(p.valuesJson)) }
            }
        } else {
            fitting = member?.fittingPref ?: ""
            s.garmentId?.let { gid -> repo.garment(gid)?.let { garment = it; name = it.name; loadFields(it, emptyList()) } }
        }
    }

    val title = when {
        s.mode == "version" -> "ناپ تبدیل کریں (نیا ورژن)"
        else -> "نیا ناپ"
    }
    Page(title) { pad ->
        FormColumn(pad) {
            Text("فرد: " + (member?.let { it.name + " — " + it.relation } ?: "-"), style = MaterialTheme.typography.titleSmall)
            if (old == null) {
                Select("کپڑے کی قسم *", garmentList ?: emptyList(), garment, { it.name + " (" + Gender.label(it.gender) + ")" }, { g ->
                    garment = g
                    if (name.isBlank() || garmentList?.any { it.name == name } == true) name = g.name
                    scope.act { loadFields(g, emptyList()) }
                })
            } else {
                Text("کپڑا: " + (garment?.name ?: "-"), style = MaterialTheme.typography.titleSmall)
                Text("پرانا ناپ محفوظ رہے گا، تبدیلی نئے ورژن کے طور پر محفوظ ہوگی۔ پرانے آرڈرز پر کوئی اثر نہیں پڑے گا۔",
                    style = MaterialTheme.typography.bodySmall, color = Semantic.warn)
            }
            Input("پروفائل کا نام *", name, { name = it })
            Select("اکائی", listOf("انچ", "سینٹی میٹر"), unit, { it }, { unit = it })
            DateInput("تاریخ", date, { date = it })
            if (garment != null) {
                SectionCard("ناپ (${unit})") {
                    rows.forEachIndexed { i, r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Input(r.label + if (r.required) " *" else "", r.value, { v -> rows[i] = r.copy(value = v) },
                                Modifier.weight(1f), KeyboardType.Text)
                            if (r.custom) IconButton(onClick = { rows.removeAt(i) }) { Icon(Icons.Default.Delete, "ہٹائیں") }
                        }
                    }
                    HorizontalDivider()
                    Text("اضافی ناپ شامل کریں (صرف اس پروفائل کے لیے)", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Input("ناپ کا نام", newLabel, { newLabel = it }, Modifier.weight(1f))
                        IconButton(onClick = {
                            if (newLabel.isNotBlank()) { rows += ValueRow(0, newLabel.trim(), "", custom = true); newLabel = "" }
                        }) { Icon(Icons.Default.Add, "شامل") }
                    }
                }
            }
            Input("فٹنگ کی پسند", fitting, { fitting = it })
            Input("خصوصی ہدایات", instructions, { instructions = it }, singleLine = false)
            Input("نوٹس", notes, { notes = it }, singleLine = false)
            SwitchRow("اس کپڑے کے لیے ترجیحی (ڈیفالٹ) ناپ", isDefault) { isDefault = it }
            BigButton("محفوظ کریں") {
                scope.act("ناپ محفوظ ہو گیا") {
                    val g = garment ?: throw UserError("کپڑے کی قسم منتخب کریں")
                    val m = member ?: throw UserError("فرد نہیں ملا")
                    val bad = rows.firstOrNull { r -> r.value.isNotBlank() && pk.tailormaster.pro.domain.Calc.parseDouble(r.value) == null && !r.value.contains("/") }
                    if (bad != null) Msg.warn("'${bad.label}' میں نمبر کے علاوہ متن ہے")
                    val values = rows.map { MeasureValue(it.fieldId, it.label.substringBefore(" (").trim().ifBlank { it.label }, pk.tailormaster.pro.domain.Digits.toLatin(it.value.trim())) }
                    val p = MeasurementProfile(customerId = m.customerId, memberId = m.id, garmentId = g.id, name = name.trim(), date = date, unit = unit,
                        valuesJson = encodeValues(values), fitting = fitting, instructions = instructions, notes = notes, isDefault = isDefault)
                    val o = old
                    val id = if (o != null) repo.saveProfileVersion(o, p) else repo.saveNewProfile(p)
                    if (s.returnAfterSave) nav.back() else nav.replace(Screen.ProfileView(id))
                }
            }
        }
    }
}

@Composable
fun ProfileViewScreen(id: Long) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val p by load(id) { profile(id) }
    val memberRec by load(p?.memberId) { p?.let { member(it.memberId) } }
    val garmentRec by load(p?.garmentId) { p?.let { garment(it.garmentId) } }
    val history by load(p) { p?.let { profileHistory(it) } ?: emptyList() }
    var dup by remember { mutableStateOf(false) }
    var dupMemberPick by remember { mutableStateOf(false) }
    val siblings by load(p?.customerId) { p?.let { members(it.customerId) } ?: emptyList() }
    var dupTarget by remember { mutableStateOf<FamilyMember?>(null) }

    Page("ناپ پروفائل") { pad ->
        val pr = p ?: return@Page
        FormColumn(pad) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pr.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (pr.isDefault) Chip("ترجیحی ★", Semantic.ok)
                    if (pr.archived) Chip("پرانا ورژن", Semantic.warn)
                }
                KV("پروفائل نمبر", Fmt.profileCode(pr.id))
                KV("فرد", memberRec?.let { it.name + " — " + it.relation } ?: "-")
                KV("کپڑا", garmentRec?.name ?: "-")
                KV("تاریخ", Fmt.date(pr.date))
                KV("اکائی", pr.unit)
                KV("آخری استعمال", pr.lastUsed?.let { Fmt.date(it) } ?: "ابھی استعمال نہیں ہوا")
            }
            SectionCard("ناپ") {
                decodeValues(pr.valuesJson).forEach { v -> KV(v.label, if (v.value.isBlank()) "-" else Fmt.d(v.value), true) }
                if (pr.fitting.isNotBlank()) KV("فٹنگ", pr.fitting)
                if (pr.instructions.isNotBlank()) Text("ہدایات: " + pr.instructions)
                if (pr.notes.isNotBlank()) Text("نوٹس: " + pr.notes)
            }
            if (!pr.archived) {
                BigButton("ناپ تبدیل کریں (نیا ورژن)") { nav.go(Screen.ProfileEdit(pr.customerId, pr.memberId, pr.garmentId, pr.id, "version")) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("کاپی بنائیں", Modifier.weight(1f)) { dupMemberPick = true }
                    SmallButton("ترجیحی بنائیں", Modifier.weight(1f), enabled = !pr.isDefault) {
                        scope.act("ترجیحی ناپ مقرر ہو گیا") { repo.setDefaultProfile(pr) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("ناپ کی پرچی پرنٹ", Modifier.weight(1f)) { nav.go(Screen.Preview(ReceiptKind.MEASUREMENT, pr.id, profileSlip = true)) }
                SmallButton(if (pr.archived) "دوبارہ فعال کریں" else "محفوظ شدہ (آرکائیو) کریں", Modifier.weight(1f)) {
                    scope.act("تبدیلی محفوظ") { repo.archiveProfile(pr, !pr.archived) }
                }
            }
            SmallButton("اس فرد کی بکنگ", Modifier.fillMaxWidth()) { nav.go(Screen.Booking(customerId = pr.customerId, memberId = pr.memberId)) }
            val h = history ?: emptyList()
            if (h.isNotEmpty()) {
                SectionCard("پرانے ورژن (ہسٹری)") {
                    h.forEach { x ->
                        Text("• " + x.name + " — " + Fmt.date(x.date), modifier = Modifier.fillMaxWidth().clickable { nav.go(Screen.ProfileView(x.id)) }.padding(vertical = 6.dp))
                    }
                }
            }
        }
    }
    if (dupMemberPick) {
        AlertDialog(
            onDismissRequest = { dupMemberPick = false },
            title = { Text("کاپی کس فرد کے لیے؟") },
            text = {
                Column {
                    (siblings ?: emptyList()).forEach { m ->
                        Text(m.name + " — " + m.relation, modifier = Modifier.fillMaxWidth().clickable { dupTarget = m; dupMemberPick = false; dup = true }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { dupMemberPick = false }) { Text("بند کریں") } },
        )
    }
    if (dup) TextPrompt("نئی کاپی کا نام", "نام", (p?.name ?: "") + " (کاپی)", onDismiss = { dup = false }) { n ->
        scope.act("کاپی بن گئی") {
            val pr = p ?: return@act
            val newId = repo.duplicateProfile(pr, n.ifBlank { pr.name + " (کاپی)" }, dupTarget?.id ?: pr.memberId)
            nav.replace(Screen.ProfileView(newId))
        }
    }
}
