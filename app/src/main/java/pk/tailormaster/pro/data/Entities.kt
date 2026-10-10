package pk.tailormaster.pro.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * All money values are stored as Long in PAISA (1 rupee = 100 paisa) so that
 * calculations never suffer from floating point rounding errors.
 * All dates are stored as epoch milliseconds.
 */

@Serializable
@Entity(tableName = "customers", indices = [Index("mobile"), Index("name")])
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fatherName: String = "",
    val mobile: String = "",
    val whatsapp: String = "",
    val address: String = "",
    val city: String = "",
    val area: String = "",
    val category: String = "",
    val regDate: Long = System.currentTimeMillis(),
    val openingBalance: Long = 0,
    val notes: String = "",
    val active: Boolean = true,
    val mergedIntoId: Long? = null,
    val sample: Boolean = false,
)

@Serializable
@Entity(tableName = "family_members", indices = [Index("customerId")])
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val name: String,
    val relation: String,
    val ageGroup: String = "",
    val mobile: String = "",
    val fittingPref: String = "",
    val notes: String = "",
    val isSelf: Boolean = false,
    val active: Boolean = true,
)

object Gender {
    const val MEN = "MEN"
    const val WOMEN = "WOMEN"
    const val KIDS = "KIDS"
    const val OTHER = "OTHER"
    val all = listOf(MEN, WOMEN, KIDS, OTHER)
    fun label(g: String) = when (g) {
        MEN -> "مردانہ"
        WOMEN -> "زنانہ"
        KIDS -> "بچے"
        else -> "دیگر"
    }
}

@Serializable
@Entity(tableName = "garments")
data class Garment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val gender: String = Gender.MEN,
    val sortOrder: Int = 0,
    val defaultRate: Long = 0,
    val receiptDesc: String = "",
    val active: Boolean = true,
)

@Serializable
@Entity(tableName = "measurement_fields", indices = [Index("garmentId")])
data class MeasurementField(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val garmentId: Long,
    val label: String,
    val unit: String = "",
    val required: Boolean = false,
    val sortOrder: Int = 0,
    val active: Boolean = true,
)

@Serializable
data class MeasureValue(val fieldId: Long = 0, val label: String, val value: String)

@Serializable
@Entity(
    tableName = "measurement_profiles",
    indices = [Index("customerId"), Index("memberId"), Index("garmentId")]
)
data class MeasurementProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val memberId: Long,
    val garmentId: Long,
    val name: String,
    val date: Long = System.currentTimeMillis(),
    val unit: String = "انچ",
    val valuesJson: String = "[]",
    val fitting: String = "",
    val instructions: String = "",
    val notes: String = "",
    val isDefault: Boolean = false,
    /** Older versions are kept (archived) when a profile is updated. */
    val archived: Boolean = false,
    val previousId: Long? = null,
    val lastUsed: Long? = null,
)

@Serializable
@Entity(tableName = "custom_rates", indices = [Index("garmentId"), Index("customerId")])
data class CustomRate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val garmentId: Long,
    val customerId: Long? = null,
    val memberId: Long? = null,
    val rate: Long,
    val active: Boolean = true,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "rate_history", indices = [Index("garmentId")])
data class RateHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val garmentId: Long,
    val customerId: Long? = null,
    val memberId: Long? = null,
    val oldRate: Long,
    val newRate: Long,
    val at: Long = System.currentTimeMillis(),
    val note: String = "",
)

object ServiceKind {
    const val URGENT = "URGENT"
    const val EMBROIDERY = "EMBROIDERY"
    const val ALTERATION = "ALTERATION"
    const val OTHER = "OTHER"
    val all = listOf(URGENT, EMBROIDERY, ALTERATION, OTHER)
    fun label(k: String) = when (k) {
        URGENT -> "ارجنٹ"
        EMBROIDERY -> "کڑھائی / ڈیزائن"
        ALTERATION -> "آلٹریشن / مرمت"
        else -> "دیگر سروس"
    }
}

