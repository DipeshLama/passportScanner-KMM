package io.github.dipeshlama.passportscanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dipeshlama.passportscanner.internal.ScannerIcons

/**
 * Colours of the scanner's camera overlay. Create with [PassportScannerDefaults.colors].
 *
 * @property scanLine the line moving up and down inside the frame.
 * @property frameCorners the corner brackets of the frame.
 * @property scrim the dimmed area outside the frame.
 * @property content text and icons shown over the camera.
 * @property chipBackground background of the instruction text and gallery button.
 */
@Immutable
class PassportScannerColors(
    val scanLine: Color,
    val frameCorners: Color,
    val scrim: Color,
    val content: Color,
    val chipBackground: Color,
)

/**
 * Colours of the bottom sheets (review, unreadable passport and tips).
 * Create with [PassportScannerDefaults.sheetColors].
 *
 * @property container sheet background.
 * @property scrim dimmed area behind the sheet.
 * @property dragHandle the handle at the top of the sheet.
 * @property title sheet titles.
 * @property body descriptions and tips.
 * @property label field labels in the review sheet.
 * @property value field values in the review sheet.
 * @property divider lines between review fields.
 * @property primaryButtonContainer background of the main button ("Confirm", "Try again").
 * @property primaryButtonContent text of the main button.
 * @property secondaryButtonContent text and outline of the other buttons ("Re-scan", "Got it").
 * @property accent highlight around the MRZ in the tips illustration.
 */
@Immutable
class PassportScannerSheetColors(
    val container: Color,
    val scrim: Color,
    val dragHandle: Color,
    val title: Color,
    val body: Color,
    val label: Color,
    val value: Color,
    val divider: Color,
    val primaryButtonContainer: Color,
    val primaryButtonContent: Color,
    val secondaryButtonContent: Color,
    val accent: Color,
)

/**
 * Text styles of the bottom sheets. Set a `fontFamily` on these to use your own font.
 * Create with [PassportScannerDefaults.sheetTypography].
 *
 * @property title sheet titles.
 * @property body descriptions and tips.
 * @property label field labels in the review sheet.
 * @property value field values in the review sheet.
 * @property button button text.
 */
@Immutable
class PassportScannerSheetTypography(
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val value: TextStyle,
    val button: TextStyle,
)

/** Default colours and UI pieces of [PassportScanner], for use in custom slots. */
object PassportScannerDefaults {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun sheetColors(
        container: Color = BottomSheetDefaults.ContainerColor,
        scrim: Color = BottomSheetDefaults.ScrimColor,
        dragHandle: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        title: Color = MaterialTheme.colorScheme.onSurface,
        body: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        label: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        value: Color = MaterialTheme.colorScheme.onSurface,
        divider: Color = MaterialTheme.colorScheme.outlineVariant,
        primaryButtonContainer: Color = MaterialTheme.colorScheme.primary,
        primaryButtonContent: Color = MaterialTheme.colorScheme.onPrimary,
        secondaryButtonContent: Color = MaterialTheme.colorScheme.primary,
        accent: Color = MaterialTheme.colorScheme.primary,
    ): PassportScannerSheetColors = PassportScannerSheetColors(
        container = container,
        scrim = scrim,
        dragHandle = dragHandle,
        title = title,
        body = body,
        label = label,
        value = value,
        divider = divider,
        primaryButtonContainer = primaryButtonContainer,
        primaryButtonContent = primaryButtonContent,
        secondaryButtonContent = secondaryButtonContent,
        accent = accent,
    )

    /**
     * Pass [fontFamily] to change the font of every sheet text at once, or override single styles.
     */
    @Composable
    fun sheetTypography(
        fontFamily: FontFamily? = null,
        title: TextStyle = MaterialTheme.typography.titleLarge,
        body: TextStyle = MaterialTheme.typography.bodyMedium,
        label: TextStyle = MaterialTheme.typography.bodyMedium,
        value: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        button: TextStyle = MaterialTheme.typography.labelLarge,
    ): PassportScannerSheetTypography {
        fun TextStyle.withFont() = if (fontFamily != null) copy(fontFamily = fontFamily) else this
        return PassportScannerSheetTypography(
            title = title.withFont(),
            body = body.withFont(),
            label = label.withFont(),
            value = value.withFont(),
            button = button.withFont(),
        )
    }

    @Composable
    fun colors(
        scanLine: Color = MaterialTheme.colorScheme.primary,
        frameCorners: Color = Color.White,
        scrim: Color = Color.Black.copy(alpha = 0.4f),
        content: Color = Color.White,
        chipBackground: Color = Color.Black.copy(alpha = 0.25f),
    ): PassportScannerColors = PassportScannerColors(
        scanLine = scanLine,
        frameCorners = frameCorners,
        scrim = scrim,
        content = content,
        chipBackground = chipBackground,
    )

    @Composable
    fun Title(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = LocalContentColor.current,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
    }

    @Composable
    fun Instruction(
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
    fun GalleryButton(
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

    @Composable
    fun CaptureButton(
        onClick: () -> Unit,
        enabled: Boolean,
        contentDescription: String,
        modifier: Modifier = Modifier,
        color: Color = LocalContentColor.current,
    ) {
        val tint = color.copy(alpha = if (enabled) 1f else 0.4f)
        Box(
            modifier = modifier
                .size(72.dp)
                .border(4.dp, tint, CircleShape)
                .padding(8.dp)
                .clip(CircleShape)
                .background(tint)
                .semantics { this.contentDescription = contentDescription }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        )
    }

    @Composable
    fun CaptureHint(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
    }
}
