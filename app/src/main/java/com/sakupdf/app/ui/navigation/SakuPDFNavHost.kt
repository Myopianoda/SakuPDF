package com.sakupdf.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sakupdf.app.ui.screens.*
import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel

object Routes {
    const val HOME = "home"
    const val FILES = "files"
    const val FILE_DETAIL = "file_detail"
    const val ORGANIZE_IMAGES = "organize_images"
    const val PDF_SETTINGS = "pdf_settings"
    const val MERGE_PDF = "merge_pdf"
    const val SPLIT_PDF = "split_pdf"
    const val COMPRESS_PDF = "compress_pdf"
    const val PDF_TO_IMAGE = "pdf_to_image"
    const val PROCESSING = "processing"
    const val RESULT_SUCCESS = "result_success"
    const val SETTINGS = "settings"
}

@Composable
fun SakuPDFNavHost(
    navController: NavHostController,
    viewModel: SakuPDFViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToRoute = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.FILES) {
            FileListScreen(
                viewModel = viewModel,
                onNavigateToDetail = { doc ->
                    viewModel.selectDocument(doc)
                    navController.navigate(Routes.FILE_DETAIL)
                },
                onNavigateToRoute = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.FILE_DETAIL) {
            FileDetailScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onDelete = { navController.popBackStack() }
            )
        }
        composable(Routes.ORGANIZE_IMAGES) {
            OrganizeImagesScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Routes.PDF_SETTINGS) }
            )
        }
        composable(Routes.PDF_SETTINGS) {
            PdfSettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onStartProcess = {
                    viewModel.startProcessing("Membuat PDF") {
                        navController.navigate(Routes.RESULT_SUCCESS) {
                            popUpTo(Routes.HOME)
                        }
                    }
                    navController.navigate(Routes.PROCESSING)
                }
            )
        }
        composable(Routes.MERGE_PDF) {
            MergePdfScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onStartProcess = {
                    viewModel.startProcessing("Menggabungkan PDF") {
                        navController.navigate(Routes.RESULT_SUCCESS) {
                            popUpTo(Routes.HOME)
                        }
                    }
                    navController.navigate(Routes.PROCESSING)
                }
            )
        }
        composable(Routes.SPLIT_PDF) {
            SplitPdfScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onStartProcess = {
                    viewModel.startProcessing("Memisahkan PDF") {
                        navController.navigate(Routes.RESULT_SUCCESS) {
                            popUpTo(Routes.HOME)
                        }
                    }
                    navController.navigate(Routes.PROCESSING)
                }
            )
        }
        composable(Routes.COMPRESS_PDF) {
            CompressPdfScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onStartProcess = {
                    viewModel.startProcessing("Mengompres PDF") {
                        navController.navigate(Routes.RESULT_SUCCESS) {
                            popUpTo(Routes.HOME)
                        }
                    }
                    navController.navigate(Routes.PROCESSING)
                }
            )
        }
        composable(Routes.PDF_TO_IMAGE) {
            PdfToImageScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onStartProcess = {
                    viewModel.startProcessing("Mengubah PDF ke Gambar") {
                        navController.navigate(Routes.RESULT_SUCCESS) {
                            popUpTo(Routes.HOME)
                        }
                    }
                    navController.navigate(Routes.PROCESSING)
                }
            )
        }
        composable(Routes.PROCESSING) {
            ProcessingScreen(
                viewModel = viewModel,
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Routes.RESULT_SUCCESS) {
            ResultSuccessScreen(
                viewModel = viewModel,
                onBackToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRoute = { route -> navController.navigate(route) }
            )
        }
    }
}
