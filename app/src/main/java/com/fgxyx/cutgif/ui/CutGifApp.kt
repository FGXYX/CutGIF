package com.fgxyx.cutgif.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fgxyx.cutgif.ui.editor.EditorScreen
import com.fgxyx.cutgif.ui.editor.EditorViewModel
import com.fgxyx.cutgif.ui.home.HomeScreen
import com.fgxyx.cutgif.ui.split.GifSplitScreen
import com.fgxyx.cutgif.ui.split.GifSplitViewModel

private const val RouteHome = "home"
private const val RouteEditor = "editor"
private const val RouteGifSplit = "gif_split"

@Composable
fun CutGifApp(
    editorViewModel: EditorViewModel = viewModel(),
    gifSplitViewModel: GifSplitViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val enterSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val exitSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    NavHost(
        navController = navController,
        startDestination = RouteHome,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = enterSpring,
            ) + fadeIn(animationSpec = tween(220))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 5 },
                animationSpec = tween(240),
            ) + fadeOut(animationSpec = tween(180))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 5 },
                animationSpec = tween(260),
            ) + fadeIn(animationSpec = tween(220))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = exitSpring,
            ) + fadeOut(animationSpec = tween(160))
        },
    ) {
        composable(RouteHome) {
            HomeScreen(
                onVideoPicked = { uri ->
                    editorViewModel.loadVideo(uri)
                    navController.navigate(RouteEditor) {
                        launchSingleTop = true
                    }
                },
                onGifPicked = { uri ->
                    gifSplitViewModel.loadGif(uri)
                    navController.navigate(RouteGifSplit) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(RouteEditor) {
            EditorScreen(
                viewModel = editorViewModel,
                onExit = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable(RouteGifSplit) {
            GifSplitScreen(
                viewModel = gifSplitViewModel,
                onExit = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}
