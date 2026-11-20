package io.github.dipeshlama.passportscanner.mrz

import androidx.compose.ui.geometry.Rect
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Parser for the two-line TD3 passport MRZ (ICAO 9303 part 4).
 *
 * Works on raw OCR output: common OCR mistakes are corrected, and a result is returned only when
 * the document number, date of birth and expiry date check digits are valid.
 */
public object MrzParser {
    private const val LINE_LENGTH = 44
    private const val LINE2_MIN_LENGTH = 28
    private const val LINE1_MIN_LENGTH = 7
    private const val FRAGMENT_MAX_GAP_IN_LINE_HEIGHTS = 2f

    /** Parses the MRZ from lines of OCR text, or returns null if no valid MRZ is found. */
    public fun parse(ocrLines: List<String>): PassportMrzData? =
        find(ocrLines.map { OcrLine(it, Rect.Zero) })?.data

    /**
     * Like [parse], but uses line positions to join lines that OCR split into fragments, and
     * returns where the MRZ is in the image.
     */
    public fun find(ocrLines: List<OcrLine>): MrzMatch? {
        val rows = mergeRows(ocrLines).map { it to normalise(it.text) }
        for (index in rows.indices) {
            val (line2, text2) = rows[index]
            if (text2.length < LINE2_MIN_LENGTH) continue
            val line1Index = (index - 1 downTo maxOf(0, index - 2)).firstOrNull { isLine1(rows[it].second) }
                ?: continue
            val (line1, text1) = rows[line1Index]
            val data = parseLines(fit(text1), fit(text2)) ?: continue

            val printedWords = rows
                .filterIndexed { i, _ -> i != index && i != line1Index }
                .flatMap { (row, _) -> row.text.uppercase().split(Regex("[^A-Z]+")) }
                .filter { it.length > 1 }
                .toSet()
            return MrzMatch(
                data = data.copy(
                    surname = data.surname.correctedBy(printedWords),
                    givenNames = data.givenNames.correctedBy(printedWords)
                ),
                bounds = line1.bounds.union(line2.bounds.extendedToFullLine(text2.length))
            )
        }
        return null
    }

    // OCR may stop a line at a run of '<' fillers; MRZ characters are fixed width, so estimate the full line.
    private fun Rect.extendedToFullLine(recognisedChars: Int): Rect {
        if (this == Rect.Zero || recognisedChars <= 0 || recognisedChars >= LINE_LENGTH) return this
        return copy(right = left + width * LINE_LENGTH / recognisedChars)
    }

    // Check digits can't tell O/0 or S/5 apart, so only try the formats passports use.
    private fun documentNumberCandidates(raw: String): List<String> {
        fun prefixed(letters: Int) =
            lettersOnly(raw.take(letters)) + digitsOnly(raw.drop(letters), keepFillers = true)
        return listOf(raw, digitsOnly(raw, keepFillers = true), prefixed(1), prefixed(2)).distinct()
    }

    private fun isLine1(text: String) = text.length >= LINE1_MIN_LENGTH && text[0] == 'P' && '<' in text

