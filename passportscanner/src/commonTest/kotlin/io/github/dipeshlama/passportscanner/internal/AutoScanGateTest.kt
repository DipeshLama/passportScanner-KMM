package io.github.dipeshlama.passportscanner.internal

import androidx.compose.ui.geometry.Rect
import io.github.dipeshlama.passportscanner.mrz.MrzGender
import io.github.dipeshlama.passportscanner.mrz.MrzMatch
import io.github.dipeshlama.passportscanner.mrz.PassportMrzData
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class AutoScanGateTest {
    private val crop = PreviewCrop(1000f, 2000f, Rect(left = 100f, top = 800f, right = 900f, bottom = 1200f))
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
    private val centredMrz = MrzMatch(data, Rect(left = 140f, top = 1080f, right = 860f, bottom = 1160f))

    private val clock = TestTimeSource()
    private val gate = AutoScanGate(timeSource = clock)

    private fun offer(match: MrzMatch?, after: Int = 0): Boolean {
        clock += after.milliseconds
        return gate.accept(match, 1000, 2000, crop)
    }

    private fun pastWarmUp() {
        offer(null)
        clock += 1500.milliseconds
    }

    @Test
    fun waitsOutWarmUpEvenWhenPassportIsAlreadyInFrame() {
        offer(centredMrz)
        assertFalse(offer(centredMrz, after = 1000))
        assertFalse(offer(centredMrz, after = 400))
    }

    @Test
    fun acceptsAfterPassportIsHeldForOneSecond() {
        pastWarmUp()
        assertFalse(offer(centredMrz))
        assertFalse(offer(centredMrz, after = 500))
        assertTrue(offer(centredMrz, after = 500))
    }

    @Test
    fun needsTwoReadingsEvenIfOneIsLate() {
        pastWarmUp()
        assertFalse(offer(centredMrz))
        assertTrue(offer(centredMrz, after = 1200))
    }

    @Test
    fun startsHoldOverWhenPassportLeavesTheFrame() {
        pastWarmUp()
        offer(centredMrz)
        assertFalse(offer(centredMrz.copy(bounds = Rect(0f, 1080f, 720f, 1160f)), after = 600))
        assertFalse(offer(centredMrz, after = 100))
        assertFalse(offer(centredMrz, after = 600))
        assertTrue(offer(centredMrz, after = 400))
    }

    @Test
    fun unreadableFramesDoNotResetProgress() {
        pastWarmUp()
        offer(centredMrz)
        assertFalse(offer(null, after = 500))
        assertTrue(offer(centredMrz, after = 500))
    }

    @Test
    fun resetRestartsWarmUp() {
        pastWarmUp()
        gate.reset()
        offer(centredMrz)
        assertFalse(offer(centredMrz, after = 1200))
    }

    @Test
    fun ignoresCountryCodeMisreadsBetweenFrames() {
        pastWarmUp()
        offer(centredMrz)
        assertTrue(offer(centredMrz.copy(data = data.copy(nationality = "OTO")), after = 1000))
    }

    @Test
    fun startsOverWhenReadingChanges() {
        pastWarmUp()
        offer(centredMrz)
        assertFalse(offer(centredMrz.copy(data = data.copy(documentNumber = "L898902C4")), after = 1000))
    }

    @Test
    fun rejectsMrzInUpperHalfOfFrame() {
        val high = centredMrz.copy(bounds = Rect(140f, 850f, 860f, 930f))
        assertFalse(high.isInsideFrame(1000, 2000, crop))
    }

    @Test
    fun rejectsPassportHeldTooFarAway() {
        val small = centredMrz.copy(bounds = Rect(400f, 1080f, 700f, 1160f))
        assertFalse(small.isInsideFrame(1000, 2000, crop))
    }
}
