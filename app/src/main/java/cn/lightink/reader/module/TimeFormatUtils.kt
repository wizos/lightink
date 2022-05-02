package cn.lightink.reader.module

import java.text.SimpleDateFormat
import java.util.*

object TimeFormat {

    private val optionDate1 by lazy { DateFormatOptions.newOptionDate1() }
    private val optionDate2 by lazy { DateFormatOptions.newOptionDate2() }
    private val optionTime1 by lazy { DateFormatOptions.newOptionTime1() }

    fun parse(source: String): Long {
        return try {
            if (source.isBlank()) return System.currentTimeMillis()
            val datetime = StringBuilder()
            val format = StringBuilder()
            arrayOf(optionDate1, optionDate2).forEach { option ->
                option.regex.find(source)?.value?.run {
                    datetime.append(this)
                    format.append(option.format)
                }
            }
            arrayOf(optionTime1).forEach { option ->
                option.regex.find(source)?.value?.run {
                    datetime.append(" $this")
                    format.append(" ${option.format}")
                }
            }
            SimpleDateFormat(format.toString(), Locale.ENGLISH).parse(datetime.toString())!!.time
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun format(time: Long): String {
        val difference = (System.currentTimeMillis() - time) / 1000
        return when {
            difference < 60 -> "刚刚"
            difference < 60 * 60 -> "${difference / 60}分钟前"
            difference < 60 * 60 * 24 -> "${difference / 60 / 60}小时前"
            difference < 60 * 60 * 24 * 2 -> "昨天"
            else -> "${difference / (60 * 60 * 24)}天前"
        }
    }

    data class DateFormatOptions(val regex: Regex, val format: String) {

        companion object {

            // 20 May 2019
            fun newOptionDate1() = DateFormatOptions("""[0-9]{2} [a-zA-Z]{3} [0-9]{4}""".toRegex(), "dd MMM yyyy")

            // 2019-05-20
            fun newOptionDate2() = DateFormatOptions("""[0-9]{4}-[0-9]{2}-[0-9]{2}""".toRegex(), "yyyy-MM-dd")

            // 12:00:01
            fun newOptionTime1() = DateFormatOptions("""[0-9]{2}:[0-9]{2}:[0-9]{2}""".toRegex(), "HH:mm:ss")

        }

    }

}