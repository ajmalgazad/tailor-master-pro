package pk.tailormaster.pro.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Central place for every financial calculation in the app.
 * All amounts are Long PAISA. Pure Kotlin so it is covered by JVM unit tests.
 */
object Calc {

    data class Line(val qty: Int, val rate: Long)

    data class Totals(
        val itemsTotal: Long,
        val chargesTotal: Long,
        val subTotal: Long,
        val discount: Long,
        val net: Long,
    )

    fun lineTotal(qty: Int, rate: Long): Long = Math.multiplyExact(qty.toLong(), rate)

    /**
     * Items Total + Additional Charges − Discount = Net Amount.
     * [discountInput] is rupees when type is AMOUNT, percent (0–100) when PERCENT.
     */
    fun totals(lines: List<Line>, charges: List<Long>, discountIsPercent: Boolean, discountInput: Double): Totals {
        val items = lines.sumOf { lineTotal(it.qty, it.rate) }
        val chg = charges.sum()
        val sub = items + chg
        val disc = discountAmount(sub, discountIsPercent, discountInput)
        return Totals(items, chg, sub, disc, sub - disc)
    }

    fun discountAmount(subTotal: Long, isPercent: Boolean, input: Double): Long {
        if (input <= 0.0 || subTotal <= 0) return 0
        val d = if (isPercent) {
            BigDecimal(subTotal).multiply(BigDecimal.valueOf(input))
                .divide(BigDecimal(100), 0, RoundingMode.HALF_UP).toLong()
        } else {
            BigDecimal.valueOf(input).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        }
        return d.coerceIn(0, subTotal)
    }

    /** Returns an Urdu error message, or null when everything is valid. */
    fun validate(lines: List<Line>, charges: List<Long>, discountIsPercent: Boolean, discountInput: Double): String? {
        if (lines.isEmpty()) return "کم از کم ایک کپڑا شامل کریں"
        if (lines.any { it.qty <= 0 }) return "تعداد صفر یا منفی نہیں ہو سکتی"
        if (lines.any { it.rate < 0 }) return "ریٹ منفی نہیں ہو سکتا"
        if (charges.any { it < 0 }) return "اضافی چارجز منفی نہیں ہو سکتے"
        if (discountInput < 0) return "رعایت منفی نہیں ہو سکتی"
        if (discountIsPercent && discountInput > 100) return "رعایت ۱۰۰ فیصد سے زیادہ نہیں ہو سکتی"
        val sub = lines.sumOf { lineTotal(it.qty, it.rate) } + charges.sum()
        if (!discountIsPercent) {
            val d = BigDecimal.valueOf(discountInput).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
            if (d > sub) return "رعایت کل رقم سے زیادہ نہیں ہو سکتی"
        }
        return null
    }

    /** Net Amount − Payments Applied = Outstanding. Payments are positive numbers here. */
    fun outstanding(net: Long, paid: Long): Long = net - paid

    /**
     * Customer balance = opening balance + charges of confirmed, non-cancelled orders
     * + signed effect of every ledger transaction (payments are negative, refunds positive...).
     */
    fun customerBalance(opening: Long, orderNets: List<Long>, txnEffects: List<Long>): Long =
        opening + orderNets.sum() + txnEffects.sum()

    /** Parses "1500", "1,500", "1500.50" or Urdu digits into paisa. Null when invalid or negative. */
    fun parseMoney(text: String): Long? {
        val t = Digits.toLatin(text).replace(",", "").replace("Rs", "").replace("روپے", "").trim()
        if (t.isEmpty()) return 0
        return try {
            val bd = BigDecimal(t)
            if (bd.signum() < 0) null else bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun parseInt(text: String): Int? = Digits.toLatin(text).trim().toIntOrNull()

    fun parseDouble(text: String): Double? {
        val t = Digits.toLatin(text).trim()
        if (t.isEmpty()) return 0.0
        return t.toDoubleOrNull()
    }

    /** Plain number text for an edit box (no thousands separator). */
    fun moneyInput(paisa: Long): String {
        val bd = BigDecimal(paisa).divide(BigDecimal(100), 2, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
    }
}

object Digits {
    private const val URDU_ZERO = '۰'

    fun toLatin(s: String): String = buildString(s.length) {
        for (ch in s) {
            append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    '٫' -> '.'
                    else -> ch
                }
            )
        }
    }

    fun toUrdu(s: String): String = buildString(s.length) {
        for (ch in s) append(if (ch in '0'..'9') URDU_ZERO + (ch - '0') else ch)
    }
}

object Money {
    /** "12,345" or "12,345.50" (no currency label). */
    fun plain(paisa: Long): String {
        val neg = paisa < 0
        val abs = kotlin.math.abs(paisa)
        val rupees = abs / 100
        val p = abs % 100
        val r = String.format(java.util.Locale.US, "%,d", rupees)
        val s = if (p == 0L) r else r + "." + p.toString().padStart(2, '0')
        return if (neg) "-$s" else s
    }
}
