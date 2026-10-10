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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import pk.tailormaster.pro.data.CustomRate
import pk.tailormaster.pro.data.Customer
import pk.tailormaster.pro.data.ExtraService
import pk.tailormaster.pro.data.FamilyMember
import pk.tailormaster.pro.data.Garment
import pk.tailormaster.pro.data.Gender
import pk.tailormaster.pro.data.MeasurementField
import pk.tailormaster.pro.data.ServiceKind
import pk.tailormaster.pro.data.UserError
import pk.tailormaster.pro.domain.Calc
import pk.tailormaster.pro.ui.*

@Composable
fun GarmentsScreen() {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val list by load { garments() }
    var gender by remember { mutableStateOf<String?>(null) }
    Page("کپڑوں کی اقسام", floating = {
        ExtendedFloatingActionButton(onClick = { nav.go(Screen.GarmentEdit()) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("نئی قسم") })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Select("گروپ", listOf<String?>(null) + Gender.all, gender, { it?.let { g -> Gender.label(g) } ?: "تمام" }, { gender = it }) }
            item { Text("ترتیب بدلنے کے لیے تیر دبائیں۔ کسی قسم پر دبا کر نام، ریٹ اور ناپ کے خانے تبدیل کریں۔", style = MaterialTheme.typography.bodySmall) }
            items((list ?: emptyList()).filter { gender == null || it.gender == gender }, key = { it.id }) { g ->
                Card(Modifier.fillMaxWidth().clickable { nav.go(Screen.GarmentEdit(g.id)) }) {
                    Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name + if (!g.active) " (غیر فعال)" else "", style = MaterialTheme.typography.titleSmall)
                            Text(Gender.label(g.gender) + " • ریٹ: " + Fmt.money(g.defaultRate), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { scope.act { repo.moveGarment(g.id, true) } }) { Icon(Icons.Default.KeyboardArrowUp, "اوپر") }
                        IconButton(onClick = { scope.act { repo.moveGarment(g.id, false) } }) { Icon(Icons.Default.KeyboardArrowDown, "نیچے") }
                    }
                }
            }
        }
    }
}

@Composable
fun GarmentEditScreen(id: Long?) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var g by remember { mutableStateOf(Garment(name = "")) }
    var rate by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(id == null) }
    var currentId by remember { mutableStateOf(id) }
    val fieldList by load(currentId) { currentId?.let { fields(it) } ?: emptyList() }
    var editField by remember { mutableStateOf<MeasurementField?>(null) }
    LaunchedEffect(id) {
        if (id != null) repo.garment(id)?.let { g = it; rate = Calc.moneyInput(it.defaultRate) }
        loaded = true
    }
    Page(if (id == null) "نئی کپڑے کی قسم" else "کپڑے کی قسم") { pad ->
        if (!loaded) return@Page
        FormColumn(pad) {
            Input("نام *", g.name, { g = g.copy(name = it) })
            Select("گروپ", Gender.all, g.gender, { Gender.label(it) }, { g = g.copy(gender = it) })
            MoneyInput("معیاری سلائی ریٹ (روپے)", rate, { rate = it })
            Input("رسید پر تفصیل", g.receiptDesc, { g = g.copy(receiptDesc = it) })
            SwitchRow("فعال", g.active) { g = g.copy(active = it) }
            Text("ریٹ بدلنے سے پرانے آرڈرز کی رقم نہیں بدلے گی۔", style = MaterialTheme.typography.bodySmall)
            BigButton("محفوظ کریں") {
                scope.act("محفوظ ہو گیا") {
                    val r = Calc.parseMoney(rate) ?: throw UserError("ریٹ درست نہیں")
                    val newId = repo.saveGarment(g.copy(defaultRate = r, receiptDesc = g.receiptDesc.ifBlank { g.name }))
                    if (currentId == null) { currentId = newId; g = g.copy(id = newId) }
                }
            }
            if (currentId != null) {
                SectionCard("ناپ کے خانے (فارم)") {
                    Text("یہ خانے اس کپڑے کا ناپ لیتے وقت دکھائی دیں گے۔", style = MaterialTheme.typography.bodySmall)
                    (fieldList ?: emptyList()).forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { editField = f }) {
                                Text(f.label + (if (f.required) " *" else "") + (if (!f.active) " (بند)" else ""), style = MaterialTheme.typography.bodyLarge)
                                if (f.unit.isNotBlank()) Text("اکائی: " + f.unit, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { scope.act { repo.moveField(currentId!!, f.id, true) } }) { Icon(Icons.Default.KeyboardArrowUp, "اوپر") }
                            IconButton(onClick = { scope.act { repo.moveField(currentId!!, f.id, false) } }) { Icon(Icons.Default.KeyboardArrowDown, "نیچے") }
                            IconButton(onClick = { editField = f }) { Icon(Icons.Default.Edit, "ترمیم") }
                        }
                        HorizontalDivider()
                    }
                    SmallButton("+ نیا ناپ کا خانہ") { editField = MeasurementField(garmentId = currentId!!, label = "") }
                }
            }
        }
    }
    editField?.let { f ->
        var label by remember(f) { mutableStateOf(f.label) }
        var unit by remember(f) { mutableStateOf(f.unit) }
        var req by remember(f) { mutableStateOf(f.required) }
        var active by remember(f) { mutableStateOf(f.active) }
        AlertDialog(
            onDismissRequest = { editField = null },
            title = { Text(if (f.id == 0L) "نیا ناپ کا خانہ" else "ناپ کا خانہ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Input("نام (مثلاً لمبائی)", label, { label = it })
                    Input("الگ اکائی (اختیاری)", unit, { unit = it })
                    CheckRow("لازمی خانہ", req) { req = it }
                    CheckRow("فعال", active) { active = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.act("محفوظ") { repo.saveField(f.copy(label = label.trim(), unit = unit.trim(), required = req, active = active)); editField = null }
                }) { Text("محفوظ") }
            },
            dismissButton = { TextButton(onClick = { editField = null }) { Text("منسوخ") } },
        )
    }
}

