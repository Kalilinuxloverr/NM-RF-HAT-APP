package com.nmrf.remote.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixGreenDark
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim

@Composable
fun OuiDbScreen(onBack: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val rows = remember(q) {
        val s = q.trim().uppercase()
        if (s.isBlank()) OuiData.rows else OuiData.rows.filter { it.key.uppercase().contains(s) || it.vendor.uppercase().contains(s) }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("OUI / COMPANY-DB", "${rows.size} Treffer · offline", action = { HeaderChip("‹ TOOLS", onBack) })
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true, label = { Text("Suche: Präfix / ID / Hersteller") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MatrixText, unfocusedTextColor = MatrixText, focusedBorderColor = MatrixGreen,
                unfocusedBorderColor = MatrixGreenDark, focusedLabelColor = MatrixGreen, unfocusedLabelColor = MatrixTextDim, cursorColor = MatrixGreen,
            ),
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(rows) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(r.kind, Modifier.width(70.dp), color = MatrixTextDim, style = MaterialTheme.typography.labelMedium)
                    Text(r.key, Modifier.width(96.dp), color = MatrixGreen, fontFamily = FontFamily.Monospace)
                    Text(r.vendor, color = MatrixText)
                }
                HorizontalDivider(color = MatrixGreenDark.copy(alpha = 0.4f))
            }
        }
    }
}
