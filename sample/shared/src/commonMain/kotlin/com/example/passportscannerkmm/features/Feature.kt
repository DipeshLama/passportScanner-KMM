package com.example.passportscannerkmm.features

import androidx.compose.runtime.Composable
import com.example.passportscannerkmm.features.barcode.BarcodeScannerFeature
import com.example.passportscannerkmm.features.passport.PassportScannerFeature

/**
 * One entry on the home screen.
 *
 * To add a feature to the demo: create a class implementing this interface and add it to
 * [Features]. Nothing else needs to change.
 */
interface Feature {
    val id: String
    val title: String
    val description: String

    /** A short glyph (emoji) shown in the home card. */
    val glyph: String

    /** False renders a disabled "Coming soon" card. */
    val available: Boolean get() = true

    /** The feature's screen. [onBack] returns to the home screen. */
    @Composable
    fun Content(onBack: () -> Unit)
}

/** Registry of every feature shown in the demo, in display order. */
val Features: List<Feature> = listOf(
    PassportScannerFeature,
    BarcodeScannerFeature,
)
