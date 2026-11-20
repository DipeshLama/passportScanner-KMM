package com.example.passportscannerkmm

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.dipeshlama.passportscanner.PassportScanResult
import io.github.dipeshlama.passportscanner.PassportScanner
import io.github.dipeshlama.passportscanner.PassportScannerDefaults
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
fun App() {
    MaterialTheme {
        var isScanning by remember { mutableStateOf(false) }
        var lastResult by remember { mutableStateOf<PassportScanResult?>(null) }

        if (isScanning) {
            PassportScanner(
                onResult = { result ->
                    lastResult = result
                    isScanning = false
                },
                onCancel = { isScanning = false },
                colors = PassportScannerDefaults.colors(scanLine = Color(0xFF00C853)),
                instruction = {
                    PassportScannerDefaults.Instruction("Hold your passport's photo page inside the frame")
                },
            )
        } else {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .safeContentPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(onClick = { isScanning = true }) { Text("Scan passport") }
                    lastResult?.let { ResultCard(it) }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: PassportScanResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val photo = remember(result) { result.image?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
            photo?.let { Image(it, contentDescription = "Scanned passport", modifier = Modifier.fillMaxWidth()) }
            when (result) {
                is PassportScanResult.Success -> with(result.data) {
                    Text("$givenNamesForDisplay $surnameForDisplay", style = MaterialTheme.typography.titleMedium)
                    Text("Passport no: $documentNumber")
                    Text("Nationality: $nationality  ·  Issued by: $issuingCountry")
                    Text("Date of birth: $dateOfBirth  ·  Sex: $gender")
                    Text("Expires: $expiryDate")
                }
                is PassportScanResult.ImageOnly -> Text("MRZ not read: photo only")
            }
        }
    }
}
