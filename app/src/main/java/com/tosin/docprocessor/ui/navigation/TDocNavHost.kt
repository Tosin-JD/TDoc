package com.tosin.docprocessor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tosin.docprocessor.ui.editor.EditorScreen
import com.tosin.docprocessor.ui.editor.OpenMode
import com.tosin.docprocessor.ui.home.HomeNavigation
import com.tosin.docprocessor.ui.home.HomeScreen

@Composable
fun TDocNavHost(
    initialOpenRequest: OpenRequest?,
    onOpenRequestHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    val startDestination = remember(initialOpenRequest) {
        initialOpenRequest?.toEditorRoute() ?: TDocRoutes.HOME
    }
    var lastRequest by remember { mutableStateOf(initialOpenRequest) }

    LaunchedEffect(initialOpenRequest) {
        if (initialOpenRequest != null && initialOpenRequest !== lastRequest) {
            navController.navigate(initialOpenRequest.toEditorRoute())
            onOpenRequestHandled()
            lastRequest = initialOpenRequest
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(TDocRoutes.HOME) {
            HomeScreen(
                onOpenDocument = { navigation: HomeNavigation ->
                    navController.navigate(
                        TDocRoutes.editorRoute(
                            openMode = navigation.openMode.name,
                            uri = navigation.uri,
                            mime = navigation.mime,
                            saveAsName = navigation.saveAsName
                        )
                    )
                }
            )
        }
        composable(
            route = TDocRoutes.EDITOR +
                "?openMode={openMode}&uri={uri}&mime={mime}&saveAsName={saveAsName}",
            arguments = listOf(
                navArgument(TDocRoutes.ARG_OPEN_MODE) {
                    type = NavType.StringType
                    defaultValue = OpenMode.VIEW.name
                },
                navArgument(TDocRoutes.ARG_URI) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(TDocRoutes.ARG_MIME) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(TDocRoutes.ARG_SAVE_AS_NAME) {
                    type = NavType.StringType
                    defaultValue = "Untitled.docx"
                }
            )
        ) {
            EditorScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}