@Serializable
@Entity(tableName = "services")
data class ExtraService(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = ServiceKind.OTHER,
    val amount: Long = 0,
    val active: Boolean = true,
)

object OrderStatus {
    const val NEW = "NEW"
    const val MEASURED = "MEASURED"
    const val CUTTING = "CUTTING"
    const val STITCHING = "STITCHING"
    const val FINISHING = "FINISHING"
    const val READY = "READY"
    const val DELIVERED = "DELIVERED"
    const val CANCELLED = "CANCELLED"
    val workflow = listOf(NEW, MEASURED, CUTTING, STITCHING, FINISHING, READY, DELIVERED)
    val all = workflow + CANCELLED
    val open = listOf(NEW, MEASURED, CUTTING, STITCHING, FINISHING)
    fun label(s: String) = when (s) {
        NEW -> "نئی بکنگ"
        MEASURED -> "ناپ مکمل"
        CUTTING -> "کٹنگ جاری"
        STITCHING -> "سلائی جاری"
        FINISHING -> "فنشنگ"
        READY -> "تیار ہے"
        DELIVERED -> "ڈیلیور ہو گیا"
        CANCELLED -> "منسوخ"
        else -> s
    }
}

@Serializable
data class Charge(val name: String, val amount: Long)

object DiscountType {
    const val AMOUNT = "AMOUNT"
    const val PERCENT = "PERCENT"
}

@Serializable
@Entity(
    tableName = "orders",
    indices = [Index("customerId"), Index("bookingDate"), Index("deliveryDate"), Index("status")]
)
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val memberId: Long,
    val bookingDate: Long = System.currentTimeMillis(),
    val deliveryDate: Long = System.currentTimeMillis(),
    val urgent: Boolean = false,
    val instructions: String = "",
    val internalNotes: String = "",
    val status: String = OrderStatus.NEW,
    val isDraft: Boolean = false,
    val chargesJson: String = "[]",
    val discountType: String = DiscountType.AMOUNT,
    val discountInput: Double = 0.0,
    val itemsTotal: Long = 0,
    val chargesTotal: Long = 0,
    val discountAmount: Long = 0,
    val netAmount: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val rebookedFromId: Long? = null,
)

@Serializable
data class MeasureSnapshot(
    val profileId: Long? = null,
    val profileName: String = "",
    val unit: String = "",
    val date: Long = 0,
    val fitting: String = "",
    val instructions: String = "",
    val values: List<MeasureValue> = emptyList(),
)

@Serializable
@Entity(tableName = "order_items", indices = [Index("orderId"), Index("garmentId")])
data class OrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val garmentId: Long,
    val garmentName: String,
    val memberId: Long,
    val memberName: String,
    val qty: Int,
    val rate: Long,
    val total: Long,
    val profileId: Long? = null,
    /** Frozen copy of the measurements at booking time — never changes afterwards. */
    val measurementJson: String = "",
    val status: String = OrderStatus.NEW,
    val delivered: Boolean = false,
    val deliveredAt: Long? = null,
    val deliveryId: Long? = null,
    val notes: String = "",
)

@Serializable
@Entity(tableName = "status_log", indices = [Index("orderId")])
data class StatusLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val itemId: Long? = null,
    val fromStatus: String,
    val toStatus: String,
    val at: Long = System.currentTimeMillis(),
    val note: String = "",
)

object TxnType {
    const val PAYMENT = "PAYMENT"
    const val REFUND = "REFUND"
    const val ADJ_DEBIT = "ADJ_DEBIT"
    const val ADJ_CREDIT = "ADJ_CREDIT"
    const val REVERSAL = "REVERSAL"
    fun label(t: String) = when (t) {
        PAYMENT -> "وصولی"
        REFUND -> "واپسی (ریفنڈ)"
        ADJ_DEBIT -> "ایڈجسٹمنٹ (بقایا میں اضافہ)"
        ADJ_CREDIT -> "ایڈجسٹمنٹ (بقایا میں کمی)"
        REVERSAL -> "منسوخ شدہ اندراج (ریورسل)"
        else -> t
    }

