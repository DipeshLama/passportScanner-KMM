package io.github.dipeshlama.passportscanner.mrz

import androidx.compose.ui.geometry.Rect
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MrzParserTest {
    private val line1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
    private val line2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"

    @Test
    fun parsesIcaoSpecimen() {
        val data = assertNotNull(MrzParser.parse(listOf(line1, line2)))
        assertEquals("L898902C3", data.documentNumber)
        assertEquals("ERIKSSON", data.surname)
        assertEquals("ANNA MARIA", data.givenNames)
        assertEquals("UTO", data.issuingCountry)
        assertEquals("UTO", data.nationality)
        assertEquals(LocalDate(1974, 8, 12), data.dateOfBirth)
        assertEquals(LocalDate(2012, 4, 15), data.expiryDate)
        assertEquals(MrzGender.FEMALE, data.gender)
    }

    @Test
    fun ignoresSurroundingTextAndOcrNoise() {
        val ocr = listOf(
            "PASSPORT",
            "Nationality NEPALI",
            "p<uto eriksson<<anna<maria«<<<<<<<<<<<<<<<<<",
            "L898902C36UTO74O8122F12O4159ZE184226B<<<<<10"
        )
        val data = assertNotNull(MrzParser.parse(ocr))
        assertEquals("ANNA MARIA", data.givenNames)
        assertEquals(LocalDate(1974, 8, 12), data.dateOfBirth)
    }

    @Test
    fun stripsFillersMisreadAsLetters() {
        val noisyLine1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<KK<<<<<<<<"
        val data = assertNotNull(MrzParser.parse(listOf(noisyLine1, line2)))
        assertEquals("ANNA MARIA", data.givenNames)
    }

    @Test
    fun readsGivenNamesWhenSeparatorReadAsGuillemet() {
        val ocrLine1 = "P<UTOERIKSSON«ANNA<MARIA«««««««««"
        val data = assertNotNull(MrzParser.parse(listOf(ocrLine1, line2)))
        assertEquals("ERIKSSON", data.surname)
        assertEquals("ANNA MARIA", data.givenNames)
    }

    @Test
    fun readsGivenNamesWhenSeparatorLostAFiller() {
        val ocrLine1 = "P<UTOERIKSSON<ANNA<MARIA<<<<<<<<<<<<<<<<<<<<"
        val data = assertNotNull(MrzParser.parse(listOf(ocrLine1, line2)))
        assertEquals("ERIKSSON", data.surname)
        assertEquals("ANNA MARIA", data.givenNames)
    }

    @Test
    fun keepsMultiWordSurnameAndMiddleName() {
        val nepaliLine1 = "P<NPLKARKI<THAPA<<RAM<BAHADUR<<<<<<<<<<<<<<<"
        val data = assertNotNull(MrzParser.parse(listOf(nepaliLine1, line2)))
        assertEquals("KARKI THAPA", data.surname)
        assertEquals("RAM BAHADUR", data.givenNames)
    }

    @Test
    fun readsFragmentedMlKitOutput() {
        val ocr = listOf(
            OcrLine("NEPAL PASSPORT", Rect(304f, 53f, 605f, 80f)),
            OcrLine("Surname SHRESTHA", Rect(302f, 173f, 641f, 200f)),
            OcrLine("Given Names RAM BAHADUR", Rect(302f, 253f, 788f, 280f)),
            OcrLine("P<NPLSHRESTHAK<RAM<BAHADUR«<", Rect(24f, 613f, 633f, 640f)),
            OcrLine("61<<<<<<«<<«««K«cO", Rect(590f, 669f, 876f, 703f)),
            OcrLine("09876543<4NPL9003174M3104161<<", Rect(22f, 673f, 648f, 700f))
        )
        val match = assertNotNull(MrzParser.find(ocr))
        assertEquals("09876543", match.data.documentNumber)
        assertEquals("SHRESTHA", match.data.surname)
        assertEquals("RAM BAHADUR", match.data.givenNames)
        assertEquals(LocalDate(1990, 3, 17), match.data.dateOfBirth)
        assertEquals(LocalDate(2031, 4, 16), match.data.expiryDate)
        assertEquals(Rect(22f, 613f, 876f, 703f), match.bounds)
    }

    @Test
    fun estimatesFullWidthOfTruncatedMlKitLines() {
        val ocr = listOf(
            OcrLine("P<UTOQUEST<AMELIA<MARY<<", Rect(267f, 1080f, 567f, 1098f)),
            OcrLine("S0123456740T08704234 F3208265<", Rect(272f, 1110f, 633f, 1134f))
        )
        val match = assertNotNull(MrzParser.find(ocr))
        assertEquals("S01234567", match.data.documentNumber)
        assertEquals(272f + 361f * 44 / 29, match.bounds.right)
        assertEquals(267f, match.bounds.left)
    }

    @Test
    fun fixesLetterDigitConfusionInDocumentNumber() {
        val ocr = listOf(
            OcrLine("P<UTOQUEST<AMELIA<MARY<<", Rect(265f, 1077f, 569f, 1099f)),
            OcrLine("SO123456740T08704234 F3208265<<", Rect(271f, 1109f, 649f, 1134f))
        )
        assertEquals("S01234567", assertNotNull(MrzParser.find(ocr)).data.documentNumber)
    }

    @Test
    fun keepsBoundsOfCompleteVisionLines() {
        val ocr = listOf(
            OcrLine("P<UTOQUEST<AMELIA<MARY<<<<<<<<<<<<<<<<<<<<<<", Rect(240f, 1054.9f, 837.2f, 1074.4f)),
            OcrLine("S012345674UT08704234F3208265<<<<<<<<<<<<<<<2", Rect(242.8f, 1091.2f, 840f, 1110.7f))
        )
        val match = assertNotNull(MrzParser.find(ocr))
        assertEquals(Rect(240f, 1054.9f, 840f, 1110.7f), match.bounds)
    }

    @Test
    fun keepsUnrelatedTextOnSameRowSeparate() {
        val ocr = listOf(
            OcrLine("P<UTOQUEST<AMELIA<MARY<<<<<<<<<<<<<<<<<<<<<<", Rect(240f, 1054.9f, 837.2f, 1074.4f)),
            OcrLine("Elementor Official Ad", Rect(921f, 1056f, 1077.2f, 1072f)),
            OcrLine("S012345674UT08704234F3208265<<<<<<<<<<<<<<<2", Rect(242.8f, 1091.2f, 840f, 1110.7f)),
            OcrLine("web creators are calling", Rect(910f, 1092f, 1077.2f, 1108f))
        )
        val match = assertNotNull(MrzParser.find(ocr))
        assertEquals(Rect(240f, 1054.9f, 840f, 1110.7f), match.bounds)
    }

    @Test
    fun rejectsFrameWithBadCheckDigit() {
        val corrupted = line2.replaceRange(13, 14, "8")
        assertNull(MrzParser.parse(listOf(line1, corrupted)))
    }

    @Test
    fun rejectsNonPassportText() {
        assertNull(MrzParser.parse(listOf("Hello world", "Nothing to see here")))
    }

    @Test
    fun checkDigitMatchesSpec() {
        assertEquals(6, MrzParser.checkDigit("L898902C3"))
        assertEquals(2, MrzParser.checkDigit("740812"))
        assertEquals(9, MrzParser.checkDigit("120415"))
    }

    @Test
    fun namesForDisplayAreTitleCased() {
        val data = assertNotNull(MrzParser.parse(listOf(line1, line2)))
        assertEquals("Eriksson", data.surnameForDisplay)
        assertEquals("Anna Maria", data.givenNamesForDisplay)
        assertEquals("Dipesh Kumar", "DIPESH KUMAR".toNameCase())
    }
}
