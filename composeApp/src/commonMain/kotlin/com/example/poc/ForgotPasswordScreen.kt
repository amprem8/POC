package com.example.poc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ForgotPasswordScreen(
    message: PassKeyMessage?,
    onVerify: (String) -> Unit,
    onBackToLogin: () -> Unit,
) {
    var recoveryPhrase by rememberSaveable { mutableStateOf("") }
    val inputController = rememberPassKeyInputController()

    AuthScreenLayout(
        title = "Forgot password",
        subtitle = "Enter the saved recovery phrase to open the master password setup screen again for this install.",
        message = message,
        content = {
            OutlinedTextField(
                value = recoveryPhrase,
                onValueChange = { recoveryPhrase = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                placeholder = { Text("Paste recovery phrase") },
                colors = passKeyTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = inputController.keyboardActions(
                    onSubmit = { onVerify(recoveryPhrase) },
                ),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onVerify(recoveryPhrase) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF111827),
                    contentColor = Color.White,
                ),
            ) {
                Text("Verify recovery phrase", fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                OutlinedButton(onClick = onBackToLogin) {
                    Text("Back to login")
                }
            }
        },
    )
}

