package com.nmrf.remote.ui.theme

import androidx.compose.ui.graphics.Color

// Matrix-Palette
val MatrixBlack = Color(0xFF000000)
val MatrixPanel = Color(0xFF07120B)       // fast schwarz, grüner Stich
val MatrixGreen = Color(0xFF00FF41)       // Phosphor-Grün (Primär)
val MatrixGreenDim = Color(0xFF16C34A)
val MatrixGreenDark = Color(0xFF0C3D1B)   // Rahmen / Rain-Schweif
val MatrixText = Color(0xFFC6FFD2)        // gut lesbarer Body
val MatrixTextDim = Color(0xFF5FB878)
val MatrixAmber = Color(0xFFFFC400)
val MatrixRed = Color(0xFFFF4D4D)

// Aliase (von bestehenden Screens genutzt)
val NeonGreen = MatrixGreen
val NeonCyan = Color(0xFF35E0FF)
val NeonMagenta = Color(0xFFFF3D9A)

// Elevation — Tiefe statt einer einzigen Fläche
val Elev1 = MatrixPanel                 // Basis-Karte (bestehend)
val Elev2 = Color(0xFF0B1E13)           // gehobene / aktive Karte
val Elev3 = Color(0xFF11301E)           // Fokus / Hover
val LineSoft = Color(0xFF0A2213)        // feine Trennlinie

// Semantischer Status-Ramp — Farbe trägt Bedeutung, nicht Deko
val StatusOk = MatrixGreen              // sicher / normal
val StatusActive = MatrixAmber          // aktiv / scannt
val StatusData = NeonCyan               // Datenpunkt / Messwert
val StatusWarn = Color(0xFFFF9F1C)      // Auffälligkeit
val StatusAlert = MatrixRed             // Bedrohung / Alarm
