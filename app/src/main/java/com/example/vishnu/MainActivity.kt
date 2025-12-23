package com.example.vishnu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.vishnu.screens.AuthScreen
import com.example.vishnu.screens.CartScreen
import com.example.vishnu.screens.CatalogScreen
import com.example.vishnu.screens.ProductDetailScreen
import com.example.vishnu.screens.ProfileScreen
import com.example.vishnu.ui.theme.VishnuTheme
import com.example.vishnu.viewModels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VishnuCrockeryApp()
        }
    }
}

@Composable
fun VishnuCrockeryApp(
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val startDestination by viewModel.startDestination.collectAsState()

    if(startDestination == null){
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator() // Or your App Logo
        }
    }else {
        NavHost(navController = navController, startDestination = startDestination!!) {

            composable("auth_screen") {
                AuthScreen(
                    onAuthSuccess = {
                        // When login succeeds, pop Auth and go to Home
                        navController.navigate("catalog") {
                            popUpTo("auth_screen") { inclusive = true }
                        }
                    }
                )
            }
            // Screen 1: Catalog
            composable("catalog") {
                Scaffold(
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { navController.navigate("cart") },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Go to Cart")
                        }
                    }
                ) { padding ->
                    // Pass padding to CatalogScreen or handle it
                    Box(modifier = Modifier.padding(padding)) {
                        CatalogScreen(
                            onProductClick = { productId ->
                                navController.navigate("detail/$productId")
                            },
                            onProfileClick = {
                                navController.navigate("profile")
                            }
                        )
                    }
                }
                // Note: You need to update your CatalogScreen to accept a click callback!
                // See the instruction below 👇
            }

            // Screen 2: Detail
            composable(
                "detail/{productId}",
                arguments = listOf(navArgument("productId") { type = NavType.StringType })
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getString("productId") ?: "1"
                ProductDetailScreen(
                    productId = productId,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("cart") {
                CartScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("profile") {
                ProfileScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VishnuTheme {

    }
}