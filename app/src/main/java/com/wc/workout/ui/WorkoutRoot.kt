package com.wc.workout.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.SportsGymnastics
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wc.workout.AppContainer
import com.wc.workout.ui.calendar.CalendarScreen
import com.wc.workout.ui.home.HomeScreen
import com.wc.workout.ui.library.ExerciseHistoryScreen
import com.wc.workout.ui.library.ExerciseLibraryScreen
import com.wc.workout.ui.trend.TrendScreen
import com.wc.workout.ui.workout.WorkoutSessionScreen

private data class BottomTab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun WorkoutRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val tabs = listOf(
        BottomTab("home", "训练") { Icon(Icons.Filled.FitnessCenter, contentDescription = null) },
        BottomTab("calendar", "日历") { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
        BottomTab("library", "动作库") { Icon(Icons.Outlined.SportsGymnastics, contentDescription = null) },
        BottomTab("trend", "趋势") { Icon(Icons.Filled.ShowChart, contentDescription = null) },
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = tab.icon,
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(container, onStartWorkout = { id -> navController.navigate("workout/$id") })
            }
            composable("calendar") { CalendarScreen(container, onOpenSession = { navController.navigate("workout/$it") }) }
            composable("library") {
                ExerciseLibraryScreen(container, onOpenExercise = { navController.navigate("exercise/$it") })
            }
            composable("trend") { TrendScreen(container) }
            composable(
                route = "workout/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { entry ->
                val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
                WorkoutSessionScreen(container, sessionId, onFinished = { navController.popBackStack() })
            }
            composable(
                route = "exercise/{exerciseId}",
                arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
            ) { entry ->
                val exerciseId = entry.arguments?.getLong("exerciseId") ?: 0L
                ExerciseHistoryScreen(container, exerciseId)
            }
        }
    }
}
