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

    private fun code(p: String, id: Long, pad: Int) = d(pk.tailormaster.pro.data.DeviceIds.code(p, id, pad))
    fun customerCode(id: Long) = code("C", id, 4)
    fun memberCode(id: Long) = code("M", id, 4)
    fun orderNo(id: Long) = code("B", id, 5)
    fun txnNo(id: Long) = code("T", id, 5)
    fun profileCode(id: Long) = code("P", id, 4)
    fun deliveryNo(id: Long) = code("D", id, 5)
}
