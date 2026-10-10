package pk.tailormaster.pro.sync

import android.content.Context
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pk.tailormaster.pro.data.AppDatabase
import pk.tailormaster.pro.data.AppJson
import pk.tailormaster.pro.data.Change
import pk.tailormaster.pro.data.DeviceIds
import pk.tailormaster.pro.data.Repository
import pk.tailormaster.pro.data.ShopSettings
import pk.tailormaster.pro.data.SyncSchema
import pk.tailormaster.pro.data.SyncTable
import pk.tailormaster.pro.data.UserError
import java.io.File
import java.util.Date
import java.util.concurrent.Executors

data class SyncStatus(
    val configured: Boolean = false,
    val signedIn: Boolean = false,
    val email: String = "",
    val running: Boolean = false,
    val online: Boolean = false,
    val lastSync: Long = 0,
    val message: String = "",
    val error: Boolean = false,
)

/** What has to happen when this device is linked to a shop account for the first time. */
enum class LinkPlan { ALREADY_LINKED, CLOUD_EMPTY_UPLOAD, CLOUD_HAS_DATA_REPLACE_LOCAL }

/** Reads the shop's Firebase settings from assets/google-services.json (added when the shop sets up Firebase). */
object FirebaseConfig {
    fun load(context: Context): FirebaseOptions? = try {
        val text = context.assets.open("google-services.json").bufferedReader().readText()
        val root = Json.parseToJsonElement(text).jsonObject
        val project = root["project_info"]!!.jsonObject
        val projectId = project["project_id"]!!.jsonPrimitive.content
        val bucket = project["storage_bucket"]?.jsonPrimitive?.contentOrNull
        val client = root["client"]!!.jsonArray.map { it.jsonObject }.firstOrNull {
            it["client_info"]?.jsonObject?.get("android_client_info")?.jsonObject?.get("package_name")?.jsonPrimitive?.content == context.packageName
        } ?: root["client"]!!.jsonArray.first().jsonObject
        val appId = client["client_info"]!!.jsonObject["mobilesdk_app_id"]!!.jsonPrimitive.content
        val apiKey = client["api_key"]!!.jsonArray.first().jsonObject["current_key"]!!.jsonPrimitive.content
        FirebaseOptions.Builder().setProjectId(projectId).setApplicationId(appId).setApiKey(apiKey)
            .apply { if (bucket != null) setStorageBucket(bucket) }.build()
    } catch (e: Exception) {
        null
    }
}

