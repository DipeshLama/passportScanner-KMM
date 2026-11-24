package io.github.dipeshlama.barcodescanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dipeshlama.barcodescanner.internal.ScannerIcons

/**
 * Colours of the scanner's camera overlay. Create with [BarcodeScannerDefaults.colors].
 *
 * @property scanLine the line moving down inside the frame.
 * @property frameCorners the corner brackets of the frame, and the frame of the crop screen.
 * @property scrim the dimmed area outside the frame, and outside the crop on the crop screen.
 * @property content text and icons shown over the camera.
 * @property chipBackground background of the instruction text, the "no code found" message and the gallery button.
 * @property processingIndicator the spinner shown while a photo is read.
 * @property processingScrim the dimmed overlay behind the spinner.
 * @property processingText the "Looking for a code…" text.
 * @property permissionText the explanation shown in the frame when camera access is missing.
 * @property permissionButtonContainer background of the "Allow camera" button and the crop screen's "Continue" button.
 * @property permissionButtonContent text of the "Allow camera" button and the crop screen's "Continue" button.
 */
@Immutable
public class BarcodeScannerColors(
    public val scanLine: Color,
    public val frameCorners: Color,
    public val scrim: Color,
    public val content: Color,
    public val chipBackground: Color,
    public val processingIndicator: Color,
    public val processingScrim: Color,
    public val processingText: Color,
    public val permissionText: Color,
    public val permissionButtonContainer: Color,
    public val permissionButtonContent: Color,
)

/** Default colours and UI pieces of [BarcodeScanner], for use in custom slots. */
public object BarcodeScannerDefaults {
    @Composable
    public fun colors(
        scanLine: Color = MaterialTheme.colorScheme.primary,
        frameCorners: Color = Color.White,
        scrim: Color = Color.Black.copy(alpha = 0.4f),
        content: Color = Color.White,
        chipBackground: Color = Color.Black.copy(alpha = 0.25f),
        processingIndicator: Color = MaterialTheme.colorScheme.primary,
        processingScrim: Color = Color.Black.copy(alpha = 0.6f),
        processingText: Color = Color.White,
        permissionText: Color = content,
        permissionButtonContainer: Color = MaterialTheme.colorScheme.primary,
        permissionButtonContent: Color = MaterialTheme.colorScheme.onPrimary,
    ): BarcodeScannerColors = BarcodeScannerColors(
        scanLine = scanLine,
        frameCorners = frameCorners,
        scrim = scrim,
        content = content,
        chipBackground = chipBackground,
        processingIndicator = processingIndicator,
        processingScrim = processingScrim,
        processingText = processingText,
        permissionText = permissionText,
        permissionButtonContainer = permissionButtonContainer,
        permissionButtonContent = permissionButtonContent,
    )

    @Composable
    public fun Title(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = LocalContentColor.current,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
    }

    @Composable
    public fun Instruction(
        text: String,
        modifier: Modifier = Modifier,
        backgroundColor: Color = Color.Black.copy(alpha = 0.25f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = LocalContentColor.current,
            textAlign = TextAlign.Center,
            modifier = modifier
                .background(backgroundColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    @Composable
    public fun GalleryButton(
        text: String,
        onClick: () -> Unit,
        enabled: Boolean,
        modifier: Modifier = Modifier,
        backgroundColor: Color = Color.Black.copy(alpha = 0.25f),
    ) {
        val contentColor = LocalContentColor.current
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = backgroundColor,
                contentColor = contentColor,
                disabledContainerColor = backgroundColor,
                disabledContentColor = contentColor.copy(alpha = 0.4f)
            ),
            border = BorderStroke(1.dp, contentColor.copy(alpha = if (enabled) 1f else 0.4f))
        ) {
            Icon(ScannerIcons.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(text)
        }
    }
}
