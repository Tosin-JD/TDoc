package com.tosin.docprocessor.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


@Composable
fun TableWidget(rows: List<List<String>>, isDarkMode: Boolean = false) {
    val borderColor = if (isDarkMode) Color.Gray else Color.Gray
    val cellBorderColor = if (isDarkMode) Color.DarkGray else Color.LightGray
    val textColor = if (isDarkMode) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, borderColor)
    ) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { cellText ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(0.5.dp, cellBorderColor)
                            .padding(8.dp)
                    ) {
                        Text(text = cellText, style = MaterialTheme.typography.bodySmall, color = textColor)
                    }
                }
            }
        }
    }
}
