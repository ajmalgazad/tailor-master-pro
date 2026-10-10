package pk.tailormaster.pro

import android.app.Application
import pk.tailormaster.pro.data.AppDatabase
import pk.tailormaster.pro.data.Repository
import pk.tailormaster.pro.data.SettingsStore
import pk.tailormaster.pro.print.PrinterManager

class TailorApp : Application() {
    lateinit var repo: Repository
        private set
    lateinit var printer: PrinterManager
        private set

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsStore(this)
        repo = Repository(AppDatabase.create(this), settings)
        printer = PrinterManager(this)
    }
}
