package com.pdg.braceletconnecte.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pdg.braceletconnecte.BraceletConnecteApplication
import com.pdg.braceletconnecte.data.auth.AuthState
import com.pdg.braceletconnecte.presentation.dashboard.DashboardScreen
import com.pdg.braceletconnecte.presentation.login.LoginScreen
import com.pdg.braceletconnecte.presentation.register.RegisterScreen
import com.pdg.braceletconnecte.presentation.stats.StatsScreen

object AppRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val STATS = "stats"
}

@Composable
fun AppNavHost(application: BraceletConnecteApplication) {
    val authState by application.authRepository.authState.collectAsState()

    if (authState is AuthState.Loading) {
        SplashScreen()
        return
    }

    val navController = rememberNavController()
    val startDestination = if (authState is AuthState.LoggedIn) AppRoutes.DASHBOARD else AppRoutes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppRoutes.LOGIN) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(AppRoutes.REGISTER) },
                onLoggedIn = {
                    navController.navigate(AppRoutes.DASHBOARD) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(AppRoutes.REGISTER) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegistered = {
                    navController.navigate(AppRoutes.DASHBOARD) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(AppRoutes.DASHBOARD) {
            DashboardScreen(onNavigateToStats = { navController.navigate(AppRoutes.STATS) })
        }
        composable(AppRoutes.STATS) {
            StatsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }

    // A 401 anywhere clears the session; react by bouncing back to Login from any screen.
    LaunchedEffect(authState) {
        val currentRoute = navController.currentDestination?.route
        if (authState is AuthState.LoggedOut && currentRoute != AppRoutes.LOGIN && currentRoute != AppRoutes.REGISTER) {
            navController.navigate(AppRoutes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Bracelet Connecté", style = MaterialTheme.typography.titleMedium)
    }
}
