package com.sakupdf.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakupdf.app.ui.components.DocumentCard
import com.sakupdf.app.ui.components.SakuPDFBottomNavBar
import com.sakupdf.app.ui.navigation.Routes
import com.sakupdf.app.ui.theme.PrimaryContainer
import com.sakupdf.app.ui.theme.SurfaceContainerLowest
import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel

data class ToolGridItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: SakuPDFViewModel,
    onNavigateToRoute: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Photo Picker launcher (max 30 images)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImagesFromUris(context.contentResolver, uris)
            onNavigateToRoute(Routes.ORGANIZE_IMAGES)
        }
    }

    // Document Picker launcher for Merge PDF
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPdfsToMerge(context.contentResolver, uris)
            onNavigateToRoute(Routes.MERGE_PDF)
        }
    }

    // Document Picker launcher for Split PDF
    val splitPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setSplitSourcePdf(context.contentResolver, uri)
            onNavigateToRoute(Routes.SPLIT_PDF)
        }
    }

    // Document Picker launcher for PDF to Image
    val pdfToImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setPdfToImageSourcePdf(context.contentResolver, uri)
            onNavigateToRoute(Routes.PDF_TO_IMAGE)
        }
    }

    // Document Picker launcher for Compress PDF
    val compressPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setCompressSourcePdf(context.contentResolver, uri)
            onNavigateToRoute(Routes.COMPRESS_PDF)
        }
    }

    val tools = listOf(
        ToolGridItem("Gambar ke PDF", "Gabungkan beberapa gambar menjadi satu PDF", Icons.Default.Collections, Routes.ORGANIZE_IMAGES),
        ToolGridItem("Gabungkan PDF", "Satukan beberapa file PDF", Icons.AutoMirrored.Filled.CallMerge, Routes.MERGE_PDF),
        ToolGridItem("Pisahkan PDF", "Pisahkan halaman PDF", Icons.AutoMirrored.Filled.CallSplit, Routes.SPLIT_PDF),
        ToolGridItem("Kompres PDF", "Kurangi ukuran file PDF", Icons.Default.Compress, Routes.COMPRESS_PDF),
        ToolGridItem("PDF ke Gambar", "Ubah halaman PDF menjadi JPG atau PNG", Icons.Default.PictureAsPdf, Routes.PDF_TO_IMAGE)
    )

    Scaffold(
        bottomBar = {
            SakuPDFBottomNavBar(
                currentRoute = Routes.HOME,
                onNavigate = onNavigateToRoute
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Gambar")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SakuPDF",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = CircleShape,
                        color = PrimaryContainer,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Privat & offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Kelola PDF dengan mudah",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Cepat, praktis, dan diproses langsung di perangkat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Text(
                    text = "Alat PDF",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // PDF Tools Cards Grid
            items(tools) { tool ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            when (tool.route) {
                                Routes.ORGANIZE_IMAGES -> {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                Routes.MERGE_PDF -> {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                }
                                Routes.SPLIT_PDF -> {
                                    splitPdfPickerLauncher.launch(arrayOf("application/pdf"))
                                }
                                Routes.COMPRESS_PDF -> {
                                    compressPdfPickerLauncher.launch(arrayOf("application/pdf"))
                                }
                                Routes.PDF_TO_IMAGE -> {
                                    pdfToImagePickerLauncher.launch(arrayOf("application/pdf"))
                                }
                                else -> {
                                    onNavigateToRoute(tool.route)
                                }
                            }
                        },
                    color = SurfaceContainerLowest,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "File terbaru",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { onNavigateToRoute(Routes.FILES) }) {
                        Text("Lihat semua")
                    }
                }
            }

            // Recent files
            items(uiState.documents.take(3)) { doc ->
                DocumentCard(
                    document = doc,
                    onClick = {
                        viewModel.selectDocument(doc)
                        onNavigateToRoute(Routes.FILE_DETAIL)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
