package com.sakupdf.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakupdf.app.domain.PdfMathUtils
import com.sakupdf.app.ui.components.EmptyState
import com.sakupdf.app.ui.components.SakuPDFTopAppBar
import com.sakupdf.app.ui.theme.PrimaryContainer
import com.sakupdf.app.ui.theme.SurfaceContainerLowest
import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel

@Composable
fun MergePdfScreen(
    viewModel: SakuPDFViewModel,
    onNavigateBack: () -> Unit,
    onStartProcess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.userNotificationMessage) {
        uiState.userNotificationMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearNotificationMessage()
        }
    }

    val openPdfsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPdfsToMerge(context.contentResolver, uris)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        if (targetUri != null) {
            viewModel.startRealMergePdfConversion(
                contentResolver = context.contentResolver,
                context = context,
                targetUri = targetUri,
                onNavigateToProcessing = onStartProcess
            )
        }
    }

    if (uiState.isClearAllMergePdfsDialogVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearAllMergePdfs() },
            title = { Text("Hapus Semua File PDF?") },
            text = { Text("Semua file PDF yang telah dipilih akan dihapus dari daftar penggabungan.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmClearAllMergePdfs() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Semua")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearAllMergePdfs() }) {
                    Text("Batal")
                }
            }
        )
    }

    val pdfCount = uiState.pdfsToMerge.size
    val totalPages = uiState.pdfsToMerge.sumOf { it.pages }
    val isValidToMerge = pdfCount >= 2

    Scaffold(
        topBar = {
            SakuPDFTopAppBar(
                title = "Gabungkan PDF",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    if (pdfCount > 0) {
                        TextButton(onClick = { viewModel.requestClearAllMergePdfs() }) {
                            Text("Hapus Semua", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$pdfCount file dipilih • Total $totalPages halaman",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val sanitized = PdfMathUtils.sanitizeFilename(uiState.mergePdfFilename)
                            createDocumentLauncher.launch(sanitized)
                        },
                        enabled = isValidToMerge,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gabungkan PDF")
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
        ) {
            if (pdfCount == 0) {
                EmptyState(
                    title = "Belum Ada File PDF",
                    description = "Pilih setidaknya 2 file PDF untuk digabungkan menjadi satu dokumen.",
                    actionLabel = "Pilih File PDF",
                    onAction = { openPdfsLauncher.launch(arrayOf("application/pdf")) }
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = PrimaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Urutkan file sesuai hasil PDF yang diinginkan. Gunakan tombol panah untuk mengubah posisi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Custom Output Filename Field
                OutlinedTextField(
                    value = uiState.mergePdfFilename,
                    onValueChange = { viewModel.setMergePdfFilename(it) },
                    label = { Text("Nama PDF Hasil") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
                ) {
                    itemsIndexed(uiState.pdfsToMerge, key = { _, doc -> doc.id }) { index, doc ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            color = SurfaceContainerLowest,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Up / Down Buttons
                                Column {
                                    IconButton(
                                        onClick = { viewModel.moveMergePdfUp(index) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Naikkan", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveMergePdfDown(index) },
                                        enabled = index < pdfCount - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Turunkan", modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${doc.sizeFormatted} • ${doc.pages} halaman",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteMergePdf(doc.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = { openPdfsLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tambah PDF")
                        }
                    }
                }
            }
        }
    }
}
