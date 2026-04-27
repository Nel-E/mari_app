package com.mari.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mari.app.ui.screens.add.AddTaskScreen
import com.mari.app.ui.screens.main.MainScreen
import com.mari.app.ui.screens.settings.SettingsScreen
import com.mari.app.ui.screens.tasks.AllTasksScreen
import com.mari.app.ui.screens.tasks.EditTaskScreen
import com.mari.app.ui.screens.update.UpdateAvailableScreen

object MariRoutes {
    const val MAIN = "main"
    const val TASKS = "tasks"
    const val ADD = "add"
    const val SETTINGS = "settings"
    const val UPDATE = "update"
    const val EDIT_TASK = "tasks/edit/{taskId}"
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
                onNavigateToEdit = { taskId -> navController.navigate("tasks/edit/$taskId") },
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
                onNavigateToUpdate = { navController.navigate(MariRoutes.UPDATE) },
            )
        }
        composable(MariRoutes.UPDATE) {
            UpdateAvailableScreen(
                onNavigateUp = { navController.navigateUp() },
            )
        }
        composable(
            route = MariRoutes.EDIT_TASK,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) {
            EditTaskScreen(
                onNavigateUp = { navController.navigateUp() },
            )
        }
    }
}
