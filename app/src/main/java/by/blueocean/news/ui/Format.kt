package by.blueocean.news.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Format {

    fun ago(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = abs(now - timestamp)
        val minutes = diff / 60_000
        val hours = diff / 3_600_000
        val days = diff / 86_400_000
        return when {
            minutes < 1 -> "только что"
            minutes < 60 -> "$minutes мин назад"
            hours < 24 -> "$hours ч назад"
            days == 1L -> "вчера"
            days < 7 -> "$days дн назад"
            else -> SimpleDateFormat("d MMM", Locale("ru")).format(Date(timestamp))
        }
    }

    fun dateTime(timestamp: Long): String =
        SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")).format(Date(timestamp))

    fun compact(number: Int): String = when {
        number >= 1_000_000 -> "${number / 1_000_000}M"
        number >= 1_000 -> "${number / 1_000}K"
        else -> number.toString()
    }
}
