package com.nmrf.remote.core

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val TEXT =
    "Diese App ist ein Werkzeug für autorisiertes Security-Testing im eigenen Labor.\n\n" +
        "• Nur an Geräten und Netzen einsetzen, die dir gehören oder für die du eine " +
        "ausdrückliche Erlaubnis hast.\n" +
        "• Scannen, Mitschneiden oder Angreifen fremder Funknetze/-geräte ist in vielen " +
        "Ländern strafbar.\n" +
        "• Sende-/Angriffsfunktionen laufen über den NM-RF-HAT und sind ausschließlich für " +
        "abgeschottete Testaufbauten gedacht.\n\n" +
        "Du allein bist für den Einsatz verantwortlich."

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("⚠️  Labor-Hinweis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(TEXT, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAccept) { Text("Verstanden — nur autorisierter Eigengebrauch") }
        Spacer(Modifier.height(24.dp))
    }
}
