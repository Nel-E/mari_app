package com.mari.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mari.app.ui.screens.add.AddTaskScreen
import com.mari.app.ui.screens.main.MainScreen
import com.mari.app.ui.screens.settings.SettingsScreen
import com.mari.app.ui.screens.tasks.AllTasksScreen

object MariRoutes {
    const val MAIN = "main"
    const val TASKS = "tasks"
    const val ADD = "add"
    const val SETTINGS = "settings"
}

@Composable
fun MariNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = MariRoutes.MAIN,
    ) {
        composable(MariRoutes.MAIN) {
            MainScreen(
                onNavigateToTasks = { navController.navigate(MariRoutes.TASKS) },
                onNavigateToAdd = { navController.navigate(MariRoutes.ADD) },
                onNavigateToSettings = { navController.navigate(MariRoutes.SETTINGS) },
            )
        }
        composable(MariRoutes.TASKS) {
            AllTasksScreen(
                onNavigateUp = { navController.navigateUp() },
                onNavigateToAdd = { navController.navigate(MariRoutes.ADD) },
            )
        }
        composable(MariRoutes.ADD) {
            AddTaskScreen(
                onNavigateUp = { navController.navigateUp() },
            )
        }
        composable(MariRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateUp = { navController.navigateUp() },
            )
        }
    }
}
