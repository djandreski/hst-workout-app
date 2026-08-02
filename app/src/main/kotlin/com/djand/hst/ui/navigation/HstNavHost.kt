package com.djand.hst.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.djand.hst.ui.history.HistoryScreen
import com.djand.hst.ui.home.HomeScreen
import com.djand.hst.ui.settings.SettingsScreen
import com.djand.hst.ui.stats.StatsScreen
import com.djand.hst.ui.workout.WorkoutScreen

/** Routes of the app. Intentionally flat: Home plus four screens, no nested graphs. */
object Routes {
    const val HOME = "home"
    const val WORKOUT = "workout/{sessionId}"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    fun workout(sessionId: Long) = "workout/$sessionId"
}

/**
 * The single NavHost. Home is the hub; everything else is reached from it and
 * returns with the back stack. No bottom navigation, no drawer, no rail.
 */
@Composable
fun HstNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartWorkout = { sessionId -> navController.navigate(Routes.workout(sessionId)) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenStats = { navController.navigate(Routes.STATS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.WORKOUT,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) {
            WorkoutScreen(onFinished = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.STATS) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
