package com.sakupdf.app

import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SakuPDFViewModelTest {

    @Test
    fun testViewModelStateTransitionFromProcessingToSuccess() {
        val viewModel = SakuPDFViewModel()

        // 1. Initial State
        val initialState = viewModel.uiState.value
        assertFalse(initialState.isProcessing)
        assertFalse(initialState.shouldNavigateToSuccess)

        // 2. Handle navigation state
        viewModel.onNavigationToSuccessHandled()
        val handledState = viewModel.uiState.value
        assertFalse(handledState.shouldNavigateToSuccess)
    }

    @Test
    fun testClearImageToPdfStateResetsNavigation() {
        val viewModel = SakuPDFViewModel()

        viewModel.clearImageToPdfState()
        val resetState = viewModel.uiState.value
        assertFalse(resetState.shouldNavigateToSuccess)
        assertNotNull(resetState.pdfSettings.filename)
        assertTrue(resetState.imagesToConvert.isEmpty())
    }
}
