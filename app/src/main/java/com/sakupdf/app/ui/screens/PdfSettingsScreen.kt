package com.sakupdf.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sakupdf.app.ui.components.SakuPDFTopAppBar
import com.sakupdf.app.ui.theme.PrimaryContainer
import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSettingsScreen(
    viewModel: SakuPDFViewModel,
    onNavigateBack: () -> Unit,
    onStartProcess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            SakuPDFTopAppBar(
                title = "Atur PDF",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = onStartProcess,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buat PDF")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Lock, contentDescription = "Privat", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PrimaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "4 Gambar Dipilih",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Siap digabungkan menjadi PDF.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Nama File
            OutlinedTextField(
                value = uiState.pdfExportName,
                onValueChange = { viewModel.setPdfExportName(it) },
                label = { Text("Nama File") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            // Ukuran Halaman
            Text("Ukuran Halaman", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Otomatis", "A4", "Letter").forEach { size ->
                    FilterChip(
                        selected = uiState.pdfPageSize == size,
                        onClick = { viewModel.setPdfPageSize(size) },
                        label = { Text(size) }
                    )
                }
            }

            // Orientasi
            Text("Orientasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.pdfOrientation == "Potret",
                    onClick = { viewModel.setPdfOrientation("Potret") },
                    leadingIcon = { Icon(Icons.Default.CropPortrait, contentDescription = null) },
                    label = { Text("Potret") }
                )
                FilterChip(
                    selected = uiState.pdfOrientation == "Lanskap",
                    onClick = { viewModel.setPdfOrientation("Lanskap") },
                    leadingIcon = { Icon(Icons.Default.CropLandscape, contentDescription = null) },
                    label = { Text("Lanskap") }
                )
            }

            // Margin
            Text("Margin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Tanpa margin", "Kecil", "Sedang").forEach { margin ->
                    FilterChip(
                        selected = uiState.pdfMargin == margin,
                        onClick = { viewModel.setPdfMargin(margin) },
                        label = { Text(margin) }
                    )
                }
            }

            // Kualitas Gambar
            Text("Kualitas Gambar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            listOf("Hemat ruang", "Seimbang (Default)", "Tinggi").forEach { q ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = uiState.pdfQuality == q,
                        onClick = { viewModel.setPdfQuality(q) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(q, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
