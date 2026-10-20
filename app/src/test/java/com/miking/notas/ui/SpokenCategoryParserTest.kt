package com.miking.notas.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenCategoryParserTest {

    private val existing = listOf("Casa", "Trabajo")

    @Test
    fun usaLaCategoriaExistenteSinDistinguirMayusculas() {
        assertEquals("Trabajo", resolveSpokenCategory("trabajo", existing))
        assertEquals("Casa", resolveSpokenCategory("CASA", existing))
    }

    @Test
    fun creaUnaCategoriaNuevaConLoDictado() {
        assertEquals("Proyecto nuevo", resolveSpokenCategory("Proyecto nuevo", existing))
        assertEquals("Salud", resolveSpokenCategory("  Salud.  ", existing))
    }

    @Test
    fun quitaFormulasHabitualesAntesDeLaCategoria() {
        assertEquals("Ideas", resolveSpokenCategory("categoría: Ideas", existing))
        assertEquals("Casa", resolveSpokenCategory("la categoría es casa", existing))
        assertEquals("Salud", resolveSpokenCategory("para la categoría Salud", existing))
        assertEquals(
            "Trabajo",
            resolveSpokenCategory("quiero una categoría llamada Trabajo", existing),
        )
    }

    @Test
    fun permiteOmitirLaCategoria() {
        assertNull(resolveSpokenCategory("sin categoría", existing))
        assertNull(resolveSpokenCategory("Sin etiqueta", existing))
        assertNull(resolveSpokenCategory("ninguna", existing))
        assertNull(resolveSpokenCategory("no quiero categoría", existing))
        assertNull(resolveSpokenCategory("   ", existing))
    }
}
