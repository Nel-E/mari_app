package com.mari.wear.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import com.mari.wear.ui.screens.add.AddTaskScreen
import com.mari.wear.ui.screens.main.MainScreen
import com.mari.wear.ui.screens.settings.SettingsScreen
import com.mari.wear.ui.screens.tasks.AllTasksScreen

object WearRoute {
    const val MAIN = "main"
    const val TASKS = "tasks"
    const val ADD = "add"
    const val SETTINGS = "settings"
}

@Composable
fun WearNavGraph(navController: NavHostController) {
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearRoute.MAIN,
    ) {
        composable(WearRoute.MAIN) { MainScreen(navController) }
        composable(WearRoute.TASKS) { AllTasksScreen(navController) }
        composable(WearRoute.ADD) { AddTaskScreen(navController) }
        composable(WearRoute.SETTINGS) { SettingsScreen(navController) }
    }
}
