package com.calorie.diary.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {

    private val dateFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    fun format(calendar: Calendar): String = dateFmt.format(calendar.time)

    fun todayString(): String = dateFmt.format(Date())

    fun parse(date: String): Calendar =
        Calendar.getInstance().apply { time = dateFmt.parse(date)!! }

    fun friendly(date: String, locale: Locale = Locale.getDefault()): String {
        val cal = parse(date)
        val pattern = if (locale.language in CJK) "M月d日 EEE" else "d MMM, EEE"
        return SimpleDateFormat(pattern, locale).format(cal.time)
    }

    fun monthLabel(date: String, locale: Locale = Locale.getDefault()): String {
        val cal = parse(date)
        val pattern = if (locale.language in CJK) "yyyy年M月" else "MMMM yyyy"
        return SimpleDateFormat(pattern, locale).format(cal.time)
    }

    private val CJK = setOf("zh", "ja")

    fun monthRange(anchor: String): Pair<String, String> {
        val cal = parse(anchor)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = format(cal)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = format(cal)
        return start to end
    }

    fun addDays(date: String, delta: Int): String {
        val cal = parse(date)
        cal.add(Calendar.DAY_OF_MONTH, delta)
        return format(cal)
    }

    fun calendarOf(date: String): Calendar = parse(date)
}
