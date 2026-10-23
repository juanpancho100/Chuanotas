package com.miking.notas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySoundPlanTest {

    @Test
    fun asignaUnSonidoDistintoACadaCategoria() {
        val plan = planCategorySounds(
            categories = listOf("Casa", "Salud", "Trabajo"),
            existing = emptyMap(),
            available = listOf("tono-a", "tono-b", "tono-c"),
            fallback = "predeterminado",
        )

        assertEquals(listOf("Casa", "Salud", "Trabajo"), plan.keys.toList())
        assertEquals(3, plan.values.toSet().size)
        assertTrue(plan.values.all { it.startsWith("tono-") })
    }

    @Test
    fun conservaElSonidoElegidoYCompletaSoloLasNuevas() {
        val plan = planCategorySounds(
            categories = listOf("Casa", "Salud", "Trabajo"),
            existing = mapOf("trabajo" to "tono-b"),
            available = listOf("tono-a", "tono-b", "tono-c"),
            fallback = "predeterminado",
        )

        assertEquals("tono-b", plan["Trabajo"])
        assertEquals(setOf("tono-a", "tono-b", "tono-c"), plan.values.toSet())
    }

    @Test
    fun eliminaSonidosDeCategoriasQueYaNoExisten() {
        val plan = planCategorySounds(
            categories = listOf("Casa"),
            existing = mapOf("Casa" to "tono-a", "Antigua" to "tono-b"),
            available = listOf("tono-a", "tono-b"),
            fallback = "predeterminado",
        )

        assertEquals(mapOf("Casa" to "tono-a"), plan)
    }

    @Test
    fun usaElPredeterminadoSiNoHaySuficientesTonos() {
        val plan = planCategorySounds(
            categories = listOf("Casa", "Salud", "Trabajo", "Viajes"),
            existing = emptyMap(),
            available = listOf("tono-a", "tono-b"),
            fallback = "predeterminado",
        )

        assertEquals("tono-a", plan["Casa"])
        assertEquals("tono-b", plan["Salud"])
        assertEquals("predeterminado", plan["Trabajo"])
        assertEquals("predeterminado", plan["Viajes"])
        assertFalse(plan.isEmpty())
    }

    @Test
    fun sinTonosDisponiblesUsaElPredeterminado() {
        val plan = planCategorySounds(
            categories = listOf("Casa", "Trabajo"),
            existing = emptyMap(),
            available = emptyList(),
            fallback = "predeterminado",
        )

        assertEquals(
            mapOf("Casa" to "predeterminado", "Trabajo" to "predeterminado"),
            plan,
        )
    }
}
