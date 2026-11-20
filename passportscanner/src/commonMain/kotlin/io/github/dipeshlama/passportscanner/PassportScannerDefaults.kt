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
import androidx.compose.material3.ButtonDefaults
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

/** Default colours and UI pieces of [PassportScanner], for use in custom slots. */
object PassportScannerDefaults {
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
