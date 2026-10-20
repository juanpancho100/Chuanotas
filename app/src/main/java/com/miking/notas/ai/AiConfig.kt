package com.miking.notas.ai

/** Ajustes de la conexión con DeepSeek. */
internal object AiConfig {

    /**
     * Modelos disponibles. "deepseek-v4-pro" se sirve ahora con la misma familia
     * que flash, así que ambos tienen el mismo precio.
     */
    val MODELS = listOf("deepseek-flash", "deepseek-v4-pro")

    const val DEFAULT_MODEL = "deepseek-flash"

    /** Clave bajo la que se guarda la API key cifrada. */
    const val API_KEY_SECRET = "deepseek_api_key"

    fun normalizeModel(value: String?): String =
        if (value != null && value in MODELS) value else DEFAULT_MODEL

    /** Nombre legible del modelo para mostrarlo en Ajustes. */
    fun modelLabel(model: String): String = when (model) {
        "deepseek-v4-pro" -> "V4 Pro"
        else -> "Flash"
    }
}
