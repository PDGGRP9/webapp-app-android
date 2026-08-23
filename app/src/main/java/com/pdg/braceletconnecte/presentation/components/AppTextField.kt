package com.pdg.braceletconnecte.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdg.braceletconnecte.ui.theme.AppColors

/** Auth-screen input field — mirrors the web design's `.field` (uppercase label + ink-bordered input). */
@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        MicroLabel(text = label, color = AppColors.InkSoft)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.8.dp),
            placeholder = placeholder?.let { { Text(it, color = AppColors.Ink.copy(alpha = 0.45f)) } },
            singleLine = singleLine,
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            keyboardOptions = keyboardOptions,
            visualTransformation = if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailingIcon = if (isPassword) {
                {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(
                            text = if (passwordVisible) "Masquer" else "Afficher",
                            color = AppColors.Ink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                null
            },
            shape = RoundedCornerShape(14.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = AppColors.Ink,
                fontWeight = FontWeight.SemiBold,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.35f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.35f),
                focusedBorderColor = AppColors.Ink,
                unfocusedBorderColor = AppColors.Ink,
                cursorColor = AppColors.Ink,
                focusedTextColor = AppColors.Ink,
                unfocusedTextColor = AppColors.Ink,
            ),
        )
    }
}
