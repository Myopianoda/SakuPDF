package com.sakupdf.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sakupdf.app.model.PdfDocument
import com.sakupdf.app.ui.components.ConfirmDeleteDialog
import com.sakupdf.app.ui.components.DocumentCard
import com.sakupdf.app.ui.components.EmptyState
import com.sakupdf.app.ui.components.SakuPDFBottomNavBar
import com.sakupdf.app.ui.components.SakuPDFTopAppBar
import com.sakupdf.app.ui.navigation.Routes
import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel

@Composable
fun FileListScreen(
    viewModel: SakuPDFViewModel,
    onNavigateToDetail: (PdfDocument) -> Unit,
    onNavigateToRoute: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var isSearchActive by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val categoryDocs = when (uiState.selectedFilter) {
        "PDF" -> uiState.documents.filter { it.isPdf }
        "Gambar" -> uiState.documents.filter { !it.isPdf }
        else -> uiState.documents
    }

    val searchedDocs = if (uiState.searchQuery.isBlank()) {
        categoryDocs
    } else {
        categoryDocs.filter { it.name.contains(uiState.searchQuery, ignoreCase = true) }
    }

    val filteredDocs = when (uiState.sortOrder) {
        "NAME_ASC" -> searchedDocs.sortedBy { it.name.lowercase() }
        "DATE_ASC" -> searchedDocs.reversed()
        else -> searchedDocs
    }

    if (uiState.deleteCandidate != null) {
        ConfirmDeleteDialog(
            fileName = uiState.deleteCandidate!!.name,
            onConfirm = { viewModel.confirmDelete(context) },
            onDismiss = { viewModel.dismissDelete() }
        )
    }

    if (uiState.renameCandidate != null) {
        com.sakupdf.app.ui.components.RenameDocumentDialog(
            initialName = uiState.renameCandidate!!.name,
            onConfirm = { newName -> viewModel.confirmRename(newName, context) },
            onDismiss = { viewModel.dismissRename() }
        )
    }

    Scaffold(
        topBar = {
            SakuPDFTopAppBar(
                title = "Berkas",
                actions = {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) viewModel.setSearchQuery("")
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Cari")
                    }
                    IconButton(onClick = { viewModel.toggleSortOrder() }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Urutkan")
                    }
                }
            )
        },
        bottomBar = {
            SakuPDFBottomNavBar(
                currentRoute = Routes.FILES,
                onNavigate = onNavigateToRoute
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isSearchActive) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Cari nama berkas...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true,
                    shape = CircleShape
                )
            }

            // Filter Chips Row (with horizontal scroll for narrow screens)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Semua", "PDF", "Gambar").forEach { filter ->
                    FilterChip(
                        selected = uiState.selectedFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter) },
                        shape = CircleShape
                    )
                }
            }

            if (filteredDocs.isEmpty()) {
                EmptyState(
                    title = "Belum ada berkas",
                    description = "File PDF atau gambar yang Anda simpan akan muncul di sini.",
                    actionLabel = "Kembali ke Beranda",
                    onAction = { onNavigateToRoute(Routes.HOME) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDocs) { doc ->
                        DocumentCard(
                            document = doc,
                            onClick = { onNavigateToDetail(doc) },
                            onMenuClick = { viewModel.requestDelete(doc) }
                        )
                    }
                }
            }
        }
    }
}
