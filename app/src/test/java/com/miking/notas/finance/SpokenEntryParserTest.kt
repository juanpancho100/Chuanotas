package com.miking.notas.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SpokenEntryParserTest {

    private val today = LocalDate.of(2026, 10, 23)
    private val categories = DefaultFinanceCategories.all +
        FinanceCategory("custom-pets", "Mascotas", EntryType.EXPENSE, SpendingGroup.NEED)

    private fun parse(text: String) = SpokenEntryParser.parse(text, categories, today)

    @Test
    fun gastoConCategoriaPorPalabraClave() {
        val entry = parse("Gasté 25 soles en almuerzo")
        assertEquals(EntryType.EXPENSE, entry.type)
        assertEquals(2500L, entry.amountCents)
        assertEquals("exp-food", entry.categoryId)
        assertEquals(today, entry.date)
    }

    @Test
    fun ayerYServicios() {
        val entry = parse("ayer pagué 120 de luz")
        assertEquals(12000L, entry.amountCents)
        assertEquals("exp-services", entry.categoryId)
        assertEquals(today.minusDays(1), entry.date)
    }

    @Test
    fun ingresoDeSueldo() {
        val entry = parse("me pagaron 1500 de sueldo")
        assertEquals(EntryType.INCOME, entry.type)
        assertEquals(150000L, entry.amountCents)
        assertEquals("inc-salary", entry.categoryId)
    }

    @Test
    fun solesConCentimos() {
        assertEquals(1250L, parse("taxi 12 con 50").amountCents)
        assertEquals(1250L, parse("taxi 12.50").amountCents)
        assertEquals("exp-transport", parse("taxi 12 con 50").categoryId)
    }

    @Test
    fun categoriaPropiaPorNombre() {
        assertEquals("custom-pets", parse("gasté 40 en mascotas").categoryId)
    }

    @Test
    fun sinImporteNiCategoria() {
        val entry = parse("algo raro")
        assertNull(entry.amountCents)
        assertNull(entry.categoryId)
    }
}
