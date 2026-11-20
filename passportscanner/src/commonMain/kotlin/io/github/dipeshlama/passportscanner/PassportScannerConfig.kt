package io.github.dipeshlama.passportscanner

import androidx.compose.runtime.Immutable

/**
 * Behaviour options for [PassportScanner].
 *
 * @property reviewBeforeReturning show the scanned details for confirmation before returning them.
 * @property allowGallery show the button for picking a photo from the gallery.
 * @property allowImageOnlyResult when the MRZ can't be read, let the user keep the photo
 *   ([PassportScanResult.ImageOnly]).
 * @property showTips show the scanning tips button.
 */
@Immutable
data class PassportScannerConfig(
    val reviewBeforeReturning: Boolean = true,
    val allowGallery: Boolean = true,
    val allowImageOnlyResult: Boolean = true,
    val showTips: Boolean = true,
)

/** Text shown by [PassportScanner]. Override any value to change or translate it. */
@Immutable
data class PassportScannerStrings(
    val title: String = "Scan passport",
    val instruction: String = "Fit the passport photo page inside the frame",
    val back: String = "Back",
    val gallery: String = "Upload from gallery",
    val tips: String = "Scanning tips",
    val captureHint: String = "Can't scan automatically? Tap to capture",
    val capture: String = "Capture",
    val processing: String = "Reading passport…",
    val permissionDescription: String =
        "Allow camera access to scan your passport, or upload a photo from your gallery.",
    val allowCamera: String = "Allow camera",

    val failedTitle: String = "Couldn't read your passport",
    val failedDescription: String =
        "Make sure the two lines of characters at the bottom of the photo page are flat, in focus and free of glare.",
    val tryAgain: String = "Try again",
    val useThisPhoto: String = "Use this photo",

    val reviewTitle: String = "Check your details",
    val reviewDescription: String = "Make sure these match your passport.",
    val rescan: String = "Re-scan",
    val confirm: String = "Confirm",
    val labelSurname: String = "Surname",
    val labelGivenNames: String = "Given names",
    val labelDocumentNumber: String = "Passport no.",
    val labelNationality: String = "Nationality",
    val labelDateOfBirth: String = "Date of birth",
    val labelSex: String = "Sex",
    val labelExpiryDate: String = "Expiry date",
    val labelIssuingCountry: String = "Issuing country",
    val sexMale: String = "Male",
    val sexFemale: String = "Female",
    val sexUnspecified: String = "Unspecified",

    val tipsTitle: String = "Tips for a quick scan",
    val tipsItems: List<String> = listOf(
        "Use the photo page: the one with two lines of <<< characters at the bottom.",
        "Lay the passport flat in good light and avoid glare.",
        "Fit the whole page inside the frame and hold still for a moment.",
    ),
    val tipsDismiss: String = "Got it",
)
