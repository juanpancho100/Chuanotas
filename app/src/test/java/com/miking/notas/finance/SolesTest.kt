package com.miking.notas.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SolesTest {

    @Test
    fun formateaConMilesYDosDecimales() {
        assertEquals("S/ 1,234.50", Soles.format(123450))
        assertEquals("S/ 0.05", Soles.format(5))
        assertEquals("-S/ 20.00", Soles.format(-2000))
    }

    @Test
    fun formatoCortoQuitaDecimalesCero() {
        assertEquals("S/ 1,200", Soles.formatShort(120000))
        assertEquals("S/ 12.50", Soles.formatShort(1250))
    }

    @Test
    fun interpretaDistintasFormasDeEscribir() {
        assertEquals(1200L, Soles.parse("12"))
        assertEquals(1250L, Soles.parse("12.5"))
        assertEquals(1250L, Soles.parse("12,50"))
        assertEquals(120000L, Soles.parse("1,200"))
        assertEquals(120050L, Soles.parse("1,200.50"))
        assertEquals(120050L, Soles.parse("1.200,50"))
        assertEquals(3000L, Soles.parse("S/ 30"))
    }

    @Test
    fun rechazaImportesInvalidos() {
        assertNull(Soles.parse(""))
        assertNull(Soles.parse("0"))
        assertNull(Soles.parse("abc"))
        assertNull(Soles.parse("12.345"))
        assertNull(Soles.parse("-5"))
    }

    @Test
    fun textoEditable() {
        assertEquals("12", Soles.toInput(1200))
        assertEquals("12.05", Soles.toInput(1205))
    }
}
