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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.vishnu.screens.AddEditProductScreen
import com.example.vishnu.screens.AdminCapacitySettingsScreen
import com.example.vishnu.screens.AdminDashboardScreen
import com.example.vishnu.screens.AdminProductionCalendarScreen
import com.example.vishnu.screens.AdminGiftPackEditScreen
import com.example.vishnu.screens.AdminGiftPacksScreen
import com.example.vishnu.screens.AdminGiftingOrdersScreen
import com.example.vishnu.screens.GiftPackBuilderScreen
import com.example.vishnu.screens.GiftingHomeScreen
import com.example.vishnu.screens.MyGiftingOrdersScreen
import com.example.vishnu.screens.AdminQuotePipelineScreen
import com.example.vishnu.screens.AuthScreen
import com.example.vishnu.screens.CartScreen
import com.example.vishnu.screens.CatalogScreen
import com.example.vishnu.screens.KycQueueScreen
import com.example.vishnu.screens.ProductDetailScreen
import com.example.vishnu.screens.QuickOrderPadScreen
import com.example.vishnu.screens.RfqScreen
import com.example.vishnu.screens.WholesaleHomeScreen
import com.example.vishnu.model.UserRole
import com.example.vishnu.screens.ProfileScreen
import com.example.vishnu.ui.theme.VishnuTheme
import com.example.vishnu.utils.DataStoreManager
import com.example.vishnu.utils.PaymentPurpose
import com.example.vishnu.utils.PaymentResult
import com.example.vishnu.utils.PaymentRouter
import javax.inject.Inject
import com.example.vishnu.viewModels.AuthViewModel
import com.example.vishnu.viewModels.CartViewModel
import com.example.vishnu.viewModels.MainViewModel
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private val cartViewModel: CartViewModel by viewModels()

    @Inject lateinit var paymentRouter: PaymentRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Checkout.preload(applicationContext)

