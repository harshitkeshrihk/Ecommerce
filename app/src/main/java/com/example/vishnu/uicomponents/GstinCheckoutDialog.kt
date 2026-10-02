package com.example.vishnu.uicomponents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.vishnu.model.GstinPrefill
import com.example.vishnu.utils.formatRupees
import com.example.vishnu.utils.isValidGstin
import com.example.vishnu.utils.normalizeGstin

/**
 * Last step of a wholesale order (Quick-Order Pad, quote acceptance): confirm
 * the total and optionally attach a GSTIN. An approved business account's
 * verified GSTIN comes pre-filled and locked.
 */
@Composable
fun GstinCheckoutDialog(
    title: String,
    total: Double,
    prefill: GstinPrefill,
    onConfirm: (gstin: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember(prefill) { mutableStateOf(prefill.value) }
    val gstin = normalizeGstin(input)
    val isError = gstin.isNotEmpty() && !isValidGstin(gstin)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Total: ${formatRupees(total)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Billed offline against the order summary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (!prefill.locked) input = it.uppercase() },
                    readOnly = prefill.locked,
                    label = { Text(if (prefill.locked) "GSTIN" else "GSTIN (optional)") },
                    singleLine = true,
                    isError = isError,
                    supportingText = {
                        Text(
                            when {
                                prefill.locked -> "From your verified business account"
                                isError -> "Enter a valid 15-character GSTIN, or leave it blank"
                                else -> "Saved on this order for offline billing"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(gstin.ifEmpty { null }) },
                enabled = !isError
            ) { Text("Confirm Order") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
