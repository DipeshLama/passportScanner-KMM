# Passport & Barcode Scanner for Compose Multiplatform

[![passport-scanner](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fio%2Fgithub%2Fdipeshlama%2Fpassport-scanner%2Fmaven-metadata.xml&label=passport-scanner&color=blue&cacheSeconds=300)](https://central.sonatype.com/artifact/io.github.dipeshlama/passport-scanner)
[![barcode-scanner](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fio%2Fgithub%2Fdipeshlama%2Fbarcode-scanner%2Fmaven-metadata.xml&label=barcode-scanner&color=blue&cacheSeconds=300)](https://central.sonatype.com/artifact/io.github.dipeshlama/barcode-scanner)
[![GitHub tag](https://img.shields.io/github/v/tag/DipeshLama/passportScanner-KMM?label=release&color=brightgreen)](https://github.com/DipeshLama/passportScanner-KMM/tags)
[![License: MIT](https://img.shields.io/github/license/DipeshLama/passportScanner-KMM?color=yellow)](LICENSE)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS-lightgrey)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.12.2-4285F4?logo=jetpackcompose&logoColor=white)

Kotlin Multiplatform / Compose Multiplatform scanners for **Android and iOS**, used from `commonMain`. There are two libraries, and you can add either one or both:

| Artifact | Scans |
|---|---|
| `passport-scanner` | Passport photo pages (the MRZ) |
| `barcode-scanner` | QR codes and barcodes |

**passport-scanner**

- Live camera scanning that finishes on its own once the passport is held steady inside the frame
- Manual capture and gallery import for when auto-scan can't read the passport, with optional cropping of gallery photos
- Flashlight button for dim rooms, and an optional check that rejects expired passports
- ICAO 9303 TD3 MRZ parser that only accepts a result when every check digit validates, so a misread frame is rejected instead of returning wrong details
- Camera permission handling, an optional review sheet and a tips sheet, with all text replaceable for localisation
- A `PassportScannerState` with a Flow of scan events, and a headless `PassportImageScanner.scanImage(bytes)` for reading images without UI
- OCR runs on the device: ML Kit on Android, Apple Vision on iOS. Nothing is sent over the network.

**barcode-scanner**

- Live scanning that returns the first code inside a square frame (or a wider frame for 1D barcodes)
- QR, Aztec, Data Matrix, PDF417, EAN-13/8, UPC-A/E, Code 128/39/93, Codabar and ITF, with the formats to read configurable
- Flashlight button, gallery import with optional cropping, and camera permission handling

## Supported platforms

| Platform | Target | Minimum version | Camera / recognition |
|---|---|---|---|
| Android | `android` | API 24 (Android 7.0) | CameraX + ML Kit (text recognition, barcode scanning) |
| iOS (device) | `iosArm64` | iOS 14 | AVFoundation + Apple Vision |

Desktop (JVM), web (JS/Wasm) and `iosX64` (Intel simulators) are not supported.

## Installation

```kotlin
// build.gradle.kts of your shared module
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.dipeshlama:passport-scanner:1.2.0")
            implementation("io.github.dipeshlama:barcode-scanner:1.0.0")
        }
    }
}
```

Both libraries are published to Maven Central ([passport-scanner](https://central.sonatype.com/artifact/io.github.dipeshlama/passport-scanner), [barcode-scanner](https://central.sonatype.com/artifact/io.github.dipeshlama/barcode-scanner)), so `mavenCentral()` is all you need in your repositories.

### Versions

The latest releases are built with these versions. Use the same or newer in your app to avoid dependency conflicts.

| Library | Version |
|---|---|
| Compose Multiplatform | 1.12.2 |
| Kotlin | 2.4.21 |
| Material 3 (Compose Multiplatform) | 1.12.0-alpha03 |
| CameraX | 1.4.2 |
| ML Kit text recognition (passport-scanner) | 19.0.1 |
| ML Kit barcode scanning (barcode-scanner) | 17.3.0 |

### Platform setup

**Android:** nothing to do. Both libraries' manifests declare the `CAMERA` permission. `passport-scanner`'s manifest also tells Play services to download the ML Kit text model when the app is installed. If your app already sets `com.google.mlkit.vision.DEPENDENCIES` (for example `ocr,face`), add `tools:replace="android:value"` to your entry and keep `ocr` in its value. `barcode-scanner` bundles its ML Kit model (about 2.4 MB), so it needs no manifest entry and works offline straight after install.

**iOS:** add a camera usage description to your `Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>The camera is used to scan your passport and QR codes.</string>
```

The gallery uses `PHPickerViewController`, which needs no photo library permission.

## Passport scanner

```kotlin
@Composable
fun ScanPassportScreen() {
    var scanning by remember { mutableStateOf(false) }

    if (scanning) {
        PassportScanner(
            onResult = { result ->
                scanning = false
                when (result) {
                    is PassportScanResult.Success -> {
                        val mrz = result.data
                        // mrz.givenNames, mrz.surname, mrz.documentNumber, mrz.nationality,
                        // mrz.dateOfBirth, mrz.expiryDate, mrz.gender, mrz.issuingCountry
                        // result.image: JPEG of the passport page (ByteArray), if you need to upload it
                    }
                    is PassportScanResult.ImageOnly -> {
                        // MRZ unreadable; the user chose to keep the photo and fill in details manually
                    }
                }
            },
            onCancel = { scanning = false },
        )
    } else {
        Button(onClick = { scanning = true }) { Text("Scan passport") }
    }
}
```

`PassportScanner` is a full-screen composable, so it works as a destination in any navigation library (Voyager, Navigation Compose, Decompose and others) or as plain state in a screen, as above. It calls `onResult` once and then stops scanning, so navigate away when it does.

`onCancel` is called for the top bar's back button **and** for system back / the back gesture. The scanner consumes system back while it's shown, so back never falls through and closes your app. Open sheets (review, tips) take system back first and just close.

### Configuration

```kotlin
PassportScanner(
    onResult = { /* ... */ },
    onCancel = { /* ... */ },
    config = PassportScannerConfig(
        reviewBeforeReturning = true, // show a confirm/re-scan sheet first
        allowGallery = true,          // "Upload from gallery" button
        allowImageOnlyResult = true,  // offer "Use this photo" when the MRZ can't be read
        showTips = true,              // tips button and sheet
        showTorch = true,             // flashlight button (hidden if the device has none)
        rejectExpired = false,        // true: ask for another passport if this one has expired
        cropGalleryImage = false,     // true: let the user crop a gallery photo before it's read
    ),
    strings = PassportScannerStrings(
        title = stringResource(Res.string.scan_passport), // localise any text
    ),
)
```

With `cropGalleryImage = true`, the user can crop a gallery photo before it's read. Only the cropped area is read and returned.

With `rejectExpired = true`, an expired passport is never returned: the scanner shows `strings.expiredTitle` with a "Scan another passport" button. A passport is still valid on its expiry date. To check it yourself instead, use `result.data.isExpired()`.

By default, sheet colours and fonts come from your `MaterialTheme`; see [Sheet colours and fonts](#sheet-colours-and-fonts) to change them.

### Customising the UI

There are four levels; use whichever is enough.

**1. Text:** pass `strings = PassportScannerStrings(...)` and override any field.

**2. Colours:** the scan line that sweeps up and down, the frame corners, the scrim, the text/icon colour over the camera, the "Reading passport…" spinner and the camera permission prompt:

```kotlin
colors = PassportScannerDefaults.colors(
    scanLine = Color(0xFF00C853),
    frameCorners = Color.White,
    scrim = Color.Black.copy(alpha = 0.5f),
    content = Color.White,
    processingIndicator = Color(0xFF00C853),     // spinner while a photo is read
    processingScrim = Color.Black.copy(alpha = 0.7f),
    processingText = Color.White,
    permissionText = Color.White,                // "Allow camera access…" message
    permissionButtonContainer = Color(0xFF00C853),
    permissionButtonContent = Color.Black,
)
```

**3. Sheet colours and fonts:** see [below](#sheet-colours-and-fonts).

**4. Slots:** replace a piece of the UI with your own composable. Leave a slot `null` (the default) to keep the built-in one:

```kotlin
PassportScanner(
    onResult = { /* ... */ },
    onCancel = { /* ... */ },
    title = { Text("Verify identity", style = MaterialTheme.typography.titleLarge) },
    instruction = { PassportScannerDefaults.Instruction("Hold the photo page inside the frame") },
    galleryButton = { onClick, enabled ->
        TextButton(onClick = onClick, enabled = enabled) { Text("Choose photo") }
    },
    captureButton = { onClick, enabled ->
        FilledIconButton(onClick = onClick, enabled = enabled) { Icon(MyIcons.Camera, "Capture") }
    },
    captureHint = { Text("Tap if auto-scan doesn't pick it up") },
)
```

| Slot | Replaces | Default text from `strings` |
|---|---|---|
| `title` | Title in the top bar | `title` |
| `instruction` | Message above the frame | `instruction` |
| `galleryButton(onClick, enabled)` | "Upload from gallery" button | `gallery` |
| `captureButton(onClick, enabled)` | Round shutter button | `capture` (accessibility label) |
| `captureHint` | Message under the shutter button | `captureHint` |

Slots are drawn over the camera with `LocalContentColor` set to `colors.content`, so plain `Text`/`Icon` are readable without extra styling. The built-in pieces are public in `PassportScannerDefaults` (`Title`, `Instruction`, `GalleryButton`, `CaptureButton`, `CaptureHint`), so you can reuse or wrap them instead of starting from scratch.

### Sheet colours and fonts

The review, "couldn't read" and tips sheets take their own colours and text styles. Every parameter defaults to your `MaterialTheme`, so override only what you need:

```kotlin
val myFont = FontFamily(Font(Res.font.inter_regular), Font(Res.font.inter_medium, FontWeight.Medium))

PassportScanner(
    onResult = { /* ... */ },
    onCancel = { /* ... */ },
    sheetColors = PassportScannerDefaults.sheetColors(
        container = Color(0xFF1C1B1F),
        title = Color.White,
        body = Color(0xFFCAC4D0),
        label = Color(0xFFCAC4D0),
        value = Color.White,
        divider = Color(0xFF49454F),
        primaryButtonContainer = Color(0xFF00C853),
        primaryButtonContent = Color.Black,
        secondaryButtonContent = Color(0xFF00C853),
        accent = Color(0xFF00C853),
    ),
    sheetTypography = PassportScannerDefaults.sheetTypography(
        fontFamily = myFont, // applied to every sheet text style
        title = MaterialTheme.typography.headlineSmall,
    ),
)
```

| `sheetColors` | Used for |
|---|---|
| `container`, `scrim`, `dragHandle` | Sheet background, dimmed backdrop, top handle |
| `title`, `body` | Sheet titles; descriptions and tips |
| `label`, `value`, `divider` | Review sheet rows |
| `primaryButtonContainer`, `primaryButtonContent` | "Confirm" / "Try again" |
| `secondaryButtonContent` | "Re-scan", "Use this photo", "Got it" (text and outline) |
| `accent` | MRZ highlight in the tips illustration |

`sheetTypography` has `title`, `body`, `label`, `value` and `button` styles. `fontFamily` is applied on top of whichever styles you pass, so you can change the font alone or both font and sizes.

### Logging and personal data

`PassportMrzData.toString()` (and so `PassportScanResult.Success.toString()`) prints `***` for every field. Read the properties (`data.documentNumber`, `data.surname`, …) when you need the values. Equality and `copy()` work as usual.

### State and events

To observe or control the scanner from outside it, pass a `PassportScannerState` and collect its `events` Flow, or read `phase` and `result` as Compose state. The state also has `capture()`, `setTorch(on)` and `restart()`.

```kotlin
val scanner = rememberPassportScannerState()
LaunchedEffect(scanner) {
    scanner.events.collect { event ->
        when (event) {
            is PassportScanEvent.Completed -> onPassport(event.result)
            PassportScanEvent.Cancelled -> navigateBack()
            else -> Unit // Unreadable, Expired
        }
    }
}
PassportScanner(state = scanner, config = PassportScannerConfig(/* ... */))
```

### Without UI

To read an MRZ from an image you already have (a file, a download, a batch job), call `PassportImageScanner.scanImage(bytes)` from any coroutine. It uses the same on-device OCR and returns `PassportMrzData?`.

```kotlin
val data: PassportMrzData? = PassportImageScanner.scanImage(jpegBytes)
```

If you already have OCR text, use the parser on its own:

```kotlin
val data: PassportMrzData? = MrzParser.parse(listOf(line1, line2)) // or MrzParser.parse(text)
```

`MrzParser.find(List<OcrLine>)` does the same with line positions, which lets it rebuild MRZ lines that the OCR engine split into pieces.

## QR & barcode scanner

```kotlin
@Composable
fun ScanCodeScreen() {
    var scanning by remember { mutableStateOf(false) }

    if (scanning) {
        BarcodeScanner(
            onResult = { result ->
                scanning = false
                // result.value: the decoded text; result.format: BarcodeFormat.QrCode, Ean13, ...
            },
            onCancel = { scanning = false },
        )
    } else {
        Button(onClick = { scanning = true }) { Text("Scan code") }
    }
}
```

`BarcodeScanner` is a full-screen composable that calls `onResult` once, and `onCancel` handles both the back button and system back. There is no review sheet: the first code read inside the frame is returned right away, with a short vibration.

### Configuration

```kotlin
BarcodeScanner(
    onResult = { /* ... */ },
    onCancel = { /* ... */ },
    config = BarcodeScannerConfig(
        formats = BarcodeFormat.All,  // or setOf(BarcodeFormat.QrCode), BarcodeFormat.Linear, ...
        frameAspectRatio = 1f,        // width / height of the frame: 1f square for QR, ~2f for 1D barcodes
        allowGallery = true,          // "Upload from gallery" button
        cropGalleryImage = false,     // true: let the user crop a gallery photo before it's read
        showTorch = true,             // flashlight button (hidden if the device has none)
        hapticFeedback = true,        // vibrate when a code is read
    ),
)
```

Limiting `formats` to what you need scans faster and keeps nearby unrelated codes from being picked up. If a gallery photo has no code, the scanner shows `strings.noCodeFound` and keeps scanning.

### Customising the UI

Change any text with `strings = BarcodeScannerStrings(...)` and the overlay colours with `colors = BarcodeScannerDefaults.colors(...)`. Replace parts of the UI with the `title`, `instruction` and `galleryButton` slots. The built-in pieces are public in `BarcodeScannerDefaults` (`Title`, `Instruction`, `GalleryButton`).

## Sample app

The `sample/` folder holds a small app with both scanners. It shows each result, and has switches for gallery cropping and the barcode-only (wide frame) mode. It is separate from the library in `passportscanner/` and uses it the way your own app would.

- `sample/androidApp`: the Android app. `sample/iosApp`: the iOS app. `sample/shared`: the Compose screen both of them show.
- Android: `./gradlew :sample:androidApp:installDebug`
- iOS: open `sample/iosApp/iosApp.xcodeproj` in Xcode and run it on a device. The simulator has no camera, but gallery import works there.

## Limitations

- TD3 (passport booklet) MRZs only; ID cards (TD1/TD2) and visas aren't parsed yet.
- Both live scanners assume the phone is held in portrait.
- The barcode scanner returns one code per scan, as text. Codes without a text value are skipped.
- The review sheet shows dates in ISO format (`yyyy-MM-dd`).

## License

MIT License. See [LICENSE](LICENSE).
