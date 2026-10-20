package com.miking.notas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Chips horizontales para elegir o crear una categoría. */
@Composable
internal fun CategoryChips(
    categories: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
    onNewCategory: () -> Unit,
    modifier: Modifier = Modifier,
    includeAll: Boolean = false,
) {
    val visible = categories.toMutableList()
    if (!selected.isNullOrBlank() && visible.none { it.equals(selected, ignoreCase = true) }) {
        visible += selected
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = if (includeAll) selected == null else selected.isNullOrBlank(),
            onClick = { onSelected(null) },
            label = { Text(if (includeAll) "Todas" else "Sin categoría") },
        )

        visible.forEach { category ->
            FilterChip(
                selected = selected?.equals(category, ignoreCase = true) == true,
                onClick = { onSelected(category) },
                label = { Text(category) },
            )
        }

        AssistChip(
            onClick = onNewCategory,
            label = { Text("Nueva") },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            },
        )
    }
}

/**
 * Pide un nombre de categoría. [onCreate] devuelve true si se pudo crear.
 * La comprobación de duplicados se hace aquí para mostrar el error en el campo.
 */
@Composable
internal fun NewCategoryDialog(
    existing: List<String>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Boolean,
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva categoría") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nombre") },
                    placeholder = { Text("Por ejemplo, Trabajo") },
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val clean = name.trim().replace(Regex("\\s+"), " ")
                    when {
                        clean.isEmpty() -> error = "Escribe un nombre"
                        existing.any { it.equals(clean, ignoreCase = true) } ->
                            error = "Ya existe una categoría con ese nombre"

                        !onCreate(clean) -> error = "No se pudo crear la categoría"
                        else -> onDismiss()
                    }
                },
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
