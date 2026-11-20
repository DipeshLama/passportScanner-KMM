package io.github.dipeshlama.passportscanner.mrz

import io.github.dipeshlama.passportscanner.PassportScanResult
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassportMrzDataTest {
    private val data = PassportMrzData(
        documentNumber = "L898902C3",
        surname = "ERIKSSON",
        givenNames = "ANNA MARIA",
        issuingCountry = "UTO",
        nationality = "UTO",
        dateOfBirth = LocalDate(1974, 8, 12),
        expiryDate = LocalDate(2012, 4, 15),
        gender = MrzGender.FEMALE
    )

    @Test
    fun toStringHidesPersonalDetails() {
        val texts = listOf(
            data.toString(),
            PassportScanResult.Success(data, image = null).toString(),
            MrzMatch(data, androidx.compose.ui.geometry.Rect.Zero).toString()
        )
        texts.forEach { text ->
            listOf("L898902C3", "ERIKSSON", "ANNA", "UTO", "1974", "2012", "FEMALE").forEach { secret ->
                assertFalse(secret in text, "\"$secret\" leaked in: $text")
            }
        }
    }

    @Test
    fun expiryDateItselfIsStillValid() {
        assertFalse(data.isExpired(today = LocalDate(2012, 4, 15)))
        assertFalse(data.isExpired(today = LocalDate(2011, 1, 1)))
        assertTrue(data.isExpired(today = LocalDate(2012, 4, 16)))
    }

    @Test
    fun equalityStillComparesValues() {
        assertTrue(data == data.copy())
        assertFalse(data == data.copy(documentNumber = "X"))
    }
}
