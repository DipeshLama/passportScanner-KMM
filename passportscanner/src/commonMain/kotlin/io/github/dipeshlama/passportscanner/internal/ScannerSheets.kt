package io.github.dipeshlama.passportscanner.internal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dipeshlama.passportscanner.PassportScannerSheetColors
import io.github.dipeshlama.passportscanner.PassportScannerSheetTypography
import io.github.dipeshlama.passportscanner.PassportScannerStrings
import io.github.dipeshlama.passportscanner.mrz.MrzGender
import io.github.dipeshlama.passportscanner.mrz.PassportMrzData
import kotlinx.coroutines.launch

/** Colours and text styles shared by every scanner sheet. */
internal class SheetStyle(
    val colors: PassportScannerSheetColors,
    val typography: PassportScannerSheetTypography,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerSheet(
    isVisible: Boolean,
    style: SheetStyle,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.(hideThen: (() -> Unit) -> Unit) -> Unit
) {
    if (!isVisible) return
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val scope = rememberCoroutineScope()
    val hideThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = style.colors.container,
        contentColor = style.colors.title,
        scrimColor = style.colors.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = style.colors.dragHandle) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content(hideThen)
        }
    }
}

@Composable
private fun SheetTitle(text: String, style: SheetStyle) {
    Text(text, style = style.typography.title, color = style.colors.title)
}

@Composable
private fun SheetBody(text: String, style: SheetStyle, modifier: Modifier = Modifier) {
    Text(text, style = style.typography.body, color = style.colors.body, modifier = modifier)
}

@Composable
private fun PrimaryButton(text: String, style: SheetStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = style.colors.primaryButtonContainer,
            contentColor = style.colors.primaryButtonContent
        )
    ) {
        Text(text, style = style.typography.button)
    }
}

@Composable
private fun SecondaryButton(text: String, style: SheetStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = style.colors.secondaryButtonContent),
        border = BorderStroke(1.dp, style.colors.secondaryButtonContent)
    ) {
        Text(text, style = style.typography.button)
    }
}

@Composable
internal fun UnreadableSheet(
    scan: CameraScan?,
    strings: PassportScannerStrings,
    style: SheetStyle,
    allowImageOnly: Boolean,
    onRetry: () -> Unit,
    onUsePhoto: () -> Unit
) {
    ScannerSheet(isVisible = scan != null, style = style, onDismissRequest = onRetry) { hideThen ->
        SheetTitle(strings.failedTitle, style)
        SheetBody(strings.failedDescription, style)
        PrimaryButton(strings.tryAgain, style, onClick = { hideThen(onRetry) }, modifier = Modifier.fillMaxWidth())
        if (allowImageOnly && scan?.image != null) {
            SecondaryButton(
                strings.useThisPhoto,
                style,
                onClick = { hideThen(onUsePhoto) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun ReviewSheet(
    data: PassportMrzData?,
    strings: PassportScannerStrings,
    style: SheetStyle,
    onRescan: () -> Unit,
    onConfirm: () -> Unit
) {
    ScannerSheet(isVisible = data != null, style = style, onDismissRequest = onRescan) { hideThen ->
        val details = data ?: return@ScannerSheet
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SheetTitle(strings.reviewTitle, style)
            SheetBody(strings.reviewDescription, style)
        }
        Column {
            DetailRow(strings.labelSurname, details.surnameForDisplay, style)
            DetailRow(strings.labelGivenNames, details.givenNamesForDisplay, style)
            DetailRow(strings.labelDocumentNumber, details.documentNumber, style)
            DetailRow(strings.labelNationality, details.nationality, style)
            DetailRow(strings.labelDateOfBirth, details.dateOfBirth.toString(), style)
            DetailRow(
                strings.labelSex,
                when (details.gender) {
                    MrzGender.MALE -> strings.sexMale
                    MrzGender.FEMALE -> strings.sexFemale
                    MrzGender.UNSPECIFIED -> strings.sexUnspecified
                },
                style
            )
            DetailRow(strings.labelExpiryDate, details.expiryDate.toString(), style)
            DetailRow(strings.labelIssuingCountry, details.issuingCountry, style, showDivider = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(strings.rescan, style, onClick = { hideThen(onRescan) }, modifier = Modifier.weight(1f))
            PrimaryButton(strings.confirm, style, onClick = { hideThen(onConfirm) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, style: SheetStyle, showDivider: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = style.typography.label,
            color = style.colors.label,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, style = style.typography.value, color = style.colors.value)
    }
    if (showDivider) HorizontalDivider(color = style.colors.divider)
}

@Composable
internal fun TipsSheet(
    isVisible: Boolean,
    strings: PassportScannerStrings,
    style: SheetStyle,
    onDismiss: () -> Unit
) {
    ScannerSheet(isVisible = isVisible, style = style, onDismissRequest = onDismiss) { hideThen ->
        SheetTitle(strings.tipsTitle, style)
        PassportIllustration(
            accent = style.colors.accent,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(240.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            strings.tipsItems.forEach { tip ->
                Row {
                    SheetBody("•", style, modifier = Modifier.width(16.dp))
                    SheetBody(tip, style)
                }
            }
        }
        TextButton(
            onClick = { hideThen(onDismiss) },
            modifier = Modifier.align(Alignment.End),
            colors = ButtonDefaults.textButtonColors(contentColor = style.colors.secondaryButtonContent)
        ) {
            Text(strings.tipsDismiss, style = style.typography.button)
        }
    }
}

@Composable
private fun PassportIllustration(accent: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val placeholder = colors.onSurfaceVariant.copy(alpha = 0.25f)
    Column(
        modifier = modifier
            .aspectRatio(125f / 88f)
            .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(width = 48.dp, height = 60.dp)
                    .background(placeholder, RoundedCornerShape(4.dp))
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5f, 0.8f, 0.65f, 0.7f, 0.45f).forEach { fraction ->
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .background(placeholder, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, accent, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            listOf(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
            ).forEach { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.sp,
                    lineHeight = 9.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    color = colors.onSurface
                )
            }
        }
    }
}
