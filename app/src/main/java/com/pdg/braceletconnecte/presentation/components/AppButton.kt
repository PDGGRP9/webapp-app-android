package com.pdg.braceletconnecte.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdg.braceletconnecte.ui.theme.AppColors

enum class AppButtonVariant { Solid, Ghost, Danger, Ink }

/** Pill-shaped button — mirrors the web design's `.btn-solid`/`.btn-ghost`/`.btn-danger`/`.btn-ink`. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Solid,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.6.dp)
    val isEnabled = enabled && !isLoading

    when (variant) {
        AppButtonVariant.Solid -> {
            val contentColor = MaterialTheme.colorScheme.onPrimary
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = isEnabled,
                shape = shape,
                contentPadding = contentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = contentColor,
                ),
            ) { ButtonLabel(text, isLoading, contentColor) }
        }

        AppButtonVariant.Ink -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = isEnabled,
                shape = shape,
                contentPadding = contentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Ink,
                    contentColor = AppColors.Lime,
                ),
            ) { ButtonLabel(text, isLoading, AppColors.Lime) }
        }

        AppButtonVariant.Ghost -> {
            val contentColor = MaterialTheme.colorScheme.onSurface
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = isEnabled,
                shape = shape,
                contentPadding = contentPadding,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
            ) { ButtonLabel(text, isLoading, contentColor) }
        }

        AppButtonVariant.Danger -> {
            val contentColor = MaterialTheme.colorScheme.error
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = isEnabled,
                shape = shape,
                contentPadding = contentPadding,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
            ) { ButtonLabel(text, isLoading, contentColor) }
        }
    }
}

@Composable
private fun ButtonLabel(text: String, isLoading: Boolean, color: Color) {
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
    } else {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}
