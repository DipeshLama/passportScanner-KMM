package io.github.dipeshlama.passportscanner.mrz

import androidx.compose.ui.geometry.Rect
import kotlinx.datetime.LocalDate

/** Sex field of the MRZ. */
enum class MrzGender { MALE, FEMALE, UNSPECIFIED }

/**
 * Data read from a TD3 (passport) MRZ.
 *
 * [documentNumber], [dateOfBirth] and [expiryDate] are verified by check digits. Names and
 * country codes are not, so they should be confirmed by the user.
 */
data class PassportMrzData(
    val documentNumber: String,
    val surname: String,
    val givenNames: String,
    val issuingCountry: String,
    val nationality: String,
    val dateOfBirth: LocalDate,
    val expiryDate: LocalDate,
    val gender: MrzGender
) {
    /** [surname] in title case. */
    val surnameForDisplay: String get() = surname.toNameCase()

    /** [givenNames] in title case. */
    val givenNamesForDisplay: String get() = givenNames.toNameCase()
}

internal fun String.toNameCase(): String =
    lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

/** A line of OCR text and its bounds in the image, or [Rect.Zero] if unknown. */
data class OcrLine(val text: String, val bounds: Rect)

/** A parsed MRZ and its bounds in the image. */
data class MrzMatch(val data: PassportMrzData, val bounds: Rect)