class SyncManager(
    private val context: Context,
    private val repo: Repository,
    private val db: AppDatabase,
    private val appName: String = "tailor",
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** All database work from listeners runs on one thread, in order. */
    private val applyExecutor = Executors.newSingleThreadExecutor()
    private var auth: FirebaseAuth? = null
    private var fs: FirebaseFirestore? = null
    private val listeners = mutableListOf<ListenerRegistration>()
    @Volatile private var shopRef: DocumentReference? = null

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status

    val configured get() = fs != null

    /** Call once at start. [emulatorHost] is only for automated tests. */
    fun init(options: FirebaseOptions? = FirebaseConfig.load(context), emulatorHost: String? = null) {
        if (options == null) {
            _status.value = SyncStatus(configured = false, message = "کلاؤڈ ابھی سیٹ نہیں ہوا (Firebase فائل موجود نہیں)")
            return
        }
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == appName } ?: FirebaseApp.initializeApp(context, options, appName)
        auth = FirebaseAuth.getInstance(app)
        fs = FirebaseFirestore.getInstance(app)
        if (emulatorHost != null) {
            auth!!.useEmulator(emulatorHost, 9099)
            fs!!.useEmulator(emulatorHost, 8080)
        }
        repo.onCommitted = { changes -> push(changes) }
        repo.settings.onShopChanged = { shop -> pushShopSettings(shop) }

        val s = repo.settings.value
        val user = auth!!.currentUser
        _status.value = SyncStatus(configured = true, signedIn = user != null && user.uid == s.cloudUid, email = s.cloudEmail,
            message = if (user != null && user.uid == s.cloudUid) "" else "لاگ اِن نہیں")
        if (user != null && user.uid == s.cloudUid && s.cloudUid.isNotBlank()) start(s.cloudUid)
    }

    private fun msg(m: String, error: Boolean = false) { _status.value = _status.value.copy(message = m, error = error) }

    private fun authError(e: Exception): String = when ((e as? FirebaseAuthException)?.errorCode) {
        "ERROR_INVALID_EMAIL" -> "ای میل درست نہیں"
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "ای میل یا پاس ورڈ غلط ہے"
        "ERROR_USER_NOT_FOUND" -> "یہ ای میل رجسٹرڈ نہیں۔ Firebase میں Users کے اندر یہ ای میل بنائیں"
        "ERROR_USER_DISABLED" -> "یہ اکاؤنٹ بند ہے"
        "ERROR_TOO_MANY_REQUESTS" -> "بہت زیادہ کوششیں۔ کچھ دیر بعد دوبارہ کریں"
        else -> if (e.message?.contains("network", true) == true) "انٹرنیٹ نہیں ہے۔ پہلی بار لاگ اِن کے لیے انٹرنیٹ ضروری ہے" else "لاگ اِن نہیں ہو سکا: " + (e.message ?: "")
    }

    /** Signs in and tells the caller what linking this device will do. Nothing is changed yet. */
    suspend fun signIn(email: String, password: String): LinkPlan {
        val a = auth ?: throw UserError("کلاؤڈ ابھی سیٹ نہیں ہوا")
        val user = try { a.signInWithEmailAndPassword(email.trim(), password).await().user!! } catch (e: Exception) { throw UserError(authError(e)) }
        val s = repo.settings.value
        if (s.cloudUid == user.uid) return LinkPlan.ALREADY_LINKED
        val meta = fs!!.collection("shops").document(user.uid).collection("meta").document("devices")
        val snap = try { meta.get(Source.SERVER).await() } catch (e: Exception) { throw UserError("کلاؤڈ سے رابطہ نہیں ہو سکا۔ انٹرنیٹ چیک کریں") }
        return if (snap.exists()) LinkPlan.CLOUD_HAS_DATA_REPLACE_LOCAL else LinkPlan.CLOUD_EMPTY_UPLOAD
    }

    /** Completes linking after the user agreed to [plan]. */
    suspend fun completeLink(plan: LinkPlan) = withContext(Dispatchers.IO) {
        val user = auth?.currentUser ?: throw UserError("پہلے لاگ اِن کریں")
        val uid = user.uid
        val meta = fs!!.collection("shops").document(uid).collection("meta").document("devices")
        when (plan) {
            LinkPlan.ALREADY_LINKED -> Unit
            LinkPlan.CLOUD_EMPTY_UPLOAD -> {
                // This device becomes device 0 and its data becomes the shop's data.
                meta.set(mapOf("next" to 1L, "created" to FieldValue.serverTimestamp())).await()
                setDevice(0)
                repo.settings.update { it.copy(cloudUid = uid, cloudEmail = user.email ?: "", lastPull = 0) }
                shopRef = fs!!.collection("shops").document(uid)
                uploadAll()
                pushShopSettings(ShopSettings.of(repo.settings.value))
                pushLogo()
            }
            LinkPlan.CLOUD_HAS_DATA_REPLACE_LOCAL -> {
                val next = fs!!.runTransaction { tr ->
                    val n = tr.get(meta).getLong("next") ?: 1L
                    tr.set(meta, mapOf("next" to n + 1), SetOptions.merge())
                    n
                }.await()
                setDevice(next.toInt())
                clearLocal()
                repo.settings.update { it.copy(cloudUid = uid, cloudEmail = user.email ?: "", lastPull = 0, sampleLoaded = false) }
            }
        }
        start(uid)
    }

    private fun setDevice(n: Int) {
        DeviceIds.device = n
        repo.settings.update { it.copy(deviceNo = n) }
    }

    private fun clearLocal() {
        db.runInTransaction(Runnable {
            db.openHelper.writableDatabase.let { w -> SyncSchema.all.forEach { t -> w.execSQL("DELETE FROM ${t.table}") } }
        })
        repo.bump()
    }

    suspend fun signOut() {
        stop()
        auth?.signOut()
        repo.settings.update { it.copy(cloudUid = "", cloudEmail = "") }
        _status.value = SyncStatus(configured = configured, message = "لاگ آؤٹ ہو گیا۔ اس فون کا ڈیٹا محفوظ ہے")
    }

    // ------------------------------------------------------------------ listening
    fun start(uid: String) {
        stop()
        val f = fs ?: return
        val ref = f.collection("shops").document(uid)
        shopRef = ref
        _status.value = _status.value.copy(signedIn = true, running = true, email = repo.settings.value.cloudEmail, message = "سنک ہو رہا ہے...", error = false)
        val since = Timestamp(Date((repo.settings.value.lastPull - 60_000L).coerceAtLeast(0)))
        for (t in SyncSchema.all) {
            listeners += ref.collection(t.coll).whereGreaterThan("_s", since).addSnapshotListener { snap, err ->
                if (err != null) { onListenError(err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val docs = snap.documentChanges.filter { it.type != DocumentChange.Type.REMOVED }.map { it.document as DocumentSnapshot }
                val fromServer = !snap.metadata.isFromCache
                if (docs.isNotEmpty()) applyExecutor.execute { applyRemote(t, docs) }
                if (fromServer) _status.value = _status.value.copy(online = true, lastSync = System.currentTimeMillis(), message = "", error = false)
            }
        }
        listeners += ref.collection("meta").document("settings").addSnapshotListener { snap, _ ->
            val data = snap?.data ?: return@addSnapshotListener
            if (snap.metadata.hasPendingWrites()) return@addSnapshotListener
            try {
                val shop = AppJson.decodeFromJsonElement(ShopSettings.serializer(), SyncSchema.fromMap(data))
                if (shop != ShopSettings.of(repo.settings.value)) repo.settings.applyRemoteShop(shop)
            } catch (_: Exception) { }
        }
        listeners += ref.collection("meta").document("logo").addSnapshotListener { snap, _ ->
            val b64 = snap?.getString("png") ?: return@addSnapshotListener
            if (snap.metadata.hasPendingWrites()) return@addSnapshotListener
            scope.launch {
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val file = File(context.filesDir, "logo.png")
                    if (!file.exists() || !file.readBytes().contentEquals(bytes)) {
                        file.writeBytes(bytes)
                        repo.settings.update { it.copy(logoFile = "logo.png") }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun onListenError(e: FirebaseFirestoreException) {
        val m = when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "کلاؤڈ نے اجازت نہیں دی۔ Firestore کے Rules چیک کریں"
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> "دوبارہ لاگ اِن کریں"
            else -> "سنک میں خرابی: " + e.code.name
        }
        msg(m, true)
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
        shopRef = null
        _status.value = _status.value.copy(running = false)
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyRemote(table: SyncTable<*>, docs: List<DocumentSnapshot>) {
        val t = table as SyncTable<Any>
        val raw = db.dao()
        var newest = repo.settings.value.lastPull
        try {
            db.runInTransaction(Runnable {
                val w = db.openHelper.writableDatabase
                for (doc in docs) {
                    val data = doc.data ?: continue
                    val id = doc.id.toLongOrNull() ?: continue
                    if (data["_del"] == true) {
                        w.execSQL("DELETE FROM ${t.table} WHERE id = ?", arrayOf<Any>(id))
                    } else {
                        t.upsert(raw, t.fromJson(SyncSchema.fromMap(data)))
                    }
                    doc.getTimestamp("_s")?.let { ts -> newest = maxOf(newest, ts.toDate().time) }
                }
            })
            if (newest > repo.settings.value.lastPull) repo.settings.update { it.copy(lastPull = newest) }
            repo.bump()
        } catch (e: Exception) {
            msg("کلاؤڈ ڈیٹا لاگو کرنے میں خرابی: " + (e.message ?: ""), true)
        }
    }

    // ------------------------------------------------------------------ sending
    /** Sends committed local changes. Firestore keeps them safely queued while offline. */
    fun push(changes: List<Change>) {
        val ref = shopRef ?: return
        val f = fs ?: return
        val latest = LinkedHashMap<String, Change>()
        changes.forEach { latest[it.coll + "/" + it.id] = it }
        latest.values.chunked(400).forEach { chunk ->
            val b = f.batch()
            chunk.forEach { c ->
                val data: Map<String, Any?> = if (c.json == null) mapOf("id" to c.id, "_del" to true) else SyncSchema.toMap(c.json)
                b.set(ref.collection(c.coll).document(c.id.toString()),
                    data + mapOf("_s" to FieldValue.serverTimestamp(), "_d" to DeviceIds.device.toLong()))
            }
            b.commit().addOnFailureListener { e -> msg("کلاؤڈ میں محفوظ نہیں ہو سکا: " + (e.message ?: ""), true) }
        }
    }

    /** Sends every local record (first link, or "sync everything again"). */
    suspend fun uploadAll() = withContext(Dispatchers.IO) {
        val raw = db.dao()
        val all = mutableListOf<Change>()
        fun <T : Any> add(t: SyncTable<T>, rows: List<T>) { rows.forEach { all += Change(t.coll, t.idOf(it), t.toJson(it)) } }
        add(SyncSchema.customers, raw.customers()); add(SyncSchema.members, raw.allMembers()); add(SyncSchema.garments, raw.garments())
        add(SyncSchema.fields, raw.allFields()); add(SyncSchema.profiles, raw.allProfiles()); add(SyncSchema.customRates, raw.allCustomRates())
        add(SyncSchema.rateHistory, raw.rateHistory()); add(SyncSchema.services, raw.services()); add(SyncSchema.orders, raw.orders())
        add(SyncSchema.items, raw.allItems()); add(SyncSchema.statusLogs, raw.allStatusLogs()); add(SyncSchema.txns, raw.txns())
        add(SyncSchema.deliveries, raw.allDeliveries()); add(SyncSchema.expenses, raw.expenses()); add(SyncSchema.printLogs, raw.allPrintLogs())
        add(SyncSchema.audits, raw.allAudits())
        push(all)
        all.size
    }

    fun pushShopSettings(shop: ShopSettings) {
        val ref = shopRef ?: return
        ref.collection("meta").document("settings").set(SyncSchema.toMap(AppJson.encodeToJsonElement(ShopSettings.serializer(), shop).jsonObject) +
            mapOf("_s" to FieldValue.serverTimestamp()))
    }

    fun pushLogo() {
        val ref = shopRef ?: return
        val f = File(context.filesDir, "logo.png")
        if (!f.exists()) { ref.collection("meta").document("logo").delete(); return }
        ref.collection("meta").document("logo").set(mapOf("png" to Base64.encodeToString(f.readBytes(), Base64.NO_WRAP), "_s" to FieldValue.serverTimestamp()))
    }

    /** For tests: wait until all local writes reached the server. */
    suspend fun awaitPendingWrites() { fs?.waitForPendingWrites()?.await() }
}
