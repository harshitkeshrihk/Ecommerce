package com.example.vishnu.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.model.GiftingOrder
import com.example.vishnu.utils.formatRupees
import com.example.vishnu.viewModels.MyGiftingOrdersViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val orderDate = DateTimeFormatter.ofPattern("dd MMM yyyy")

private fun formatIsoDay(iso: String?): String? =
    iso?.let { runCatching { LocalDate.parse(it).format(orderDate) }.getOrDefault(it) }

/** "Paid ₹X of ₹Y · Balance ₹Z due 12 Oct" — shared by the customer and admin gifting order lists. */
@Composable
fun GiftingPaymentLine(order: GiftingOrder) {
    if (order.isFullyPaid) {
        Text(
            "✓ Fully paid",
            color = Color(0xFF2E7D32),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        return
    }
    val due = order.balanceDueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val overdue = due != null && due.isBefore(LocalDate.now())
    Text(
        "Paid ${formatRupees(order.amountPaid)} of ${formatRupees(order.order.totalAmount)} · " +
            "Balance ${formatRupees(order.balance)}" +
            (due?.let { if (overdue) " was due ${it.format(orderDate)}" else " due ${it.format(orderDate)}" } ?: ""),
        color = if (overdue) MaterialTheme.colorScheme.error else Color(0xFFEF6C00),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyGiftingOrdersScreen(
    onBack: () -> Unit,
    onInitiatePayment: (amount: Double, email: String, phone: String) -> Unit,
    viewModel: MyGiftingOrdersViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val orders by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val payingOrderId by viewModel.payingOrderId.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MyGiftingOrdersViewModel.Event.LaunchPayment ->
                    onInitiatePayment(event.amount, event.email, event.phone)
                is MyGiftingOrdersViewModel.Event.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My gifting orders") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        when {
            isLoading && orders.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            orders.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("You haven't placed any gifting orders yet", color = Color.Gray)
            }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders, key = { it.orderId }) { order ->
                    MyGiftingOrderCard(
                        order = order,
                        isPaying = payingOrderId == order.orderId,
                        payEnabled = payingOrderId == null,
                        onPayBalance = { viewModel.payBalance(order) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MyGiftingOrderCard(
    order: GiftingOrder,
    isPaying: Boolean,
    payEnabled: Boolean,
    onPayBalance: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Order #${order.orderId}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(customerStatusLabel(order.order.status)) })
            }
            Text("${order.packCount} × ${order.packName}")
            Text(
                "Ship by ${formatIsoDay(order.shipByDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            order.personalizationText?.let {
                Text("Personalization: \"$it\"", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            GiftingPaymentLine(order)
            if (!order.isFullyPaid && order.order.status !in setOf("CANCELLED", "REFUNDED")) {
                Button(
                    onClick = onPayBalance,
                    enabled = payEnabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    if (isPaying) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Pay balance ${formatRupees(order.balance)}")
                    }
                }
            }
        }
    }
}

private fun customerStatusLabel(status: String): String = when (status) {
    "ADVANCE_PAID" -> "Advance paid"
    "PAID" -> "Confirmed"
    "PROCESSING" -> "In production"
    "SHIPPED" -> "Shipped"
    "DELIVERED" -> "Delivered"
    "CANCELLED" -> "Cancelled"
    else -> status.lowercase().replaceFirstChar { it.uppercase() }
}
