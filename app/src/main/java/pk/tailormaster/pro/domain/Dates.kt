package pk.tailormaster.pro.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Dates {
    const val DAY = 24L * 60 * 60 * 1000

    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun endOfDay(ms: Long): Long = startOfDay(ms) + DAY - 1

    fun startOfMonth(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(ms)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    fun endOfMonth(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfMonth(ms)
        add(Calendar.MONTH, 1)
    }.timeInMillis - 1

    fun addDays(ms: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        add(Calendar.DAY_OF_MONTH, days)
    }.timeInMillis

    fun today(): Long = startOfDay(System.currentTimeMillis())

    fun format(ms: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).format(Date(ms))

    fun inRange(ms: Long, from: Long, to: Long) = ms in from..to
}
