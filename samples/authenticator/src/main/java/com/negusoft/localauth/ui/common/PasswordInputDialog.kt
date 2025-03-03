package com.negusoft.localauth.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.negusoft.localauth.ui.theme.LocalAuthTheme

object PasswordInputDialog {

    @Composable
    operator fun invoke(
        title: String,
        onDismissRequest: () -> Unit,
        confirm: (value: String) -> Unit
    ) {
        val valueField = remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = valueField.value,
                        onValueChange = { valueField.value = it },
                        label = { Text("Password") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm(valueField.value)
                        onDismissRequest()
                    },
                    content = { Text("Confirm") }
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) { Text("Dismiss") }
            }
        )
    }

}

@Composable
@Preview
fun PasswordInputDialogPreview() {
    LocalAuthTheme {
        PasswordInputDialog(
            title = "Important title",
            onDismissRequest = {},
            confirm = {}
        )
    }
}