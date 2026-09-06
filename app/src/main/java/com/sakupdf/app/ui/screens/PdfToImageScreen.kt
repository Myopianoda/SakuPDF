package com.sakupdf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.sakupdf.app.ui.components.PageGrid
import com.sakupdf.app.ui.components.SakuPDFTopAppBar
import com.sakupdf.app.ui.theme.PrimaryContainer
import com.sakupdf.app.ui.theme.SurfaceContainerLowest
import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImageScreen(
    viewModel: SakuPDFViewModel,
    onNavigateBack: () -> Unit,
    onStartProcess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val selectedCount = uiState.pdfToImagePages.count { it.isSelected }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setPdfToImageSourcePdf(context.contentResolver, uri)
        }
    }

    val destinationFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.startRealPdfToImageConversion(
                context = context,
                contentResolver = context.contentResolver,
                destinationTreeUri = treeUri,
                onNavigateToProcessing = onStartProcess
            )
        }
    }

    Scaffold(
        topBar = {
            SakuPDFTopAppBar(
                title = "PDF ke Gambar",
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
                        onClick = {
                            val source = uiState.pdfToImageSourcePdf
                            if (source == null || source.uri == null) {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            } else if (selectedCount == 0) {
                                viewModel.setErrorMessage("Pilih setidaknya 1 halaman untuk diubah ke gambar.")
                            } else {
                                destinationFolderLauncher.launch(null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.pdfToImageSourcePdf == null) "Pilih Dokumen PDF" else "Pilih Folder & Ubah ($selectedCount)")
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
            // Document Card
            val sourceDoc = uiState.pdfToImageSourcePdf
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                    },
                color = SurfaceContainerLowest,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = sourceDoc?.name ?: "Pilih Dokumen PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (sourceDoc != null) "${sourceDoc.sizeFormatted} • ${sourceDoc.pages} Halaman" else "Ketuk untuk memilih file PDF",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Format Output
            Text("Format Output", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("JPG", "PNG").forEach { format ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = uiState.pdfToImageFormat == format,
                            onClick = { viewModel.setPdfToImageFormat(format) }
                        )
                        Text(format, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Kualitas
            Text("Kualitas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("Standar", "Tinggi").forEach { q ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = uiState.pdfToImageQuality == q,
                            onClick = { viewModel.setPdfToImageQuality(q) }
                        )
                        Text(q, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Pilih Halaman
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pilih Halaman", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(shape = CircleShape, color = PrimaryContainer) {
                    Text(
                        text = "$selectedCount Dipilih",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            PageGrid(
                pages = uiState.pdfToImagePages,
                onPageToggle = { viewModel.togglePdfToImagePage(it) },
                modifier = Modifier.height(240.dp)
            )
        }
    }
}
