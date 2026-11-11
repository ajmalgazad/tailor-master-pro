package pk.tailormaster.pro.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local offline database (SQLite via Room).
 *
 * MIGRATION RULE: when an entity changes, bump [VERSION] and add a Migration in
 * [AppMigrations]. Never use destructive migration — shop data must survive updates.
 */
@Database(
    entities = [
        Customer::class, FamilyMember::class, Garment::class, MeasurementField::class,
        MeasurementProfile::class, CustomRate::class, RateHistory::class, ExtraService::class,
        Order::class, OrderItem::class, StatusLog::class, Txn::class, Delivery::class,
        Expense::class, PrintLog::class, AuditLog::class,
        // v2: kaarigar (workers)
        Worker::class, WorkJob::class, WorkerPayment::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
    // v1 -> v2 only adds new tables, so Room writes the migration itself (schemas/ holds both versions).
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TailorDao
    abstract fun workerDao(): WorkerDao

    companion object {
        const val VERSION = 2
        const val NAME = "tailor_master.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*AppMigrations.all)
                .build()
    }
}

object AppMigrations {
    // Example for the future:
    // val M1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { ... } }
    val all: Array<androidx.room.migration.Migration> = arrayOf()
}
