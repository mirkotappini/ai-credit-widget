package com.patchouli.deepseekwidget.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formattazione unica per app e widget.
 * Locale italiano: migliaia con punto, decimali con virgola, simbolo prima.
 */
object Format {

    private val shortTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ITALY)
    private val dayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale.ITALY)
    private val updatedDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ITALY)
    private val snapshotDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ITALY)

    fun money(value: BigDecimal): String = currency(value, 2, true)

    fun money(value: Double): String = money(BigDecimal.valueOf(value))

    /** Saldo del widget 2x2: centesimi sotto 100, intero fino a 9.999, poi k/M. */
    fun moneyWidget2x2(value: BigDecimal): String = when {
        value.abs() < HUNDRED -> currency(value, 2, true)
        value.abs() < TEN_THOUSAND -> currencyFloor(value, 0, true)
        value.abs() < HUNDRED_THOUSAND -> compactFloor(value, "k", 1)
        value.abs() < MILLION -> compactFloor(value, "k", 0)
        else -> compactFloor(value, "M", 1)
    }

    /** Saldo del widget 4x2: centesimi fino a 999.999,99, poi M. */
    fun moneyWidget4x2(value: BigDecimal): String =
        if (value.abs() < MILLION) currency(value, 2, true) else compactFloor(value, "M", 1)

    /** Metrica del widget 4x2: centesimi sotto 1.000, intero fino a 9.999, poi k. */
    fun moneyWidgetMetric(value: BigDecimal): String = when {
        value.abs() < THOUSAND -> currency(value, 2, true)
        value.abs() < TEN_THOUSAND -> currencyFloor(value, 0, true)
        value.abs() < HUNDRED_THOUSAND -> compactFloor(value, "k", 1)
        value.abs() < MILLION -> compactFloor(value, "k", 0)
        else -> compactFloor(value, "M", 1)
    }

    /** Etichette degli assi: niente simbolo, 2 decimali sotto 100, poi intero/k/M. */
    fun axisMoney(value: Double): String {
        val amount = BigDecimal.valueOf(value)
        return when {
            amount.abs() < HUNDRED -> decimal(amount, 2, true, RoundingMode.HALF_UP)
            amount.abs() < TEN_THOUSAND -> decimal(amount, 0, true, RoundingMode.HALF_UP)
            amount.abs() < HUNDRED_THOUSAND -> compactNumber(amount, "k", 1)
            amount.abs() < MILLION -> compactNumber(amount, "k", 0)
            else -> compactNumber(amount, "M", 1)
        }
    }

    fun shortTime(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(shortTimeFormatter)

    /** Ora UTC dei bucket OpenAI; mai convertita in locale. */
    fun utcTime(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC).format(shortTimeFormatter)

    fun utcDayLabel(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC).format(dayFormatter)

    fun utcDate(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC).format(updatedDateFormatter)

    fun updatedDate(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(updatedDateFormatter)

    fun snapshotTime(ts: Long): String {
        val zoned = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
        return if (zoned.toLocalDate() == LocalDate.now()) {
            zoned.format(shortTimeFormatter)
        } else {
            zoned.format(snapshotDateFormatter)
        }
    }

    fun dateTime(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(snapshotDateFormatter)

    fun dayLabel(day: LocalDate): String = day.format(dayFormatter)

    fun dayOfMonth(day: LocalDate): String = day.dayOfMonth.toString()

    fun startOfDayMillis(day: LocalDate): Long =
        day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun currency(value: BigDecimal, decimals: Int, grouping: Boolean): String {
        val sign = if (value.signum() < 0) "−" else ""
        return sign + "$" + decimal(value.abs(), decimals, grouping, RoundingMode.HALF_UP)
    }

    private fun currencyFloor(value: BigDecimal, decimals: Int, grouping: Boolean): String {
        val sign = if (value.signum() < 0) "−" else ""
        return sign + "$" + decimal(value.abs(), decimals, grouping, RoundingMode.DOWN)
    }

    private fun decimal(
        value: BigDecimal,
        decimals: Int,
        grouping: Boolean,
        rounding: RoundingMode
    ): String {
        val symbols = DecimalFormatSymbols(Locale.ITALY)
        val pattern = if (grouping) "#,##0" else "0"
        val format = DecimalFormat(pattern, symbols).apply {
            roundingMode = rounding
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            isGroupingUsed = grouping
        }
        return format.format(value)
    }

    private fun compactFloor(value: BigDecimal, suffix: String, decimals: Int): String {
        val sign = if (value.signum() < 0) "−" else ""
        return sign + "$" + compactNumber(value.abs(), suffix, decimals)
    }

    private fun compactNumber(value: BigDecimal, suffix: String, decimals: Int): String {
        val divisor = if (suffix == "M") MILLION else THOUSAND
        val scaled = value.divide(divisor, decimals, RoundingMode.HALF_UP)
        return decimal(scaled, decimals, false, RoundingMode.DOWN) + suffix
    }

    private val HUNDRED: BigDecimal = BigDecimal("100")
    private val THOUSAND: BigDecimal = BigDecimal("1000")
    private val TEN_THOUSAND: BigDecimal = BigDecimal("10000")
    private val HUNDRED_THOUSAND: BigDecimal = BigDecimal("100000")
    private val MILLION: BigDecimal = BigDecimal("1000000")
}
