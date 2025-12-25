package com.example.vishnu.uicomponents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.model.Order
import com.example.vishnu.model.OrderItemDetail
import com.example.vishnu.repository.ProfileRepository
import com.example.vishnu.utils.formatIsoDate
import com.example.vishnu.viewModels.ProfileViewModel
import io.github.jan.supabase.postgrest.query.Order as SupabaseOrder
import kotlinx.coroutines.launch

@Composable
fun OrderItemCard(
    order: Order,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    var expanded by remember { mutableStateOf(false) }
    // State to hold the detailed items once fetched
    var orderDetails by remember { mutableStateOf<List<OrderItemDetail>>(emptyList()) }
    var isLoadingDetails by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Effect to fetch data ONLY when expanded for the first time
    LaunchedEffect(expanded) {
        if (expanded && orderDetails.isEmpty()) {
            isLoadingDetails = true
            // Fetch in background scope
            scope.launch {
                orderDetails = viewModel.getOrderItems(order.id)
                isLoadingDetails = false
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }, // Toggle expand on click
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // --- HEADER ROW (Always Visible) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: ID and Date
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Order #${order.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatIsoDate(order.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                // Right Side: Status Badge
                OrderStatusBadge(status = order.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- TOTAL ROW ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Amount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = "₹${order.totalAmount.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expand/Collapse Indicator + Divider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Divider(modifier = Modifier.weight(1f), color = Color.LightGray.copy(alpha = 0.3f))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle Details",
                    tint = Color.Gray,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }


            // --- EXPANDABLE DETAILS SECTION ---
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (isLoadingDetails) {
                        // Loading State
                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (orderDetails.isEmpty()) {
                        // Error/Empty State
                        Text("No items found for this order.", style = MaterialTheme.typography.bodySmall, color = Color.Red)
                    } else {
                        // List of Items
                        Text("Items", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        orderDetails.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Quantity x Name
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = "${item.quantity}x",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = item.productName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                // Price
                                Text(
                                    text = "₹${(item.price * item.quantity).toInt()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (item != orderDetails.last()) {
                                Divider(color = Color.LightGray.copy(alpha = 0.1f), thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper Component for the Status Chip
@Composable
fun OrderStatusBadge(status: String) {
    val (bgColor, textColor) = when (status) {
        "PAID" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32)) // Green
        "DELIVERED" -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0)) // Blue
        "PROCESSING", "SHIPPED" -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100)) // Orange
        "CANCELLED" -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828)) // Red
        else -> Pair(Color.LightGray, Color.DarkGray)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text(
            text = status,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}