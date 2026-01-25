package com.example.vishnu.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.model.Order
import com.example.vishnu.uicomponents.OrderItemCard // Ensure this matches your package
import com.example.vishnu.viewModels.ProfileViewModel

// Enum to manage internal navigation
enum class ProfileSubScreen {
    MENU, EDIT_PROFILE, ORDERS, WISHLIST , ORDER_DETAILS , ADDRESSES
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogoutClick: () -> Unit,
    onBackClick: () -> Unit,
    onTrackOrderClick : (Long) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    var currentScreen by remember { mutableStateOf(ProfileSubScreen.MENU) }
    val context = LocalContext.current

    var selectedOrder by remember { mutableStateOf<Order?>(null) }

    // Handle System Back Button
    BackHandler(enabled = currentScreen != ProfileSubScreen.MENU) {
        currentScreen = ProfileSubScreen.MENU
    }

    // Refresh Data whenever we return to this screen
    LaunchedEffect(Unit) {
        viewModel.loadOrderData()
    }

    // Main Container with Animation
    Scaffold(
        containerColor = Color(0xFFF5F5F5) // Light Grey Background for modern feel
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            AnimatedContent(
                targetState = currentScreen,
                label = "ProfileNav",
                transitionSpec = {
                    fadeIn() + slideInHorizontally { it } with fadeOut() + slideOutHorizontally { -it }
                }
            ) { screen ->
                when (screen) {
                    ProfileSubScreen.MENU -> {
                        DashboardView(
                            viewModel = viewModel,
                            onNavigate = { currentScreen = it },
                            onSupportClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:1234567890") // Replace with real number
                                }
                                context.startActivity(intent)
                            },
                            onLogoutClick = onLogoutClick,
                            onBackClick = onBackClick,
                        )
                    }
                    ProfileSubScreen.EDIT_PROFILE -> {
                        EditProfileView(
                            viewModel = viewModel,
                            onBack = { currentScreen = ProfileSubScreen.MENU }
                        )
                    }
                    ProfileSubScreen.ORDERS -> {
                        OrdersView(
                            viewModel = viewModel,
                            onBack = { currentScreen = ProfileSubScreen.MENU },
                            onOrderClick = {order->
                                selectedOrder = order
                                currentScreen = ProfileSubScreen.ORDER_DETAILS
                            }
                        )
                    }
                    ProfileSubScreen.WISHLIST -> {
                        // Placeholder for Wishlist
                        EmptyStateScreen(
                            title = "Your Wishlist",
                            icon = Icons.Outlined.FavoriteBorder,
                            message = "You haven't saved any items yet.",
                            onBack = { currentScreen = ProfileSubScreen.MENU }
                        )
                    }
                    ProfileSubScreen.ORDER_DETAILS -> {
                        if (selectedOrder != null) {
                            // Note: Navigation to tracking would need to be handled differently
                            // since we're in a nested navigation. For now, we'll pass null.
                            // You can implement a callback system if needed.
                            OrderDetailScreen(
                                order = selectedOrder!!,
                                onBack = { currentScreen = ProfileSubScreen.ORDERS },
                                onTrackOrder = { orderId ->
                                    onTrackOrderClick(orderId)
                                }
                            )
                        }
                    }
                    ProfileSubScreen.ADDRESSES -> {
                        SavedAddressesScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = ProfileSubScreen.MENU }
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------
// 1. DASHBOARD VIEW (The Main Menu)
// ------------------------------------------------------------
@Composable
fun DashboardView(
    viewModel: ProfileViewModel,
    onNavigate: (ProfileSubScreen) -> Unit,
    onSupportClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onBackClick:() -> Unit
) {
    // Observe Profile Data
    val name by viewModel.name.collectAsState()
    val phone by viewModel.phone.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Header Section ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Avatar
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(70.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = name.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Info
                Column {
                    Text(text = name.ifEmpty { "Guest User" }, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(text = phone.ifEmpty { "No phone linked" }, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        // --- Menu Options ---
        Column(modifier = Modifier.padding(16.dp)) {

            MenuOptionCard(
                icon = Icons.Outlined.Person,
                title = "Edit Profile",
                subtitle = "Update name, address & phone",
                onClick = { onNavigate(ProfileSubScreen.EDIT_PROFILE) }
            )

            MenuOptionCard(
                icon = Icons.Outlined.ShoppingBag,
                title = "My Orders",
                subtitle = "Active orders and history",
                onClick = { onNavigate(ProfileSubScreen.ORDERS) }
            )

            MenuOptionCard(
                icon = Icons.Outlined.FavoriteBorder,
                title = "Wishlist",
                subtitle = "Your saved products",
                onClick = { onNavigate(ProfileSubScreen.WISHLIST) }
            )

            MenuOptionCard(
                icon = Icons.Outlined.SupportAgent,
                title = "Help & Support",
                subtitle = "Contact customer care",
                onClick = onSupportClick
            )

            MenuOptionCard(
                icon = Icons.Outlined.LocationOn,
                title = "Add Address",
                subtitle = "Manage Your Addresses",
                onClick = {onNavigate(ProfileSubScreen.ADDRESSES)}
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Logout
            OutlinedButton(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out")
            }
        }
    }
}

@Composable
fun MenuOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}

// ------------------------------------------------------------
// 2. EDIT PROFILE VIEW (Refactored from your code)
// ------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileView(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val name by viewModel.name.collectAsState()
    val phone by viewModel.phone.collectAsState()
    val address by viewModel.address.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.updateStatus.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatus()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileTextField(value = name, onValueChange = { viewModel.name.value = it }, label = "Full Name", icon = Icons.Default.Person)
            Spacer(modifier = Modifier.height(16.dp))
            ProfileTextField(value = phone, onValueChange = { viewModel.phone.value = it }, label = "Phone Number", icon = Icons.Default.Phone, keyboardType = KeyboardType.Phone)
            Spacer(modifier = Modifier.height(16.dp))
            ProfileTextField(value = address, onValueChange = { viewModel.address.value = it }, label = "Address", icon = Icons.Default.Home, singleLine = false, modifier = Modifier.height(100.dp))

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.saveProfile() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Save Changes")
            }
        }
    }
}

// ------------------------------------------------------------
// 3. ORDERS VIEW (Active / History Tabs)
// ------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersView(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onOrderClick: (Order) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Active", "History")
    val activeOrders by viewModel.activeOrders.collectAsState()
    val pastOrders by viewModel.pastOrders.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadOrderData()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("My Orders") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            val ordersToShow = if (selectedTab == 0) activeOrders else pastOrders

            if (ordersToShow.isEmpty()) {
                EmptyStateScreen(
                    title = "No Orders Found",
                    icon = Icons.Outlined.History,
                    message = "You have no ${tabs[selectedTab].lowercase()} orders.",
                    onBack = null // Don't show back button inside the empty component
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF5F5F5))
                ) {
                    items(ordersToShow) { order ->
                        // Using the Card we created in previous steps
                        OrderItemCard(
                            order = order,
                            onOrderClick = { onOrderClick(order) }
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------
// HELPER COMPONENTS
// ------------------------------------------------------------
@Composable
fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

@Composable
fun EmptyStateScreen(
    title: String,
    icon: ImageVector,
    message: String,
    onBack: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message, color = Color.Gray)

        if (onBack != null) {
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onBack) {
                Text("Go Back")
            }
        }
    }
}