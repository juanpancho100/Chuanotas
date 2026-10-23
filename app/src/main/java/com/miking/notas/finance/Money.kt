package com.miking.notas.finance

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Importes en soles. Se guardan siempre en céntimos (Long) para que las sumas
 * no arrastren errores de redondeo como pasaría con Double.
 */
object Soles {

    private val symbols = DecimalFormatSymbols(Locale.US)

    /** «S/ 1,234.50», con signo menos delante si es negativo. */
    fun format(cents: Long): String {
        val pattern = DecimalFormat("#,##0.00", symbols)
        val text = pattern.format(abs(cents) / 100.0)
        return if (cents < 0) "-S/ $text" else "S/ $text"
    }

    /** Sin decimales si son cero: «S/ 1,200» o «S/ 12.50». Para gráficos y chips. */
    fun formatShort(cents: Long): String {
        if (cents % 100 != 0L) return format(cents)
        val text = DecimalFormat("#,##0", symbols).format(abs(cents) / 100)
        return if (cents < 0) "-S/ $text" else "S/ $text"
    }

    /**
     * Interpreta lo que escribe el usuario: «12», «12.5», «12,50», «1,200.00»
     * o «S/ 30». Devuelve null si no es un importe positivo válido.
     */
    fun parse(text: String): Long? {
        var clean = text.trim()
            .replace("S/", "", ignoreCase = true)
            .replace("s/.", "", ignoreCase = true)
            .replace(" ", "")
        if (clean.isEmpty()) return null

        val lastComma = clean.lastIndexOf(',')
        val lastDot = clean.lastIndexOf('.')
        clean = when {
            // «1,200.50» → la coma separa miles.
            lastComma >= 0 && lastDot > lastComma -> clean.replace(",", "")
            // «1.200,50» → el punto separa miles.
            lastDot >= 0 && lastComma > lastDot -> clean.replace(".", "").replace(',', '.')
            // «12,50» → coma decimal; «1,200» → miles.
            lastComma >= 0 -> {
                val decimals = clean.length - lastComma - 1
                if (decimals == 3) clean.replace(",", "") else clean.replace(',', '.')
            }
            else -> clean
        }

        if (!clean.matches(Regex("""\d+(\.\d{1,2})?"""))) return null
        val parts = clean.split('.')
        val whole = parts[0].toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L
        val cents = whole * 100 + fraction
        return cents.takeIf { it > 0 }
    }

    /** Texto editable a partir de céntimos: 1250 → «12.50», 1200 → «12». */
    fun toInput(cents: Long): String =
        if (cents % 100 == 0L) (cents / 100).toString() else "%d.%02d".format(cents / 100, cents % 100)
}