    /** Effect on the customer's outstanding balance. Positive = customer owes more. */
    fun effect(type: String, amount: Long): Long = when (type) {
        PAYMENT, ADJ_CREDIT -> -amount
        REFUND, ADJ_DEBIT -> amount
        else -> 0
    }

    fun isCash(type: String) = type == PAYMENT || type == REFUND
}

object PayMethod {
    const val CASH = "نقد"
    const val BANK = "بینک ٹرانسفر"
    const val DIGITAL = "ڈیجیٹل (ایزی پیسہ / جاز کیش)"
    const val OTHER = "دیگر"
    val all = listOf(CASH, BANK, DIGITAL, OTHER)
}

@Serializable
@Entity(tableName = "txns", indices = [Index("customerId"), Index("orderId"), Index("date")])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val orderId: Long? = null,
    val date: Long = System.currentTimeMillis(),
    val type: String,
    val amount: Long,
    /** Signed effect on balance (see TxnType.effect). Reversal = minus original effect. */
    val signedEffect: Long,
    /** True when real money moved (payments, refunds and their reversals). */
    val cash: Boolean,
    val method: String = PayMethod.CASH,
    val reference: String = "",
    val notes: String = "",
    val reversedById: Long? = null,
    val reversesId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "deliveries", indices = [Index("orderId")])
data class Delivery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val date: Long = System.currentTimeMillis(),
    val itemIdsJson: String = "[]",
    val notes: String = "",
    val paymentTxnId: Long? = null,
    val forcedComplete: Boolean = false,
)

@Serializable
@Entity(tableName = "expenses", indices = [Index("date")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long = System.currentTimeMillis(),
    val category: String,
    val amount: Long,
    val method: String = PayMethod.CASH,
    val notes: String = "",
    val voided: Boolean = false,
    val sample: Boolean = false,
)

object ExpenseCategories {
    val defaults = listOf(
        "دکان کا کرایہ", "بجلی کا بل", "ملازمین کی تنخواہ",
        "دھاگہ اور دیگر سامان", "مرمت", "دیگر اخراجات"
    )
}

object ReceiptKind {
    const val BOOKING = "BOOKING"
    const val MEASUREMENT = "MEASUREMENT"
    const val PAYMENT = "PAYMENT"
    const val DELIVERY = "DELIVERY"
    const val LEDGER = "LEDGER"
    const val CUSTOMER = "CUSTOMER"
    const val TEST = "TEST"
    fun label(k: String) = when (k) {
        BOOKING -> "بکنگ رسید"
        MEASUREMENT -> "ناپ کی پرچی"
        PAYMENT -> "وصولی رسید"
        DELIVERY -> "ڈیلیوری رسید"
        LEDGER -> "کھاتہ اسٹیٹمنٹ"
        CUSTOMER -> "کسٹمر معلومات"
        TEST -> "ٹیسٹ پرنٹ"
        else -> k
    }
}

@Serializable
@Entity(tableName = "print_log", indices = [Index("at")])
data class PrintLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val refId: Long,
    val title: String = "",
    val at: Long = System.currentTimeMillis(),
    /** OK = printer confirmed, SENT = data sent but printer gave no confirmation, FAILED. */
    val status: String,
    val message: String = "",
)

@Serializable
@Entity(tableName = "audit_log", indices = [Index("at")])
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long = System.currentTimeMillis(),
    val entity: String,
    val entityId: Long,
    val action: String,
    val detail: String = "",
)

/** Query result helpers */
data class CustomerBalance(val customerId: Long, val balance: Long)

data class GarmentCount(val garmentName: String, val qty: Long, val amount: Long)
