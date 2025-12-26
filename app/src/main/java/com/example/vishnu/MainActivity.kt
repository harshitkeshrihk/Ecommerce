package com.example.vishnu

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import com.example.vishnu.screens.AddEditProductScreen
import com.example.vishnu.screens.AdminDashboardScreen
import com.example.vishnu.screens.AuthScreen
import com.example.vishnu.screens.CartScreen
import com.example.vishnu.screens.CatalogScreen
import com.example.vishnu.screens.ProductDetailScreen
import com.example.vishnu.screens.ProfileScreen
import com.example.vishnu.ui.theme.VishnuTheme
import com.example.vishnu.utils.DataStoreManager
import com.example.vishnu.viewModels.AuthViewModel
import com.example.vishnu.viewModels.CartViewModel
import com.example.vishnu.viewModels.Constants.ADMIN_EMAILS
import com.example.vishnu.viewModels.MainViewModel
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private val cartViewModel: CartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Checkout.preload(applicationContext)

        enableEdgeToEdge()
        setContent {
            VishnuCrockeryApp(
                onInitiatePayment = { amount, email, phone ->
                    startPayment(amount, email, phone)
                }
            )
        }
    }
    private fun startPayment(amount: Double, email: String, phone: String) {
        // Real Razorpay Code
        val checkout = Checkout()
        checkout.setKeyID("rzp_test_RvAYVqnRum5bKG") // Replace this!
        try {
            val options = JSONObject()
            options.put("name", "Vishnu Crockery")
            options.put("description", "Payment for Order")
            options.put("currency", "INR")
            options.put("amount", (amount * 100).toInt()) // Paise
            options.put("prefill.email", email)
            options.put("prefill.contact", phone)
            checkout.open(this, options)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPaymentSuccess(razorpayPaymentID: String?, paymentData: PaymentData?) {
        Toast.makeText(this, "Payment Successful!", Toast.LENGTH_SHORT).show()
        if (razorpayPaymentID != null) {
            cartViewModel.onPaymentSuccess(razorpayPaymentID)
        }
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        Toast.makeText(this, "Payment Failed: $response", Toast.LENGTH_SHORT).show()
    }

}

@Composable
fun VishnuCrockeryApp(
    viewModel: MainViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    onInitiatePayment: (amount: Double, email: String, phone: String) -> Unit
) {
    val navController = rememberNavController()
    val startDestination by viewModel.startDestination.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()

    if(startDestination == null){
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator() // Or your App Logo
        }
    }else {
        NavHost(navController = navController, startDestination = startDestination!!) {
            composable("auth_screen") {
                AuthScreen(
                    onAuthSuccess = {
                       if(isAdmin){
                            navController.navigate("admin_dashboard") {
                                popUpTo("auth_screen") { inclusive = true }
                            }
                        } else {
                            navController.navigate("catalog") {
                                popUpTo("auth_screen") { inclusive = true }
                            }
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
                    onBackClick = { navController.popBackStack()},
                    onEditClick = {
                        navController.navigate("add_edit_product?productId=${productId}")
                    }
                )
            }

            composable("cart") {
                CartScreen(
                    onBackClick = { navController.popBackStack() },
                    onInitiatePayment = onInitiatePayment
                )
            }

            composable("profile") {
                ProfileScreen(
                    onLogoutClick = {
                        authViewModel.onSignOut()
                        navController.navigate("auth_screen") {
                            // "0" means the root of the graph. This clears AdminDashboard, Catalog, EVERYTHING.
                            popUpTo(0) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable("admin_dashboard") {
                AdminDashboardScreen(
                    onAddProductClick = {
                        navController.navigate("add_edit_product")
                    },
                    onGoToStoreClick = {
                        navController.navigate("catalog")
                    }
                )
            }

            composable(
                route = "add_edit_product?productId={productId}",
                arguments = listOf(navArgument("productId") { nullable = true })
            ) {
                AddEditProductScreen(
                    productId = it.arguments?.getString("productId"),
                    onBack = { navController.popBackStack() }
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