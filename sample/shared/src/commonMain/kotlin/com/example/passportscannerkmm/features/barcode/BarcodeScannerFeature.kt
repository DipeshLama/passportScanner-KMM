package com.example.passportscannerkmm.features.barcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.passportscannerkmm.features.Feature
import com.example.passportscannerkmm.theme.DemoColors
import com.example.passportscannerkmm.ui.DemoScaffold
import com.example.passportscannerkmm.ui.LabeledValue
import com.example.passportscannerkmm.ui.StatusChip
import io.github.dipeshlama.barcodescanner.BarcodeFormat
import io.github.dipeshlama.barcodescanner.BarcodeScanResult
import io.github.dipeshlama.barcodescanner.BarcodeScanner
import io.github.dipeshlama.barcodescanner.BarcodeScannerConfig
import io.github.dipeshlama.barcodescanner.BarcodeScannerDefaults

object BarcodeScannerFeature : Feature {
    override val id = "barcode"
    override val title = "QR & barcode scanner"
    override val description = "Read QR codes and product barcodes."
    override val glyph = "🔳"

    @Composable
    override fun Content(onBack: () -> Unit) {
        var isScanning by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<BarcodeScanResult?>(null) }
        var cropGalleryImage by remember { mutableStateOf(true) }
        var linearOnly by remember { mutableStateOf(false) }

        if (isScanning) {
            BarcodeScanner(
                onResult = {
                    result = it
                    isScanning = false
                },
                onCancel = { isScanning = false },
                config = if (linearOnly) {
                    BarcodeScannerConfig(
                        formats = BarcodeFormat.Linear,
                        frameAspectRatio = 2f,
                        cropGalleryImage = cropGalleryImage,
                    )
                } else {
                    BarcodeScannerConfig(cropGalleryImage = cropGalleryImage)
                },
                colors = BarcodeScannerDefaults.colors(scanLine = Color(0xFF00C853)),
            )
            return
        }

        DemoScaffold(
            title = "QR & barcode scanner",
            subtitle = "Point the camera at a code, or pick a picture from your gallery.",
            onBack = onBack,
        ) {
            val current = result
            if (current == null) EmptyState() else ResultCard(current)
            SettingToggle(
                title = "Barcodes only",
                description = "Wide frame for product barcodes; QR codes are ignored.",
                checked = linearOnly,
                onCheckedChange = { linearOnly = it },
            )
            SettingToggle(
                title = "Crop gallery photos",
                description = "Pick the code to read when a photo has several.",
                checked = cropGalleryImage,
                onCheckedChange = { cropGalleryImage = it },
            )
            Button(
                onClick = { isScanning = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(if (current == null) "Scan code" else "Scan again", fontWeight = FontWeight.SemiBold)
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
private fun SettingToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
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
            Text("🔳", style = MaterialTheme.typography.displayMedium)
            Text("No scan yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "The code's content will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultCard(result: BarcodeScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusChip(text = result.format.label(), color = DemoColors.Success)
            LabeledValue("Content", result.value)
        }
    }
}

private fun BarcodeFormat.label(): String = when (this) {
    BarcodeFormat.QrCode -> "QR code"
    BarcodeFormat.Aztec -> "Aztec"
    BarcodeFormat.DataMatrix -> "Data Matrix"
    BarcodeFormat.Pdf417 -> "PDF417"
    BarcodeFormat.Code128 -> "Code 128"
    BarcodeFormat.Code39 -> "Code 39"
    BarcodeFormat.Code93 -> "Code 93"
    BarcodeFormat.Codabar -> "Codabar"
    BarcodeFormat.Ean13 -> "EAN-13"
    BarcodeFormat.Ean8 -> "EAN-8"
    BarcodeFormat.Itf -> "ITF"
    BarcodeFormat.UpcA -> "UPC-A"
    BarcodeFormat.UpcE -> "UPC-E"
}
