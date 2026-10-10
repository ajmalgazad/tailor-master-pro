package pk.tailormaster.pro.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import pk.tailormaster.pro.data.Customer
import pk.tailormaster.pro.data.OrderStatus
import pk.tailormaster.pro.data.Repository
import pk.tailormaster.pro.data.SettingsStore
import pk.tailormaster.pro.data.UserError
import pk.tailormaster.pro.domain.Calc
import pk.tailormaster.pro.print.PrinterManager
import java.util.Calendar

val LocalRepo = staticCompositionLocalOf<Repository> { error("no repo") }
val LocalPrinter = staticCompositionLocalOf<PrinterManager> { error("no printer") }
val LocalSync = staticCompositionLocalOf<pk.tailormaster.pro.sync.SyncManager?> { null }

// ------------------------------------------------------------------ messages
enum class MsgType { OK, WARN, ERROR }
data class UiMsg(val text: String, val type: MsgType, val id: Long = System.nanoTime())

object Msg {
    val flow = MutableStateFlow<UiMsg?>(null)
    fun ok(t: String) { flow.value = UiMsg(t, MsgType.OK) }
    fun warn(t: String) { flow.value = UiMsg(t, MsgType.WARN) }
    fun err(t: String) { flow.value = UiMsg(t, MsgType.ERROR) }
}

/** Runs a database action and turns any error into a clear Urdu message. */
fun CoroutineScope.act(success: String? = null, block: suspend () -> Unit) = launch {
    try {
        block()
        if (success != null) Msg.ok(success)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e // screen was closed: not an error
    } catch (e: UserError) {
        Msg.err(e.message ?: "خرابی")
    } catch (e: Exception) {
        Msg.err("خرابی: " + (e.message ?: e.javaClass.simpleName))
    }
}

/** Loads data and reloads automatically whenever anything in the database changes. */
@Composable
fun <T> load(vararg keys: Any?, block: suspend Repository.() -> T): State<T?> {
    val repo = LocalRepo.current
    val v by repo.version.collectAsState()
    return produceState<T?>(null, v, *keys) {
        value = try { repo.block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: UserError) { Msg.err(e.message ?: ""); null } catch (e: Exception) {
            Msg.err("خرابی: " + (e.message ?: e.javaClass.simpleName)); null
        }
    }
}

// ------------------------------------------------------------------ scaffold
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(
    title: String,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val nav = LocalNav.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (showBack) IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "واپس") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floating,
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad -> content(pad) }
}

/** Scrollable form body. */
@Composable
fun FormColumn(pad: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
    }
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun KV(label: String, value: String, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), enabled: Boolean = true, color: Color? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = if (color != null) ButtonDefaults.buttonColors(containerColor = color) else ButtonDefaults.buttonColors(),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SmallButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 46.dp), shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun Input(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, error: String? = null, enabled: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), singleLine = singleLine, enabled = enabled,
        isError = error != null, supportingText = if (error != null) { { Text(error) } } else null, minLines = minLines,
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

/** Money input in rupees; shows validation if the text is not a valid non-negative amount. */
@Composable
fun MoneyInput(label: String, text: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    val err = if (text.isNotBlank() && Calc.parseMoney(text) == null) "درست رقم لکھیں (منفی نہیں)" else null
    Input(label, text, onChange, modifier, KeyboardType.Decimal, error = err)
}

@Composable
fun DateInput(label: String, ms: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = {
            val c = Calendar.getInstance().apply { timeInMillis = ms }
            DatePickerDialog(ctx, { _, y, m, d ->
                val n = Calendar.getInstance().apply { timeInMillis = ms; set(y, m, d) }
                onChange(n.timeInMillis)
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        },
        modifier = modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(Icons.Default.DateRange, null)
        Spacer(Modifier.width(8.dp))
        Text("$label: ${Fmt.date(ms)}", modifier = Modifier.weight(1f))
    }
}

@Composable
fun <T> Select(label: String, options: List<T>, selected: T?, labelOf: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                @Suppress("UNCHECKED_CAST")
                val shown = when {
                    selected != null -> labelOf(selected)
                    options.any { it == null } -> labelOf(null as T)
                    else -> "منتخب کریں"
                }
                Text(shown, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(labelOf(o)) }, onClick = { open = false; onSelect(o) })
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

fun statusColor(s: String): Color = when (s) {
    OrderStatus.READY -> Color(0xFF2E7D32)
    OrderStatus.DELIVERED -> Color(0xFF546E7A)
    OrderStatus.CANCELLED -> Color(0xFFC62828)
    OrderStatus.NEW -> Color(0xFF1565C0)
    else -> Color(0xFFB26A00)
}

@Composable
fun Chip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun Confirm(title: String, text: String, confirmText: String = "ہاں", danger: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = { onDismiss(); onConfirm() },
                colors = if (danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("نہیں") } },
    )
}

