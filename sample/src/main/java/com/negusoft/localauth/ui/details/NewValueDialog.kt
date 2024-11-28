package com.negusoft.localauth.ui.details

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

object NewValueDialog {

    @Composable
    operator fun invoke(
        onDismissRequest: () -> Unit,
        createValue: (key: String, value: String) -> Unit
    ) {
        val keyField = remember { mutableStateOf("") }
        val valueField = remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = "New secret value") },
            text = {
                Column {
                    OutlinedTextField(
                        value = keyField.value,
                        onValueChange = { keyField.value = it },
                        label = { Text("Key") }
                    )
                    OutlinedTextField(
                        value = valueField.value,
                        onValueChange = { valueField.value = it },
                        label = { Text("Value") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        createValue(keyField.value, valueField.value)
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

@Preview
@Composable
fun NewValueDialogPreview() {
    LocalAuthTheme {
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                NewValueDialog(onDismissRequest = {}, createValue = { _, _ -> })
            }
        }
    }
}