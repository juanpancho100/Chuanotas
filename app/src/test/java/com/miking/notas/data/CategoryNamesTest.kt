package com.miking.notas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryNamesTest {

    @Test
    fun limpiaEspaciosSobrantes() {
        assertEquals("Trabajo", CategoryNames.clean("  Trabajo  "))
        assertEquals("Ideas para casa", CategoryNames.clean("Ideas   para\tcasa"))
        assertNull(CategoryNames.clean("   "))
    }

    @Test
    fun eliminaDuplicadosSinDistinguirMayusculas() {
        assertEquals(
            listOf("casa", "Trabajo"),
            CategoryNames.normalize(listOf("Trabajo", "casa", "TRABAJO", "  Casa  ")),
        )
    }

    @Test
    fun ordenaAlfabeticamente() {
        assertEquals(
            listOf("casa", "Estudios", "Trabajo"),
            CategoryNames.normalize(listOf("Trabajo", "casa", "Estudios")),
        )
    }

    @Test
    fun contieneComparaSinDistinguirMayusculas() {
        assertTrue(CategoryNames.contains(listOf("Trabajo", "Casa"), "trabajo"))
        assertFalse(CategoryNames.contains(listOf("Trabajo", "Casa"), "Personal"))
    }

    @Test
    fun resuelveLaCategoriaExistenteOCreaUnaNueva() {
        assertEquals(
            "Trabajo",
            CategoryNames.resolve(listOf("Casa", "Trabajo"), "  trabajo  "),
        )
        assertEquals(
            "Proyecto nuevo",
            CategoryNames.resolve(listOf("Casa", "Trabajo"), "Proyecto nuevo"),
        )
        assertNull(CategoryNames.resolve(listOf("Casa"), "   "))
    }

    @Test
    fun fondoSeLeeConClaveEstable() {
        assertEquals(BackgroundStyle.PAPER, BackgroundStyle.fromStorage("paper"))
        assertEquals(BackgroundStyle.SAGE, BackgroundStyle.fromStorage("sage"))
        assertEquals(BackgroundStyle.DEFAULT, BackgroundStyle.fromStorage("desconocido"))
        assertEquals(BackgroundStyle.DEFAULT, BackgroundStyle.fromStorage(null))
    }
}
