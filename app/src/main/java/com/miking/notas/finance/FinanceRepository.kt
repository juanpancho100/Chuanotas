package com.miking.notas.finance

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Guarda las finanzas en finance.json, aparte de las notas. */
class FinanceRepository(context: Context) {

    private val file = File(context.filesDir, "finance.json")

    fun load(): FinanceData = runCatching {
        if (!file.exists()) return@runCatching FinanceData()
        val text = file.readText()
        if (text.isBlank()) return@runCatching FinanceData()
        FinanceData.fromJson(JSONObject(text))
    }.getOrElse { FinanceData() }

    /** Escritura atómica, igual que con las notas. */
    @Synchronized
    fun save(data: FinanceData) {
        runCatching {
            val text = data.toJson().toString()
            val temp = File(file.parentFile, "finance.json.tmp")
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                file.writeText(text)
                temp.delete()
            }
        }
    }
}