@Composable
fun TextPrompt(title: String, label: String, initial: String = "", keyboard: KeyboardType = KeyboardType.Text, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Input(label, t, { t = it }, keyboard = keyboard) },
        confirmButton = { Button(onClick = { onDismiss(); onOk(t) }) { Text("ٹھیک ہے") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("منسوخ") } },
    )
}

/** Asks for the PIN (if one is set) before running a sensitive action. */
class PinGate {
    var pending by mutableStateOf<(() -> Unit)?>(null)
    fun require(settings: SettingsStore, action: () -> Unit) {
        if (!settings.hasPin) action() else pending = action
    }
}

@Composable
fun rememberPinGate(): PinGate {
    val gate = remember { PinGate() }
    val repo = LocalRepo.current
    gate.pending?.let { action ->
        PinDialog(onDismiss = { gate.pending = null }) { pin ->
            if (repo.settings.checkPin(pin)) { gate.pending = null; action() } else Msg.err("غلط PIN")
        }
    }
    return gate
}

@Composable
fun PinDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصدیق کے لیے PIN درج کریں") },
        text = { Input("PIN", t, { t = it.filter { c -> c.isDigit() }.take(8) }, keyboard = KeyboardType.NumberPassword) },
        confirmButton = { Button(onClick = { onOk(t) }) { Text("تصدیق") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("منسوخ") } },
    )
}

/** Searchable customer list in a dialog. */
@Composable
fun CustomerPicker(onDismiss: () -> Unit, onPick: (Customer) -> Unit) {
    var q by remember { mutableStateOf("") }
    val rows by load { customerRows(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کسٹمر منتخب کریں") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("نام، موبائل، کوڈ یا پتہ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(8.dp))
                val list = (rows ?: emptyList()).filter { matchesCustomer(it.customer, q) }.take(100)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.customer.id }) { r ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(r.customer) }.padding(vertical = 10.dp)) {
                            Text(r.customer.name, style = MaterialTheme.typography.titleSmall)
                            Text(Fmt.customerCode(r.customer.id) + " • " + Fmt.d(r.customer.mobile), style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                    if (list.isEmpty()) item { Empty("کوئی کسٹمر نہیں ملا") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بند کریں") } },
    )
}

fun matchesCustomer(c: Customer, q: String): Boolean {
    if (q.isBlank()) return true
    val t = pk.tailormaster.pro.domain.Digits.toLatin(q.trim()).lowercase()
    val code = pk.tailormaster.pro.data.DeviceIds.code("C", c.id, 4).lowercase()
    return c.name.lowercase().contains(t) || c.mobile.contains(t) || c.whatsapp.contains(t) || code.contains(t) ||
        c.id.toString() == t || pk.tailormaster.pro.data.DeviceIds.parse(t) == c.id || c.address.contains(t) || c.area.contains(t) || c.city.contains(t) || c.fatherName.contains(t)
}

@Composable
fun MessageBanner(msg: UiMsg, onClose: () -> Unit) {
    val bg = when (msg.type) { MsgType.OK -> Semantic.ok; MsgType.WARN -> Semantic.warn; MsgType.ERROR -> Semantic.bad }
    Surface(color = bg, shape = RoundedCornerShape(12.dp), shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().padding(12.dp).clickable { onClose() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when (msg.type) { MsgType.OK -> "✓"; MsgType.WARN -> "!"; MsgType.ERROR -> "✕" },
                color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Text(msg.text, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun RowScope.Stat(title: String, value: String, color: Color, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.weight(1f).let { if (onClick != null) it.clickable { onClick() } else it },
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ColorDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
}

@Composable
fun SearchBox(q: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        q, onChange, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(hint) },
        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
    )
}