@Composable
fun RatesScreen() {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    val garmentList by load { garments() }
    val rates by load { customRates() }
    val serviceList by load { services() }
    val history by load { rateHistory() }
    val pairs by load { membersWithCustomers() }
    var editRateFor by remember { mutableStateOf<Garment?>(null) }
    var editCustom by remember { mutableStateOf<CustomRate?>(null) }
    var editService by remember { mutableStateOf<ExtraService?>(null) }

    Page("ریٹ ماسٹر") { pad ->
        val gmap = (garmentList ?: emptyList()).associateBy { it.id }
        val members = (pairs ?: emptyList()).associateBy { it.first.id }
        val customers = (pairs ?: emptyList()).associate { it.second.id to it.second }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { TabBar(listOf("معیاری ریٹ", "خصوصی ریٹ", "اضافی سروسز", "ریٹ ہسٹری"), tab) { tab = it } }
            when (tab) {
                0 -> {
                    item { Text("ہر کپڑے کا معیاری ریٹ۔ تبدیلی صرف نئی بکنگ پر لاگو ہوگی۔", style = MaterialTheme.typography.bodySmall) }
                    Gender.all.forEach { gen ->
                        val gl = (garmentList ?: emptyList()).filter { it.gender == gen }
                        if (gl.isNotEmpty()) {
                            item { Text(Gender.label(gen), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                            items(gl, key = { "g" + it.id }) { g ->
                                Card(Modifier.fillMaxWidth().clickable { editRateFor = g }) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(g.name + if (!g.active) " (غیر فعال)" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                        Text(Fmt.money(g.defaultRate), style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item { Text("کسی خاص کسٹمر یا فیملی ممبر کے لیے الگ ریٹ۔ ترتیب: ممبر کا ریٹ ← کسٹمر کا ریٹ ← معیاری ریٹ۔", style = MaterialTheme.typography.bodySmall) }
                    item { BigButton("+ نیا خصوصی ریٹ") { editCustom = CustomRate(garmentId = 0, rate = 0) } }
                    items(rates ?: emptyList(), key = { "r" + it.id }) { r ->
                        Card(Modifier.fillMaxWidth().clickable { editCustom = r }) {
                            Column(Modifier.padding(12.dp)) {
                                Row {
                                    Text(gmap[r.garmentId]?.name ?: "-", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                    Text(Fmt.money(r.rate), style = MaterialTheme.typography.titleSmall)
                                }
                                val who = r.memberId?.let { mid -> members[mid]?.let { "ممبر: " + it.first.name + " (" + it.second.name + ")" } }
                                    ?: r.customerId?.let { "کسٹمر: " + (customers[it]?.name ?: "-") } ?: "-"
                                Text(who + if (!r.active) " • غیر فعال" else "", style = MaterialTheme.typography.bodySmall)
                                if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                2 -> {
                    item { Text("ارجنٹ، کڑھائی، آلٹریشن وغیرہ کے چارجز۔ بکنگ میں ایک بٹن سے شامل ہوتے ہیں۔", style = MaterialTheme.typography.bodySmall) }
                    item { BigButton("+ نئی سروس") { editService = ExtraService(name = "") } }
                    items(serviceList ?: emptyList(), key = { "s" + it.id }) { sv ->
                        Card(Modifier.fillMaxWidth().clickable { editService = sv }) {
                            Row(Modifier.padding(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(sv.name + if (!sv.active) " (غیر فعال)" else "", style = MaterialTheme.typography.titleSmall)
                                    Text(ServiceKind.label(sv.kind), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(Fmt.money(sv.amount), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
                else -> {
                    items(history ?: emptyList(), key = { "h" + it.id }) { h ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text((gmap[h.garmentId]?.name ?: "-") + " • " + Fmt.dateTime(h.at), style = MaterialTheme.typography.titleSmall)
                            val who = h.memberId?.let { members[it]?.first?.name } ?: h.customerId?.let { customers[it]?.name } ?: "معیاری"
                            Text("$who: ${Fmt.money(h.oldRate)} ← ${Fmt.money(h.newRate)} • ${h.note}", style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    editRateFor?.let { g ->
        TextPrompt("ریٹ: " + g.name, "نیا ریٹ (روپے)", Calc.moneyInput(g.defaultRate), androidx.compose.ui.text.input.KeyboardType.Decimal,
            onDismiss = { editRateFor = null }) { t ->
            scope.act("ریٹ تبدیل ہو گیا") {
                val r = Calc.parseMoney(t) ?: throw UserError("ریٹ درست نہیں")
                repo.saveGarment(g.copy(defaultRate = r))
            }
        }
    }

    editCustom?.let { r ->
        var garment by remember(r) { mutableStateOf((garmentList ?: emptyList()).firstOrNull { it.id == r.garmentId }) }
        var cust by remember(r) { mutableStateOf<Customer?>(r.customerId?.let { (pairs ?: emptyList()).firstOrNull { p -> p.second.id == it }?.second }
            ?: r.memberId?.let { mid -> (pairs ?: emptyList()).firstOrNull { p -> p.first.id == mid }?.second }) }
        var mem by remember(r) { mutableStateOf<FamilyMember?>(r.memberId?.let { mid -> (pairs ?: emptyList()).firstOrNull { it.first.id == mid }?.first }) }
        var amount by remember(r) { mutableStateOf(if (r.id == 0L) "" else Calc.moneyInput(r.rate)) }
        var note by remember(r) { mutableStateOf(r.note) }
        var active by remember(r) { mutableStateOf(r.active) }
        var picking by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editCustom = null },
            title = { Text("خصوصی ریٹ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallButton("کسٹمر: " + (cust?.name ?: "منتخب کریں"), Modifier.fillMaxWidth()) { picking = true }
                    val ms = (pairs ?: emptyList()).filter { it.second.id == cust?.id }.map { it.first }
                    Select("فیملی ممبر (اختیاری)", listOf<FamilyMember?>(null) + ms, mem, { it?.let { m -> m.name + " — " + m.relation } ?: "تمام افراد" }, { mem = it })
                    Select("کپڑا", (garmentList ?: emptyList()), garment, { it.name }, { garment = it })
                    MoneyInput("ریٹ (روپے)", amount, { amount = it })
                    Input("نوٹ", note, { note = it })
                    CheckRow("فعال", active) { active = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.act("خصوصی ریٹ محفوظ") {
                        val g = garment ?: throw UserError("کپڑا منتخب کریں")
                        val c = cust ?: throw UserError("کسٹمر منتخب کریں")
                        val a = Calc.parseMoney(amount) ?: throw UserError("ریٹ درست نہیں")
                        repo.saveCustomRate(r.copy(garmentId = g.id, customerId = c.id, memberId = mem?.id, rate = a, note = note, active = active))
                        editCustom = null
                    }
                }) { Text("محفوظ") }
            },
            dismissButton = { TextButton(onClick = { editCustom = null }) { Text("منسوخ") } },
        )
        if (picking) CustomerPicker(onDismiss = { picking = false }) { cust = it; mem = null; picking = false }
    }

    editService?.let { sv ->
        var name by remember(sv) { mutableStateOf(sv.name) }
        var kind by remember(sv) { mutableStateOf(sv.kind) }
        var amount by remember(sv) { mutableStateOf(if (sv.id == 0L) "" else Calc.moneyInput(sv.amount)) }
        var active by remember(sv) { mutableStateOf(sv.active) }
        AlertDialog(
            onDismissRequest = { editService = null },
            title = { Text("سروس") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Input("نام", name, { name = it })
                    Select("قسم", ServiceKind.all, kind, { ServiceKind.label(it) }, { kind = it })
                    MoneyInput("رقم (روپے)", amount, { amount = it })
                    CheckRow("فعال", active) { active = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.act("سروس محفوظ") {
                        val a = Calc.parseMoney(amount) ?: throw UserError("رقم درست نہیں")
                        repo.saveService(sv.copy(name = name.trim(), kind = kind, amount = a, active = active))
                        editService = null
                    }
                }) { Text("محفوظ") }
            },
            dismissButton = { TextButton(onClick = { editService = null }) { Text("منسوخ") } },
        )
    }
}
