package com.example.vishnu.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.model.Order
import com.example.vishnu.model.OrderItemDetail
import com.example.vishnu.utils.last6Digits
import com.example.vishnu.viewModels.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    order: Order,
    viewModel: ProfileViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onTrackOrder: ((Long) -> Unit)? = null // Optional callback for navigation
) {

    var orderDetails by remember { mutableStateOf<List<OrderItemDetail>>(emptyList()) }
    var isLoadingDetails by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val deliveryAssignment by viewModel.currentAssignment.collectAsState()

    LaunchedEffect(Unit) {
        if (orderDetails.isEmpty()) {
            isLoadingDetails = true
            // Fetch in background scope
            scope.launch {
                orderDetails = viewModel.getOrderItems(order.id)
                isLoadingDetails = false
            }
        }
    }

    LaunchedEffect(order.id) {
        viewModel.loadDeliveryAssignment(order.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Order #${last6Digits(order.id)}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(order.createdAt.toString(), fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        if(isLoadingDetails){
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }else{
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 1. Live Status Tracker
                StatusTracker(currentStatus = order.status)

                // Track Order Button (only show if delivery partner is assigned and order is in transit)

                val isTrackable = deliveryAssignment != null &&
                        (deliveryAssignment?.deliveryStatus == "SHIPPED" ||
                                deliveryAssignment?.deliveryStatus == "IN_TRANSIT" ||
                                deliveryAssignment?.deliveryStatus == "OUT_FOR_DELIVERY"||
                                deliveryAssignment?.deliveryStatus == "PICKED_UP")

                if (isTrackable) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onTrackOrder?.invoke(order.id)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Track Your Order",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Live location tracking",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. Store Info
                Text("Order from", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
//            Text(order.storename ?: "Store Name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // 3. Items List
                Text("Items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                orderDetails.forEach { orderItem ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${orderItem.quantity}x  ${orderItem.productName}", modifier = Modifier.weight(1f))
                        Text("₹${(orderItem.price * orderItem.quantity).toInt()}", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // 4. Bill Summary
                BillRow("Item Total", "₹${order.totalAmount.toInt()}")
                BillRow("Delivery Fee", "Free", isGreen = true) // Logic can be improved
                BillRow("Taxes", "₹0") // Logic can be improved
                Spacer(modifier = Modifier.height(8.dp))
                BillRow("Grand Total", "₹${order.totalAmount.toInt()}", isBold = true)

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Address
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delivery Address", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
//                    Text(order.address, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                    }
                }
            }
        }
    }
}

// --- Helper Composable for the Tracker ---
@Composable
fun StatusTracker(currentStatus: String) {
    // Define your Order Lifecycle stages
    val stages = listOf("PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED")

    // Find where the current order is in the lifecycle
    // (Note: You might need to normalize strings, e.g., "PENDING" -> "Pending")
    val currentIndex = stages.indexOfFirst { it.equals(currentStatus, ignoreCase = true) }.let {
        if (it == -1) 0 else it // Default to start if unknown
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Order Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        stages.forEachIndexed { index, stage ->
            val isCompleted = index <= currentIndex
            val isCurrent = index == currentIndex

            Row(modifier = Modifier.height(40.dp)) {
                // The Timeline Line
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
                    // Dot
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = if (isCompleted) Color(0xFF4CAF50) else Color.LightGray)
                    }
                    // Line (except for last item)
                    if (index < stages.lastIndex) {
                        Canvas(modifier = Modifier.width(2.dp).weight(1f)) {
                            drawLine(
                                color = if (index < currentIndex) Color(0xFF4CAF50) else Color.LightGray,
                                start = Offset(center.x, 0f),
                                end = Offset(center.x, size.height),
                                strokeWidth = 4f
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // The Text
                Text(
                    text = stage,
                    fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCompleted) Color.Black else Color.Gray
                )
            }
        }
    }
}

@Composable
fun BillRow(label: String, value: String, isBold: Boolean = false, isGreen: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal, color = if (isGreen) Color(0xFF4CAF50) else Color.Black)
    }
}