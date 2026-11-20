package io.github.dipeshlama.passportscanner.internal

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dipeshlama.passportscanner.PassportScannerStrings
import io.github.dipeshlama.passportscanner.mrz.MrzGender
import io.github.dipeshlama.passportscanner.mrz.PassportMrzData
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.(hideThen: (() -> Unit) -> Unit) -> Unit
) {
    if (!isVisible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hideThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
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
internal fun UnreadableSheet(
    scan: CameraScan?,
    strings: PassportScannerStrings,
    allowImageOnly: Boolean,
    onRetry: () -> Unit,
    onUsePhoto: () -> Unit
) {
    ScannerSheet(isVisible = scan != null, onDismissRequest = onRetry) { hideThen ->
        Text(strings.failedTitle, style = MaterialTheme.typography.titleLarge)
        Text(
            strings.failedDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { hideThen(onRetry) }, modifier = Modifier.fillMaxWidth()) {
            Text(strings.tryAgain)
        }
        if (allowImageOnly && scan?.image != null) {
            OutlinedButton(onClick = { hideThen(onUsePhoto) }, modifier = Modifier.fillMaxWidth()) {
                Text(strings.useThisPhoto)
            }
        }
    }
}

@Composable
internal fun ReviewSheet(
    data: PassportMrzData?,
    strings: PassportScannerStrings,
    onRescan: () -> Unit,
    onConfirm: () -> Unit
) {
    ScannerSheet(isVisible = data != null, onDismissRequest = onRescan) { hideThen ->
        val details = data ?: return@ScannerSheet
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(strings.reviewTitle, style = MaterialTheme.typography.titleLarge)
            Text(
                strings.reviewDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column {
            DetailRow(strings.labelSurname, details.surnameForDisplay)
            DetailRow(strings.labelGivenNames, details.givenNamesForDisplay)
            DetailRow(strings.labelDocumentNumber, details.documentNumber)
            DetailRow(strings.labelNationality, details.nationality)
            DetailRow(strings.labelDateOfBirth, details.dateOfBirth.toString())
            DetailRow(
                strings.labelSex,
                when (details.gender) {
                    MrzGender.MALE -> strings.sexMale
                    MrzGender.FEMALE -> strings.sexFemale
                    MrzGender.UNSPECIFIED -> strings.sexUnspecified
                }
            )
            DetailRow(strings.labelExpiryDate, details.expiryDate.toString())
            DetailRow(strings.labelIssuingCountry, details.issuingCountry, showDivider = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { hideThen(onRescan) }, modifier = Modifier.weight(1f)) {
                Text(strings.rescan)
            }
            Button(onClick = { hideThen(onConfirm) }, modifier = Modifier.weight(1f)) {
                Text(strings.confirm)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, showDivider: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
    if (showDivider) HorizontalDivider()
}

@Composable
internal fun TipsSheet(
    isVisible: Boolean,
    strings: PassportScannerStrings,
    onDismiss: () -> Unit
) {
    ScannerSheet(isVisible = isVisible, onDismissRequest = onDismiss) { hideThen ->
        Text(strings.tipsTitle, style = MaterialTheme.typography.titleLarge)
        PassportIllustration(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(240.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            strings.tipsItems.forEach { tip ->
                Row {
                    Text("•", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(tip, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        TextButton(onClick = { hideThen(onDismiss) }, modifier = Modifier.align(Alignment.End)) {
            Text(strings.tipsDismiss)
        }
    }
}

@Composable
private fun PassportIllustration(modifier: Modifier = Modifier) {
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
                .border(1.5.dp, colors.primary, RoundedCornerShape(4.dp))
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
