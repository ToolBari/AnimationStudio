package com.din.htmltomp4.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.din.htmltomp4.ui.screens.ConfigScreen
import com.din.htmltomp4.ui.screens.DashboardScreen
import com.din.htmltomp4.ui.screens.HistoryScreen
import com.din.htmltomp4.ui.screens.PreviewScreen
import com.din.htmltomp4.ui.screens.ProcessingScreen
import com.din.htmltomp4.ui.screens.VideoToHtmlConfigScreen
import com.din.htmltomp4.ui.screens.VideoToHtmlProcessingScreen
import com.din.htmltomp4.ui.screens.VideoToHtmlResultScreen
import com.din.htmltomp4.viewmodel.ConverterViewModel
import com.din.htmltomp4.viewmodel.HistoryViewModel
import com.din.htmltomp4.viewmodel.VideoToHtmlViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val CONFIG = "config"
    const val PROCESSING = "processing"
    const val PREVIEW = "preview"
    const val VIDEO_CONFIG = "video_config"
    const val VIDEO_PROCESSING = "video_processing"
    const val VIDEO_RESULT = "video_result"
    const val HISTORY = "history"
}

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    // Single shared ViewModel instance per direction across the whole flow,
    // scoped to the Activity via the default viewModelStoreOwner from
    // NavHost's context. HistoryViewModel is shared across BOTH directions
    // so either one's output shows up in the same Recent Conversions list.
    val viewModel: ConverterViewModel = viewModel()
    val videoToHtmlViewModel: VideoToHtmlViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = viewModel,
                videoToHtmlViewModel = videoToHtmlViewModel,
                historyViewModel = historyViewModel,
                onHtmlPicked = { navController.navigate(Routes.CONFIG) },
                onVideoPicked = { navController.navigate(Routes.VIDEO_CONFIG) },
                onSeeAllHistory = { navController.navigate(Routes.HISTORY) }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                historyViewModel = historyViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CONFIG) {
            ConfigScreen(
                viewModel = viewModel,
                onStartConversion = { navController.navigate(Routes.PROCESSING) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PROCESSING) {
            ProcessingScreen(
                viewModel = viewModel,
                historyViewModel = historyViewModel,
                onFinished = {
                    navController.navigate(Routes.PREVIEW) {
                        popUpTo(Routes.DASHBOARD)
                    }
                },
                onCancelled = { navController.popBackStack(Routes.DASHBOARD, inclusive = false) }
            )
        }

        composable(Routes.PREVIEW) {
            PreviewScreen(
                viewModel = viewModel,
                onDone = {
                    viewModel.resetState()
                    navController.popBackStack(Routes.DASHBOARD, inclusive = false)
                }
            )
        }

        composable(Routes.VIDEO_CONFIG) {
            VideoToHtmlConfigScreen(
                viewModel = videoToHtmlViewModel,
                onStartConversion = { navController.navigate(Routes.VIDEO_PROCESSING) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.VIDEO_PROCESSING) {
            VideoToHtmlProcessingScreen(
                viewModel = videoToHtmlViewModel,
                historyViewModel = historyViewModel,
                onFinished = {
                    navController.navigate(Routes.VIDEO_RESULT) {
                        popUpTo(Routes.DASHBOARD)
                    }
                },
                onCancelled = { navController.popBackStack(Routes.DASHBOARD, inclusive = false) }
            )
        }

        composable(Routes.VIDEO_RESULT) {
            VideoToHtmlResultScreen(
                viewModel = videoToHtmlViewModel,
                onDone = {
                    videoToHtmlViewModel.resetState()
                    navController.popBackStack(Routes.DASHBOARD, inclusive = false)
                }
            )
        }
    }
}
