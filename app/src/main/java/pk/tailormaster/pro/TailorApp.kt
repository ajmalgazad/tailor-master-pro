package pk.tailormaster.pro

import android.app.Application
import pk.tailormaster.pro.data.AppDatabase
import pk.tailormaster.pro.data.Repository
import pk.tailormaster.pro.data.SettingsStore
import pk.tailormaster.pro.print.PrinterManager
import pk.tailormaster.pro.sync.SyncManager

class TailorApp : Application() {
    lateinit var repo: Repository
        private set
    lateinit var printer: PrinterManager
        private set
    lateinit var sync: SyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsStore(this)
        val db = AppDatabase.create(this)
        pk.tailormaster.pro.i18n.I18n.load(this)
        pk.tailormaster.pro.i18n.I18n.setLanguage(settings.value.language)
        repo = Repository(db, settings)
        printer = PrinterManager(this)
        sync = SyncManager(this, repo, db)
        try { sync.init() } catch (e: Exception) { android.util.Log.e("TailorApp", "sync init failed", e) }
    }
}
