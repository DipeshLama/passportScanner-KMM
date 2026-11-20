package io.github.dipeshlama.passportscanner.mrz

import androidx.compose.ui.geometry.Rect
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Sex field of the MRZ. */
public enum class MrzGender { MALE, FEMALE, UNSPECIFIED }

/**
 * Data read from a TD3 (passport) MRZ.
 *
 * [documentNumber], [dateOfBirth] and [expiryDate] are verified by check digits. Names and
 * country codes are not, so they should be confirmed by the user.
 *
 * [toString] hides every value so that logging a result doesn't leak personal details. Read the
 * properties directly when you need them.
 */
public data class PassportMrzData(
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

    /**
     * True when [expiryDate] is before [today]. A passport is still valid on its expiry date.
     *
     * @param today the date to compare against; defaults to today in the device's time zone.
     */
    public fun isExpired(today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): Boolean =
        expiryDate < today

    override fun toString(): String =
        "PassportMrzData(documentNumber=$REDACTED, surname=$REDACTED, givenNames=$REDACTED, " +
            "issuingCountry=$REDACTED, nationality=$REDACTED, dateOfBirth=$REDACTED, " +
            "expiryDate=$REDACTED, gender=$REDACTED)"
}

private const val REDACTED = "***"

internal fun String.toNameCase(): String =
    lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

/** A line of OCR text and its bounds in the image, or [Rect.Zero] if unknown. */
public data class OcrLine(val text: String, val bounds: Rect)

/** A parsed MRZ and its bounds in the image. */
public data class MrzMatch(val data: PassportMrzData, val bounds: Rect)
