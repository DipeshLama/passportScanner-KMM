package com.example.passportscannerkmm.features.passport

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.passportscannerkmm.features.Feature
import com.example.passportscannerkmm.theme.DemoColors
import com.example.passportscannerkmm.ui.DemoScaffold
import com.example.passportscannerkmm.ui.LabeledRow
import com.example.passportscannerkmm.ui.LabeledValue
import com.example.passportscannerkmm.ui.StatusChip
import io.github.dipeshlama.passportscanner.PassportScanResult
import io.github.dipeshlama.passportscanner.PassportScanner
import io.github.dipeshlama.passportscanner.PassportScannerConfig
import io.github.dipeshlama.passportscanner.PassportScannerDefaults
import org.jetbrains.compose.resources.decodeToImageBitmap

object PassportScannerFeature : Feature {
    override val id = "passport"
    override val title = "Passport scanner"
    override val description = "Scan the photo page and read the MRZ."
    override val glyph = "🛂"

    @Composable
    override fun Content(onBack: () -> Unit) {
        var isScanning by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<PassportScanResult?>(null) }
        var cropGalleryImage by remember { mutableStateOf(true) }

        if (isScanning) {
            PassportScanner(
                onResult = {
                    result = it
                    isScanning = false
                },
                onCancel = { isScanning = false },
                config = PassportScannerConfig(cropGalleryImage = cropGalleryImage),
                colors = PassportScannerDefaults.colors(scanLine = Color(0xFF00C853)),
                instruction = {
                    PassportScannerDefaults.Instruction("Hold your passport's photo page inside the frame")
                },
            )
            return
        }

        DemoScaffold(
            title = "Passport scanner",
            subtitle = "Point the camera at the photo page, or pick a picture from your gallery.",
            onBack = onBack,
        ) {
            val current = result
            if (current == null) {
                EmptyState()
            } else {
                ResultCard(current)
            }
            CropToggle(checked = cropGalleryImage, onCheckedChange = { cropGalleryImage = it })
            Button(
                onClick = { isScanning = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(if (current == null) "Scan passport" else "Scan again", fontWeight = FontWeight.SemiBold)
            }
            if (current != null) {
                OutlinedButton(
                    onClick = { result = null },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Clear result") }
            }
        }
    }
}

@Composable
private fun CropToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Crop gallery photos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Adjust a picked photo before it's read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("🛂", style = MaterialTheme.typography.displayMedium)
            Text("No scan yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Your result will appear here. Data stays in memory and is never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultCard(result: PassportScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val photo = remember(result) {
                result.image?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
            }
            photo?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Scanned passport",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
                )
            }
            when (result) {
                is PassportScanResult.Success -> with(result.data) {
                    val expired = isExpired()
                    StatusChip(
                        text = if (expired) "Expired" else "Valid until $expiryDate",
                        color = if (expired) DemoColors.Danger else DemoColors.Success,
                    )
                    LabeledValue("Full name", "$givenNamesForDisplay $surnameForDisplay")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LabeledRow("Passport no.", documentNumber, "Nationality", nationality)
                    LabeledRow("Date of birth", dateOfBirth.toString(), "Sex", genderLabel(gender.name))
                    LabeledRow("Issued by", issuingCountry, "Expires", expiryDate.toString())
                }
                is PassportScanResult.ImageOnly -> {
                    StatusChip("MRZ not read", DemoColors.Danger)
                    Text(
                        "The photo was captured, but the machine-readable zone couldn't be decoded. Try again with better light.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun genderLabel(name: String): String =
    name.lowercase().replaceFirstChar { it.uppercase() }
