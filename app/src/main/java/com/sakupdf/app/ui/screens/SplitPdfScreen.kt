package com.sakupdf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.sakupdf.app.domain.SplitRangeParser
import com.sakupdf.app.domain.SplitRangeResult
import com.sakupdf.app.model.SplitMethod
import com.sakupdf.app.ui.components.PageGrid
import com.sakupdf.app.ui.components.SakuPDFTopAppBar
import com.sakupdf.app.ui.theme.PrimaryContainer
import com.sakupdf.app.ui.theme.SurfaceContainerLowest
import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel

@Composable
fun SplitPdfScreen(
    viewModel: SakuPDFViewModel,
    onNavigateBack: () -> Unit,
    onStartProcess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setSplitSourcePdf(context.contentResolver, uri)
        }
    }

    val destinationFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.startRealSplitPdfConversion(
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
                title = "Pisahkan PDF",
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
                            val source = uiState.splitSourcePdf
                            if (source == null || source.uri == null) {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            } else {
                                if (uiState.splitMethod == SplitMethod.CUSTOM) {
                                    val parse = SplitRangeParser.parse(uiState.splitCustomRange, source.pages)
                                    if (parse is SplitRangeResult.Error) {
                                        viewModel.setErrorMessage(parse.message)
                                        return@Button
                                    }
                                } else if (uiState.splitMethod == SplitMethod.VISUAL) {
                                    if (uiState.splitPageItems.none { it.isSelected }) {
                                        viewModel.setErrorMessage("Pilih setidaknya 1 halaman untuk dipisahkan.")
                                        return@Button
                                    }
                                }
                                destinationFolderLauncher.launch(null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.splitSourcePdf == null) "Pilih Dokumen PDF" else "Pilih Folder & Pisahkan")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Document Card
            val sourceDoc = uiState.splitSourcePdf
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sourceDoc?.name ?: "Pilih Dokumen PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (sourceDoc != null) "${sourceDoc.pages} Halaman • ${sourceDoc.sizeFormatted}" else "Ketuk untuk memilih file PDF",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text("Metode Pemisahan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // Method 1: All
            SplitMethodOptionCard(
                title = SplitMethod.ALL.title,
                description = SplitMethod.ALL.description,
                icon = Icons.Default.Layers,
                isSelected = uiState.splitMethod == SplitMethod.ALL,
                onClick = { viewModel.setSplitMethod(SplitMethod.ALL) }
            )

            // Method 2: Custom Range
            SplitMethodOptionCard(
                title = SplitMethod.CUSTOM.title,
                description = SplitMethod.CUSTOM.description,
                icon = Icons.Default.FormatListNumbered,
                isSelected = uiState.splitMethod == SplitMethod.CUSTOM,
                onClick = { viewModel.setSplitMethod(SplitMethod.CUSTOM) }
            ) {
                if (uiState.splitMethod == SplitMethod.CUSTOM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.splitCustomRange,
                        onValueChange = { viewModel.setSplitCustomRange(it) },
                        label = { Text("Rentang Halaman (contoh: 1-5, 8, 11-14)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Text(
                        text = "Pisahkan dengan koma atau gunakan tanda hubung untuk rentang.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Method 3: Visual
            SplitMethodOptionCard(
                title = SplitMethod.VISUAL.title,
                description = SplitMethod.VISUAL.description,
                icon = Icons.Default.GridView,
                isSelected = uiState.splitMethod == SplitMethod.VISUAL,
                onClick = { viewModel.setSplitMethod(SplitMethod.VISUAL) }
            ) {
                if (uiState.splitMethod == SplitMethod.VISUAL) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PageGrid(
                        pages = uiState.splitPageItems,
                        onPageToggle = { viewModel.toggleSplitPage(it) },
                        modifier = Modifier.height(220.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SplitMethodOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    extraContent: @Composable () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = SurfaceContainerLowest,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isSelected, onClick = onClick)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            extraContent()
        }
    }
}