//        enableEdgeToEdge()
        setContent {
            VishnuTheme(dynamicColor=false) {
                VishnuCrockeryApp(
                    onInitiatePayment = { amount, email, phone ->
                        startPayment(amount, email, phone)
                    }
                )
            }
        }
    }
    private fun startPayment(amount: Double, email: String, phone: String) {
        // Real Razorpay Code
        val checkout = Checkout()
        checkout.setKeyID(BuildConfig.RAZORPAY_KEY_ID)
        try {
            val options = JSONObject()
            options.put("name", "Vishnu Crockery")
            options.put("description", "Payment for Order")
            options.put("currency", "INR")
            options.put("amount", Math.round(amount * 100)) // Paise (rounded, not truncated)
            options.put("prefill.email", email)
            options.put("prefill.contact", phone)
            checkout.open(this, options)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            if (paymentRouter.consumePurpose() == PaymentPurpose.GIFTING) {
                paymentRouter.deliverGifting(PaymentResult.Failure(e.message ?: "Could not open payment"))
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentID: String?, paymentData: PaymentData?) {
        Toast.makeText(this, "Payment Successful!", Toast.LENGTH_SHORT).show()
        val purpose = paymentRouter.consumePurpose()
        if (razorpayPaymentID != null) {
            when (purpose) {
                PaymentPurpose.CART -> cartViewModel.onPaymentSuccess(razorpayPaymentID)
                PaymentPurpose.GIFTING -> paymentRouter.deliverGifting(PaymentResult.Success(razorpayPaymentID))
            }
        } else if (purpose == PaymentPurpose.GIFTING) {
            paymentRouter.deliverGifting(PaymentResult.Failure("Payment ID missing"))
        }
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        Toast.makeText(this, "Payment Failed: $response", Toast.LENGTH_SHORT).show()
        if (paymentRouter.consumePurpose() == PaymentPurpose.GIFTING) {
            paymentRouter.deliverGifting(PaymentResult.Failure(response ?: "Payment failed"))
        }
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
    val role by viewModel.role.collectAsState()

    if(startDestination == null){
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator() // Or your App Logo
        }
    }else {
        NavHost(navController = navController, startDestination = startDestination!!) {
            composable("auth_screen") {
                LaunchedEffect(Unit) {
                    authViewModel.navigationEvent.collect { destination ->
                        when (destination) {
                            is AuthViewModel.AuthDestination.AdminDashboard -> {
                                navController.navigate("admin_dashboard") {
                                    popUpTo("auth_screen") { inclusive = true }
                                }
                            }
                            is AuthViewModel.AuthDestination.Catalog -> {
                                navController.navigate("catalog") {
                                    popUpTo("auth_screen") { inclusive = true }
                                }
                            }
                            is AuthViewModel.AuthDestination.WholesaleHome -> {
                                navController.navigate("wholesale_home") {
                                    popUpTo("auth_screen") { inclusive = true }
                                }
                            }
                        }
                    }
                }
                // Login button -> onSignIn() -> navigationEvent above handles routing.
                AuthScreen(viewModel = authViewModel) // Pass the SAME instance
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
                            },
                            onGiftingClick = {
                                navController.navigate("gifting_home")
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
                    onInitiatePayment = onInitiatePayment,
                    onProductClick = { productId ->
                       navController.navigate("detail/{productId}")
                    }
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
                // Route-level guard: only Admin can reach the dashboard, even via
                // a direct navigate() call, stale deep link, or back-stack replay.
                if (role != UserRole.ADMIN) {
                    LaunchedEffect(Unit) {
                        navController.navigate("catalog") {
                            popUpTo("admin_dashboard") { inclusive = true }
                        }
                    }
                } else {
                    AdminDashboardScreen(
                        onAddProductClick = { storeId ->
                            navController.navigate("add_edit_product?storeId=$storeId")
                        },
                        onGoToStoreClick = {
                            navController.navigate("catalog")
                        },
                        onKycQueueClick = {
                            navController.navigate("kyc_queue")
                        },
                        onQuotePipelineClick = {
                            navController.navigate("admin_quote_pipeline")
                        },
                        onGiftPacksClick = {
                            navController.navigate("admin_gift_packs")
                        }
                    )
                }
            }

            composable("kyc_queue") {
                if (role != UserRole.ADMIN) {
                    LaunchedEffect(Unit) {
                        navController.navigate("catalog") { popUpTo("kyc_queue") { inclusive = true } }
                    }
                } else {
                    KycQueueScreen(onBack = { navController.popBackStack() })
                }
            }

            composable("admin_quote_pipeline") {
                if (role != UserRole.ADMIN) {
                    LaunchedEffect(Unit) {
                        navController.navigate("catalog") { popUpTo("admin_quote_pipeline") { inclusive = true } }
                    }
                } else {
                    AdminQuotePipelineScreen(onBack = { navController.popBackStack() })
                }
            }

            // --- Wholesale / Distributor ---

            composable("wholesale_home") {
                WholesaleHomeScreen(
                    onQuickOrderClick = { navController.navigate("quick_order_pad") },
                    onRequestQuoteClick = { navController.navigate("rfq_screen") },
                    onGiftingClick = { navController.navigate("gifting_home") },
                    onProfileClick = { navController.navigate("profile") }
                )
            }

            // --- Bulk Gifting (Phase 2) — any signed-in user ---

            composable("gifting_home") {
                GiftingHomeScreen(
                    onBack = { navController.popBackStack() },
                    onPackClick = { packId -> navController.navigate("gift_pack_builder?packId=$packId") },
                    onBuildOwnClick = { navController.navigate("gift_pack_builder") },
                    onMyOrdersClick = { navController.navigate("my_gifting_orders") }
                )
            }

            composable("my_gifting_orders") {
                MyGiftingOrdersScreen(
                    onBack = { navController.popBackStack() },
                    onInitiatePayment = onInitiatePayment
                )
            }

            composable(
                route = "gift_pack_builder?packId={packId}",
                arguments = listOf(navArgument("packId") { nullable = true })
            ) { backStackEntry ->
                GiftPackBuilderScreen(
                    packId = backStackEntry.arguments?.getString("packId"),
                    onBack = { navController.popBackStack() },
                    onInitiatePayment = onInitiatePayment,
                    onOrderPlaced = { navController.popBackStack("gifting_home", inclusive = false) }
                )
            }

            composable("admin_gift_packs") {
                if (role != UserRole.ADMIN) {
                    LaunchedEffect(Unit) {
                        navController.navigate("catalog") { popUpTo("admin_gift_packs") { inclusive = true } }
                    }
                } else {
                    AdminGiftPacksScreen(
                        onBack = { navController.popBackStack() },
                        onEditPack = { packId ->
                            navController.navigate(
                                if (packId == null) "admin_gift_pack_edit" else "admin_gift_pack_edit?packId=$packId"
                            )
                        },
                        onGiftingOrdersClick = { navController.navigate("admin_gifting_orders") },
                        onProductionCalendarClick = { navController.navigate("admin_production_calendar") }
                    )
                }
            }

            composable(
                route = "admin_gift_pack_edit?packId={packId}",
                arguments = listOf(navArgument("packId") { nullable = true })
            ) { backStackEntry ->
                if (role != UserRole.ADMIN) {
                    LaunchedEffect(Unit) {
                        navController.navigate("catalog") {
                            popUpTo("admin_gift_pack_edit?packId={packId}") { inclusive = true }
                        }
                    }
                } else {
                    AdminGiftPackEditScreen(
                        packId = backStackEntry.arguments?.getString("packId"),
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("admin_gifting_orders") {
                if (role != UserRole.ADMIN) {
                    LaunchedEffect(Unit) {
                        navController.navigate("catalog") { popUpTo("admin_gifting_orders") { inclusive = true } }
                    }
                } else {
                    AdminGiftingOrdersScreen(onBack = { navController.popBackStack() })
                }
            }

            composable("admin_production_calendar") {
                if (role != UserRole.ADMIN) {
                    LaunchedEffect(Unit) {
                        navController.navigate("catalog") { popUpTo("admin_production_calendar") { inclusive = true } }
                    }
                } else {
                    AdminProductionCalendarScreen(
                        onBack = { navController.popBackStack() },
                        onCapacitySettingsClick = { navController.navigate("admin_capacity_settings") }
                    )
                }
            }

            composable("admin_capacity_settings") {
                if (role != UserRole.ADMIN) {
                    LaunchedEffect(Unit) {
                        navController.navigate("catalog") { popUpTo("admin_capacity_settings") { inclusive = true } }
                    }
                } else {
                    AdminCapacitySettingsScreen(onBack = { navController.popBackStack() })
                }
            }

            composable("quick_order_pad") {
                QuickOrderPadScreen(onBack = { navController.popBackStack() })
            }

            composable("rfq_screen") {
                RfqScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = "add_edit_product?productId={productId}&storeId={storeId}",
                arguments = listOf(
                    navArgument("productId") { nullable = true },
                    navArgument("storeId") { nullable = true }
                )
            ) { backStackEntry ->
                // Same guard as admin_dashboard — product create/edit is Admin-only.
                if (role != UserRole.ADMIN) {
                    LaunchedEffect(Unit) {
                        navController.navigate("catalog") {
                            popUpTo("add_edit_product?productId={productId}&storeId={storeId}") { inclusive = true }
                        }
                    }
                } else {
                    AddEditProductScreen(
                        productId = backStackEntry.arguments?.getString("productId"),
                        storeId = backStackEntry.arguments?.getString("storeId"),
                        onBack = { navController.popBackStack() }
                    )
                }
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