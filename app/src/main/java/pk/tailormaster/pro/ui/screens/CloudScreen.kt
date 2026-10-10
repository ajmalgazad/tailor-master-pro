package pk.tailormaster.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pk.tailormaster.pro.sync.LinkPlan
import pk.tailormaster.pro.ui.*

const val WEB_APP_URL = "https://ajmalgazad.github.io/tailor-master-pro/app/"

@Composable
fun CloudScreen() {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val sync = LocalSync.current
    val scope = rememberCoroutineScope()
    val gate = rememberPinGate()
    val st = sync?.status?.collectAsState()?.value
    val settings by repo.settings.state.collectAsState()
    var email by remember { mutableStateOf(settings.cloudEmail) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<LinkPlan?>(null) }
    var localCustomers by remember { mutableStateOf(0) }
    var askLogout by remember { mutableStateOf(false) }

    Page("کلاؤڈ سنک") { pad ->
        FormColumn(pad) {
            SectionCard("یہ کیا ہے؟") {
                Text("دکان کے ایک اکاؤنٹ سے لاگ اِن کریں تو یہ فون اور ویب ورژن (کمپیوٹر یا دوسرا فون) ایک ہی ڈیٹا دیکھتے ہیں۔ " +
                    "ایک جگہ بکنگ کریں تو دوسری جگہ خود آ جاتی ہے۔ انٹرنیٹ نہ ہو تب بھی ایپ چلتی رہتی ہے اور انٹرنیٹ آنے پر سنک ہو جاتا ہے۔",
                    style = MaterialTheme.typography.bodyMedium)
                Text("ویب ورژن: $WEB_APP_URL", style = MaterialTheme.typography.bodySmall)
            }
            if (sync == null || st == null || !st.configured) {
                SectionCard("کلاؤڈ ابھی سیٹ نہیں ہوا") {
                    Text("اس ایپ میں ابھی دکان کا Firebase اکاؤنٹ نہیں جوڑا گیا۔ جڑنے کے بعد نیا ورژن انسٹال کریں تو یہاں لاگ اِن آ جائے گا۔",
                        color = Semantic.warn)
                }
                return@FormColumn
            }
            SectionCard("حالت") {
                KV("اکاؤنٹ", if (st.signedIn) st.email else "لاگ اِن نہیں", true)
                if (st.signedIn) {
                    KV("سنک", if (st.running) "چل رہا ہے" else "بند", color = if (st.running) Semantic.ok else Semantic.warn)
                    KV("آخری رابطہ", if (st.lastSync == 0L) "ابھی نہیں" else Fmt.dateTime(st.lastSync))
                    KV("اس ڈیوائس کا نمبر", Fmt.num(settings.deviceNo))
                }
                if (st.message.isNotBlank()) Text(st.message, color = if (st.error) Semantic.bad else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!st.signedIn) {
                SectionCard("لاگ اِن") {
                    Input("دکان کا ای میل", email, { email = it }, keyboard = KeyboardType.Email)
                    Input("پاس ورڈ", password, { password = it }, keyboard = KeyboardType.Password)
                    Text("پہلی بار لاگ اِن کے لیے انٹرنیٹ ضروری ہے۔", style = MaterialTheme.typography.bodySmall)
                    BigButton(if (busy) "انتظار کریں..." else "لاگ اِن کریں", enabled = !busy && email.isNotBlank() && password.isNotBlank()) {
                        busy = true
                        scope.act {
                            try {
                                localCustomers = repo.customerRows(true).count { !it.customer.sample }
                                val p = sync.signIn(email, password)
                                password = ""
                                if (p == LinkPlan.ALREADY_LINKED) { sync.completeLink(p); Msg.ok("سنک شروع ہو گیا") } else plan = p
                            } finally { busy = false }
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("سارا ڈیٹا دوبارہ بھیجیں", Modifier.weight(1f)) {
                        scope.act { val n = sync.uploadAll(); Msg.ok("${Fmt.num(n)} ریکارڈ کلاؤڈ کو بھیج دیے گئے") }
                    }
                    SmallButton("لاگ آؤٹ", Modifier.weight(1f)) { askLogout = true }
                }
            }
        }
    }

    plan?.let { p ->
        when (p) {
            LinkPlan.CLOUD_EMPTY_UPLOAD -> Confirm("پہلی بار کلاؤڈ سے جوڑیں",
                "کلاؤڈ ابھی خالی ہے۔ اس فون کا سارا ڈیٹا کلاؤڈ پر چلا جائے گا اور یہی دکان کا اصل ڈیٹا بن جائے گا۔", "جوڑیں",
                onDismiss = { plan = null }) {
                gate.require(repo.settings) { scope.act("فون کلاؤڈ سے جڑ گیا اور ڈیٹا بھیج دیا گیا") { sync!!.completeLink(p) } }
            }
            LinkPlan.CLOUD_HAS_DATA_REPLACE_LOCAL -> Confirm("کلاؤڈ میں پہلے سے ڈیٹا موجود ہے",
                (if (localCustomers > 0) "⚠ اس فون میں ${Fmt.num(localCustomers)} کسٹمر ہیں۔ جوڑنے پر اس فون کا ڈیٹا ہٹا کر کلاؤڈ والا ڈیٹا آ جائے گا۔ پہلے بیک اپ بنا لیں!\n\n" else "") +
                    "کلاؤڈ کا ڈیٹا اس فون پر آ جائے گا۔", "ہاں، کلاؤڈ کا ڈیٹا لیں", danger = localCustomers > 0,
                onDismiss = { plan = null; scope.act { sync?.signOut() } }) {
                gate.require(repo.settings) { scope.act("فون کلاؤڈ سے جڑ گیا۔ ڈیٹا آ رہا ہے...") { sync!!.completeLink(p) } }
            }
            LinkPlan.ALREADY_LINKED -> plan = null
        }
    }
    if (askLogout) Confirm("لاگ آؤٹ کریں؟", "اس فون کا ڈیٹا محفوظ رہے گا لیکن سنک بند ہو جائے گا۔", "لاگ آؤٹ",
        onDismiss = { askLogout = false }) { scope.act { sync?.signOut() } }
}
