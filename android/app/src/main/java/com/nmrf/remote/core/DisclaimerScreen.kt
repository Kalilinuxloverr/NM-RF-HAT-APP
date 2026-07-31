package com.nmrf.remote.core

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nmrf.remote.ui.components.MatrixCard
import com.nmrf.remote.ui.theme.MatrixAmber
import com.nmrf.remote.ui.theme.MatrixBlack
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixText

private val LINES = listOf(
    "Nur an Geräten und Netzen einsetzen, die dir gehören oder für die du eine ausdrückliche Erlaubnis hast.",
    "Scannen, Mitschneiden oder Angreifen fremder Funknetze/-geräte ist in vielen Ländern strafbar.",
    "Sende-/Angriffsfunktionen laufen über den NM-RF-HAT und nur in abgeschotteten Testaufbauten.",
    "Du allein bist für den Einsatz verantwortlich.",
)

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    Column(
        Modifier.fillMaxSize().appBackground().padding(20.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("⚠ LAB-HINWEIS", style = MaterialTheme.typography.headlineSmall, color = MatrixAmber)
        Spacer(Modifier.height(6.dp))
        Text(
            "Werkzeug für autorisiertes Security-Testing im eigenen Labor.",
            style = MaterialTheme.typography.bodyMedium, color = MatrixText,
        )
        Spacer(Modifier.height(16.dp))
        MatrixCard {
            LINES.forEach {
                Text("› $it", style = MaterialTheme.typography.bodyMedium, color = MatrixText, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen, contentColor = MatrixBlack),
        ) { Text("[ VERSTANDEN — EIGENGEBRAUCH ]") }
        Spacer(Modifier.height(24.dp))
    }
}
