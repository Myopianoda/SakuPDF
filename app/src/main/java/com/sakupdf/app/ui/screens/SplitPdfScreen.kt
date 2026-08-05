package com.sakupdf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
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
import androidx.compose.ui.unit.dp
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
                        onClick = onStartProcess,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.CallSplit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pisahkan PDF")
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                        Text("Laporan_Tahunan_2023_Final.pdf", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("24 Halaman • 2.4 MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            extraContent()
        }
    }
}
