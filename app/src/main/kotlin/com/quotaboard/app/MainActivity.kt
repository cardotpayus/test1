package com.quotaboard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.quotaboard.app.ui.AppViewModel
import com.quotaboard.app.ui.screens.AddAccountScreen
import com.quotaboard.app.ui.screens.DetailScreen
import com.quotaboard.app.ui.screens.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as QuotaApp
        setContent {
            com.quotaboard.app.ui.QuotaBoardTheme {
                Surface {
                    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory(app))
                    AppNavHost(vm)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(vm: AppViewModel) {
    val nav = rememberNavController()
    val accounts by vm.accounts.collectAsState()

    NavHost(
        navController = nav,
        startDestination = "home",
    ) {
        composable("home") {
            HomeScreen(
                vm = vm,
                onAddAccount = { nav.navigate("add") },
                onOpenDetail = { nav.navigate("detail/$it") },
            )
        }
        composable("add") {
            AddAccountScreen(
                vm = vm,
                onDone = { nav.popBackStack() },
                onBack = { nav.popBackStack() },
            )
        }
        composable("detail/{accountId}") { entry ->
            val id = entry.arguments?.getString("accountId").orEmpty()
            if (accounts.none { it.id == id }) {
                androidx.compose.runtime.LaunchedEffect(id) { nav.popBackStack() }
            } else {
                DetailScreen(vm = vm, accountId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
