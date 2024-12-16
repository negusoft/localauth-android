package com.negusoft.localauth.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.negusoft.localauth.ui.theme.LocalAuthTheme

object InputDialog {

    @Composable
    operator fun invoke(
        title: String,
        inputLabel: String,
        confirmText: String,
        dismissText: String,
        input: String,
        onInputChange: (String) -> Unit,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        label = { Text(inputLabel) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    content = { Text(confirmText) }
                )
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(dismissText) }
            }
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun InputDialogPreview() {
    LocalAuthTheme {
        Scaffold { padding ->
            Box(modifier = Modifier.padding(padding)) {
                val (input, setInput) = remember { mutableStateOf("") }
                InputDialog(
                    title = "Title",
                    inputLabel = "Field name",
                    confirmText = "Confirm",
                    dismissText = "Dismiss",
                    input = input,
                    onInputChange = setInput,
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
    }
}