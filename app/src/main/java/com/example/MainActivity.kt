package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.ConversionProgressDialog
import com.example.ui.screens.CompressPdfScreen
import com.example.ui.screens.DeletePagesScreen
import com.example.ui.screens.DocumentScanScreen
import com.example.ui.screens.ExtractTextScreen
import com.example.ui.screens.GrayscaleInvertScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImagesToPdfScreen
import com.example.ui.screens.MergePdfScreen
import com.example.ui.screens.PageNumbersScreen
import com.example.ui.screens.PdfToImagesScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.ReorderPagesScreen
import com.example.ui.screens.RotatePdfScreen
import com.example.ui.screens.SignPdfScreen
import com.example.ui.screens.SplitPdfScreen
import com.example.ui.screens.StampPdfScreen
import com.example.ui.screens.TextToPdfScreen
import com.example.ui.screens.WatermarkPdfScreen
import com.example.ui.screens.WebHtmlToPdfScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PdfConverterApp()
            }
        }
    }
}

@Composable
fun PdfConverterApp(
    viewModel: PdfViewModel = viewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Handle back button to return to Home screen when on any sub-screen
    BackHandler(enabled = currentScreen != AppScreen.Home) {
        viewModel.navigateTo(AppScreen.Home)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        when (val screen = currentScreen) {
            is AppScreen.Home -> {
                HomeScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.ImagesToPdf -> {
                ImagesToPdfScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.TextToPdf -> {
                TextToPdfScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.PdfToImages -> {
                PdfToImagesScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.MergePdf -> {
                MergePdfScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.SplitPdf -> {
                SplitPdfScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.WatermarkPdf -> {
                WatermarkPdfScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.CompressPdf -> {
                CompressPdfScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.RotatePdf -> {
                RotatePdfScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.GrayscaleInvertPdf -> {
                GrayscaleInvertScreen(
                    initialMode = screen.initialMode,
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.PageNumbersPdf -> {
                PageNumbersScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.DeletePagesPdf -> {
                DeletePagesScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.ReorderPagesPdf -> {
                ReorderPagesScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.SignPdf -> {
                SignPdfScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.StampPdf -> {
                StampPdfScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.DocumentScan -> {
                DocumentScanScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.WebHtmlToPdf -> {
                WebHtmlToPdfScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.ExtractText -> {
                ExtractTextScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.Viewer -> {
                PdfViewerScreen(
                    file = screen.file,
                    title = screen.title,
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is AppScreen.History -> {
                HistoryScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        // Global progress dialog for operations
        ConversionProgressDialog(progress = progress)
    }
}
