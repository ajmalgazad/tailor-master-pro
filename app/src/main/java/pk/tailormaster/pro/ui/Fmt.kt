package pk.tailormaster.pro.ui

import pk.tailormaster.pro.data.AppSettings
import pk.tailormaster.pro.domain.Dates
import pk.tailormaster.pro.domain.Digits
import pk.tailormaster.pro.domain.Money

/** Display formatting that follows the user's settings (digits, date format, currency). */
object Fmt {
    @Volatile var settings: AppSettings = AppSettings()

    fun d(s: String): String = if (settings.urduDigits) Digits.toUrdu(s) else s

    fun money(paisa: Long): String = d(Money.plain(paisa)) + " " + settings.currencyLabel

    fun moneyPlain(paisa: Long): String = d(Money.plain(paisa))

    fun date(ms: Long?): String = if (ms == null || ms == 0L) "-" else d(Dates.format(ms, settings.dateFormat))

    fun dateTime(ms: Long?): String = if (ms == null || ms == 0L) "-" else d(Dates.format(ms, settings.dateFormat + " hh:mm a"))

    fun num(n: Number): String = d(n.toString())

    fun customerCode(id: Long) = d("C-" + id.toString().padStart(4, '0'))
    fun memberCode(id: Long) = d("M-" + id.toString().padStart(4, '0'))
    fun orderNo(id: Long) = d("B-" + id.toString().padStart(5, '0'))
    fun txnNo(id: Long) = d("T-" + id.toString().padStart(5, '0'))
    fun profileCode(id: Long) = d("P-" + id.toString().padStart(4, '0'))
    fun deliveryNo(id: Long) = d("D-" + id.toString().padStart(5, '0'))
}
