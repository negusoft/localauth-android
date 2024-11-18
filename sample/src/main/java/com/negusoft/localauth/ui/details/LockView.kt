package com.negusoft.localauth.ui.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.negusoft.localauth.ui.theme.LocalAuthTheme


@Composable
fun LockView(
    modifier: Modifier = Modifier,
    title: String,
    enabled: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    open: Boolean,
    onUnlock: () -> Unit,
    color: Color
) {
    val stateColor = if (enabled) color else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier
            .width(IntrinsicSize.Max)
            .wrapContentSize(),
        color = stateColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, stateColor)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            when {
                !open ->
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = enabled, onClick = onUnlock, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = color.copy(alpha = 0.1f))) {
                        Text(text = if (enabled) "Unlock" else "Disabled")
                    }
                !enabled ->
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onEnable, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = color.copy(alpha = 0.1f))) {
                        Text(text = "Register")
                    }
                else ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDisable,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = color.copy(alpha = 0.2f)
                            ),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = "Remove")
                        }
                        OutlinedButton(
                            onClick = onEnable,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = color.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(text = "Modify")
                        }
                    }
            }
        }
    }
}

@Preview
@Composable
fun LockViewPreview() {
    LocalAuthTheme {
        Scaffold { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top)
            ) {
                Text(text = "Locked", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LockView(
                        modifier = Modifier.weight(1f),
                        title = "Disabled",
                        enabled = false,
                        onEnable = {},
                        onDisable = {},
                        open = false,
                        onUnlock = {},
                        color = Color.Red
                    )
                    LockView(
                        modifier = Modifier.weight(1f),
                        title = "Enabled",
                        enabled = true,
                        onEnable = {},
                        onDisable = {},
                        open = false,
                        onUnlock = {},
                        color = Color.Red
                    )
                }
                Text(text = "Open", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LockView(
                        modifier = Modifier.weight(1f),
                        title = "Locked",
                        enabled = false,
                        onEnable = {},
                        onDisable = {},
                        open = true,
                        onUnlock = {},
                        color = Color.Green
                    )
                    LockView(
                        modifier = Modifier.weight(1f),
                        title = "Locked",
                        enabled = true,
                        onEnable = {},
                        onDisable = {},
                        open = true,
                        onUnlock = {},
                        color = Color.Green
                    )
                }
            }

        }
    }
}