    private fun mergeRows(lines: List<OcrLine>): List<OcrLine> {
        if (lines.all { it.bounds == Rect.Zero }) return lines
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.sortedBy { it.bounds.center.y }) {
            val row = rows.lastOrNull()
            if (row != null && row.first().isSameRowAs(line)) row += line else rows += mutableListOf(line)
        }
        return rows.flatMap { row ->
            val runs = mutableListOf<MutableList<OcrLine>>()
            for (fragment in row.sortedBy { it.bounds.left }) {
                val run = runs.lastOrNull()
                val maxGap = fragment.bounds.height * FRAGMENT_MAX_GAP_IN_LINE_HEIGHTS
                if (run != null && fragment.bounds.left - run.maxOf { it.bounds.right } <= maxGap) {
                    run += fragment
                } else {
                    runs += mutableListOf(fragment)
                }
            }
            runs.map { run ->
                OcrLine(
                    text = run.joinToString("") { it.text },
                    bounds = run.map { it.bounds }.reduce { acc, rect -> acc.union(rect) }
                )
            }
        }
    }

    private fun OcrLine.isSameRowAs(other: OcrLine): Boolean {
        val tolerance = minOf(bounds.height, other.bounds.height) / 2
        return kotlin.math.abs(bounds.center.y - other.bounds.center.y) <= tolerance
    }

    // OCR often reads a '<' next to a name as K or C; fix it using names printed elsewhere on the page.
    private fun String.correctedBy(printedWords: Set<String>): String {
        if (printedWords.isEmpty()) return this
        return split(' ').joinToString(" ") { word ->
            if (word in printedWords) return@joinToString word
            listOf(word.trimEnd('K', 'C'), word.trimStart('K', 'C'), word.trim('K', 'C'))
                .firstOrNull { it.isNotEmpty() && it in printedWords }
                ?: word
        }
    }

    private fun Rect.union(other: Rect) = Rect(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom)
    )

    private fun parseLines(line1: String, line2: String): PassportMrzData? {
        val documentNumber = documentNumberCandidates(line2.substring(0, 9))
            .firstOrNull { isValid(it, line2[9]) }
            ?: return null

        val dobRaw = digitsOnly(line2.substring(13, 19))
        if (!isValid(dobRaw, line2[19])) return null

        val expiryRaw = digitsOnly(line2.substring(21, 27))
        if (!isValid(expiryRaw, line2[27])) return null

        val dateOfBirth = parseDate(dobRaw, isBirthDate = true) ?: return null
        val expiryDate = parseDate(expiryRaw, isBirthDate = false) ?: return null

        val (surname, givenNames) = parseNames(line1.substring(5))
        if (surname.isBlank()) return null

        return PassportMrzData(
            documentNumber = documentNumber.replace("<", ""),
            surname = surname,
            givenNames = givenNames,
            issuingCountry = lettersOnly(line1.substring(2, 5)).replace("<", ""),
            nationality = lettersOnly(line2.substring(10, 13)).replace("<", ""),
            dateOfBirth = dateOfBirth,
            expiryDate = expiryDate,
            gender = when (line2[20]) {
                'M' -> MrzGender.MALE
                'F' -> MrzGender.FEMALE
                else -> MrzGender.UNSPECIFIED
            }
        )
    }

    private fun normalise(raw: String): String =
        raw.uppercase()
            .replace("«", "<<")
            .replace('‹', '<')
            .filterNot { it.isWhitespace() }
            .filter { it.isLetterOrDigit() || it == '<' }

    private fun fit(line: String): String =
        if (line.length >= LINE_LENGTH) line.take(LINE_LENGTH) else line.padEnd(LINE_LENGTH, '<')

    private fun parseNames(field: String): Pair<String, String> {
        val cleaned = lettersOnly(field).replace(Regex("<[<KC]*$"), "")
        val separator = cleaned.indexOf("<<").takeIf { it >= 0 }?.let { it to 2 }
            ?: cleaned.indexOf('<').takeIf { it >= 0 }?.let { it to 1 }
        val (surnamePart, givenPart) = if (separator == null) {
            cleaned to ""
        } else {
            val (index, length) = separator
            cleaned.substring(0, index) to cleaned.substring(index + length)
        }
        return surnamePart.toName() to givenPart.toName()
    }

    private fun String.toName(): String = split('<').filter { it.isNotEmpty() }.joinToString(" ")

    private fun parseDate(yymmdd: String, isBirthDate: Boolean): LocalDate? {
        val yy = yymmdd.substring(0, 2).toIntOrNull() ?: return null
        val month = yymmdd.substring(2, 4).toIntOrNull() ?: return null
        val day = yymmdd.substring(4, 6).toIntOrNull() ?: return null
        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        val year = if (isBirthDate) {
            if (2000 + yy > currentYear) 1900 + yy else 2000 + yy
        } else {
            2000 + yy
        }
        return runCatching { LocalDate(year, month, day) }.getOrNull()
    }

    private fun isValid(field: String, checkChar: Char): Boolean {
        val expected = digitOrNull(checkChar) ?: return false
        return checkDigit(field) == expected
    }

    internal fun checkDigit(field: String): Int {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0
        field.forEachIndexed { index, c ->
            val value = when {
                c.isDigit() -> c - '0'
                c in 'A'..'Z' -> c - 'A' + 10
                else -> 0
            }
            sum += value * weights[index % 3]
        }
        return sum % 10
    }

    private fun digitOrNull(c: Char): Int? = digitsOnly(c.toString()).single().digitToIntOrNull()

    private fun digitsOnly(field: String, keepFillers: Boolean = false): String = field.map {
        when (it) {
            'O', 'Q', 'D' -> '0'
            'I', 'L' -> '1'
            'Z' -> '2'
            'S' -> '5'
            'G' -> '6'
            'B' -> '8'
            '<' -> if (keepFillers) '<' else '0'
            else -> it
        }
    }.joinToString("")

    private fun lettersOnly(field: String): String = field.map {
        when (it) {
            '0' -> 'O'
            '1' -> 'I'
            '2' -> 'Z'
            '5' -> 'S'
            '8' -> 'B'
            else -> it
        }
    }.joinToString("")
}
