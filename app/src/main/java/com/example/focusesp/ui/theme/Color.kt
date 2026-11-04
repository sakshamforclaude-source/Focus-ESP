package com.example.focusesp.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Midnight navy palette — deep, cool and calm. A single electric-blue accent
// with an ice-blue / cyan secondary gives the product a premium, focused feel.
// Every foreground token is tuned for WCAG-friendly contrast on its surface:
// never dark text on a dark surface.
//
// FocusESP ships the blue-black identity as the only runtime theme, but a fully
// paired light palette is kept here so the colour system stays complete.
// ---------------------------------------------------------------------------

// Core surfaces (dark)
val MidnightBackground = Color(0xFF090D14)
val NavySurface = Color(0xFF151E2C)          // main card surfaces
val NavySurfaceElevated = Color(0xFF1D293A)  // elevated surfaces / chips
val NavySurfaceHigh = Color(0xFF243447)      // inputs, steppers, tracks
val NavyOutline = Color(0xFF2C3B50)          // borders
val NavyOutlineSoft = Color(0xFF1E2A3B)      // hairline dividers

// Typography (dark)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFFB8C5D6)
val TextMuted = Color(0xFF91A2B8)

// Accents (dark)
val ElectricBlue = Color(0xFF60A5FA)         // primary
val ElectricBlueDeep = Color(0xFF3B82F6)     // gradient end
val IceBlue = Color(0xFFBAE6FD)              // secondary accent
val CyanAccent = Color(0xFF22D3EE)           // tertiary / signal

val BlueContainer = Color(0xFF1B3358)
val OnBlueContainer = Color(0xFFCFE6FF)
val OnPrimaryNavy = Color(0xFF07111F)        // text on the bright blue primary

val CyanContainer = Color(0xFF0E3A46)
val OnCyanContainer = Color(0xFFA5F3FC)

// Status (dark)
val DangerRed = Color(0xFFFDA4AF)            // error text
val DangerContainer = Color(0xFF3A1D22)
val OnDangerContainer = Color(0xFFFECDD3)

val AmberTint = Color(0xFFFBBF24)
val AmberContainer = Color(0xFF3A2E14)
val OnAmberContainer = Color(0xFFFDE68A)

/** Soft blue glow used behind primary actions and progress rings. */
val ElectricGlow = Color(0x5560A5FA)

// ---------------------------------------------------------------------------
// Light counterparts — exact spec values, kept so the theme stays complete.
// ---------------------------------------------------------------------------
val LightBackground = Color(0xFFF5F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFEAF0F8)
val LightSurfaceVariant = Color(0xFFEAF0F8)
val LightOutline = Color(0xFFD5DFEB)
val LightOnSurface = Color(0xFF111827)
val LightOnSurfaceVariant = Color(0xFF374151)
val LightMuted = Color(0xFF59677A)
val LightBlue = Color(0xFF2563EB)            // primary accent
val LightSecondaryAccent = Color(0xFF0369A1) // secondary accent
val LightTertiary = Color(0xFF0369A1)
val LightBlueContainer = Color(0xFFDBEAFE)
val LightOnBlueContainer = Color(0xFF172554)
val LightOnPrimary = Color(0xFFFFFFFF)       // text on blue buttons
val LightError = Color(0xFFB91C1C)           // error text
val LightErrorContainer = Color(0xFFFEE2E2)
val LightOnErrorContainer = Color(0xFF7F1D1D)
