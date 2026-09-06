package com.sakupdf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
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
import com.sakupdf.app.ui.components.ConfirmDeleteDialog
import com.sakupdf.app.ui.components.SakuPDFTopAppBar
import com.sakupdf.app.ui.theme.PrimaryContainer
import com.sakupdf.app.ui.theme.SurfaceContainerLowest
import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel

@Composable
fun FileDetailScreen(
    viewModel: SakuPDFViewModel,
    onNavigateBack: () -> Unit,
    onDelete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val doc = uiState.activeDocument ?: return

    if (uiState.deleteCandidate != null) {
        ConfirmDeleteDialog(
            fileName = uiState.deleteCandidate!!.name,
            onConfirm = {
                viewModel.confirmDelete(context)
                onDelete()
            },
            onDismiss = { viewModel.dismissDelete() }
        )
    }

    if (uiState.renameCandidate != null) {
        com.sakupdf.app.ui.components.RenameDocumentDialog(
            initialName = uiState.renameCandidate!!.name,
            onConfirm = { newName ->
                viewModel.confirmRename(newName, context)
            },
            onDismiss = { viewModel.dismissRename() }
        )
    }

    Scaffold(
        topBar = {
            SakuPDFTopAppBar(
                title = "Detail File",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
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

            // Large Preview Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp)),
                color = SurfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (doc.isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pratinjau Dokumen",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Text(
                text = doc.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = doc.status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Metadata Items
            MetadataItem("Jenis", if (doc.isPdf) "Dokumen PDF" else "Gambar JPG", Icons.Default.Description)
            MetadataItem("Ukuran", doc.sizeFormatted, Icons.Default.SdCard)
            MetadataItem("Halaman", "${doc.pages} Halaman", Icons.Default.AutoStories)
            MetadataItem("Tanggal Dibuat", doc.dateFormatted, Icons.Default.CalendarToday)
            MetadataItem("Lokasi", doc.location, Icons.Default.Folder)

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton("Buka", Icons.AutoMirrored.Filled.OpenInNew) {
                    val uri = doc.uri
                    if (uri != null) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, if (doc.isPdf) "application/pdf" else "image/*")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            viewModel.setErrorMessage("Tidak ada aplikasi yang dapat membuka file ini.")
                        }
                    } else {
                        viewModel.setErrorMessage("File ini adalah file contoh atau lokasi tidak ditemukan.")
                    }
                }
                ActionButton("Bagikan", Icons.Default.Share) {
                    val uri = doc.uri
                    if (uri != null) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = if (doc.isPdf) "application/pdf" else "image/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Bagikan Berkas"))
                        } catch (_: Exception) {
                            viewModel.setErrorMessage("Gagal membagikan berkas.")
                        }
                    } else {
                        viewModel.setErrorMessage("File ini adalah file contoh atau lokasi tidak ditemukan.")
                    }
                }
                ActionButton("Ubah nama", Icons.Default.Edit) {
                    viewModel.requestRename(doc)
                }
                ActionButton(
                    "Hapus",
                    Icons.Default.Delete,
                    isDestructive = true,
                    onClick = { viewModel.requestDelete(doc) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetadataItem(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isDestructive) MaterialTheme.colorScheme.errorContainer
                    else PrimaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
