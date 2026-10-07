package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Таб моноширинным шрифтом с горизонтальной прокруткой. */
@Composable
fun TabText(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
            .padding(10.dp)
    ) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            softWrap = false,
        )
    }
}
