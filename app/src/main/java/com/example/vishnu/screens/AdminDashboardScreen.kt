package com.example.vishnu.screens


import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.model.Order
import com.example.vishnu.utils.last6Digits
import com.example.vishnu.viewModels.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onAddProductClick: () -> Unit,
    onGoToStoreClick: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val orders by viewModel.allOrders.collectAsState()
    val context = LocalContext.current

    // Calculate Stats on the fly
    val totalRevenue = orders.sumOf { it.totalAmount }
    val pendingCount = orders.count { it.status == "PROCESSING" }
    val shippedCount = orders.count { it.status == "SHIPPED" }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8F9FA), // Light grey background
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Admin Command Center",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                ),
                actions = {
                    OutlinedButton(
                        onClick = onGoToStoreClick,
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        border = null // Removes border for a cleaner look
                    ) {
                        Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Store Mode")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProductClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Product") }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            // --- 1. STATS OVERVIEW SECTION ---
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = "Total Revenue",
                            value = "₹${totalRevenue.toInt()}",
                            icon = Icons.Default.CurrencyRupee,
                            color = Color(0xFF4CAF50), // Green
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Pending",
                            value = "$pendingCount",
                            icon = Icons.Outlined.PendingActions,
                            color = Color(0xFFFF9800), // Orange
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = "Total Orders",
                            value = "${orders.size}",
                            icon = Icons.Outlined.Inventory2,
                            color = Color(0xFF2196F3), // Blue
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Shipped",
                            value = "$shippedCount",
                            icon = Icons.Outlined.LocalShipping,
                            color = Color(0xFF9C27B0), // Purple
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // --- 2. RECENT ORDERS HEADER ---
            item {
                PaddingValues(horizontal = 16.dp, vertical = 8.dp).let {
                    Text(
                        "Recent Orders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }

            // --- 3. ORDERS LIST ---
            if (orders.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No orders received yet.", color = Color.Gray)
                    }
                }
            } else {
                items(orders.reversed()) { order -> // Show newest first
                    AdminOrderCardEnhanced(order = order, onStatusChange = { newStatus ->
                        viewModel.changeStatus(order.id, newStatus)
                    })
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                }
            }
        }
    }
}

// ----------------------------------------------------------------
// CUSTOM COMPONENTS
// ----------------------------------------------------------------

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = title, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun AdminOrderCardEnhanced(
    order: Order,
    onStatusChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val statusOptions = listOf("PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED")

    // Determine Status Colors
    val (statusColor, statusBg) = when (order.status) {
        "DELIVERED" -> Color(0xFF4CAF50) to Color(0xFFE8F5E9)
        "CANCELLED" -> Color(0xFFF44336) to Color(0xFFFFEBEE)
        "SHIPPED" -> Color(0xFF2196F3) to Color(0xFFE3F2FD)
        else -> Color(0xFFFF9800) to Color(0xFFFFF3E0) // Processing
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // -- Header: ID and Status --
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ORDER #${last6Digits(order.id)}", // Show only last 6 chars for cleanliness
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status Chip
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable { expanded = true } // Click chip to change status
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = order.status,
                                color = statusColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Dropdown attached to the status chip
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        statusOptions.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status) },
                                onClick = {
                                    onStatusChange(status)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha = 0.3f))

            // -- Body: Details --
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circle Icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("Customer Order", fontWeight = FontWeight.SemiBold)
                    // If you have date in your model, display it here:
                    // Text("Placed on: ${order.date}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                // Amount
                Text(
                    text = "₹${order.totalAmount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // -- Footer: Actions (Optional) --
            // You can add a "View Details" text button here if you implement order details later.
        }
    }
}