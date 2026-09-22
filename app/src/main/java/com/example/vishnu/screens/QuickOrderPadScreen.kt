package com.example.vishnu.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.model.PriceSource
import com.example.vishnu.model.Product
import com.example.vishnu.model.ResolvedPrice
import com.example.vishnu.utils.BUSINESS_WHATSAPP_NUMBER
import com.example.vishnu.utils.shareOrderSummaryOnWhatsApp
import com.example.vishnu.viewModels.QuickOrderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickOrderPadScreen(
    onBack: () -> Unit,
    viewModel: QuickOrderViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsState()
    val quantities by viewModel.quantities.collectAsState()
    val resolvedPrices by viewModel.resolvedPrices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isPlacingOrder by viewModel.isPlacingOrder.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickOrderViewModel.QuickOrderEvent.Placed -> {
                    shareOrderSummaryOnWhatsApp(context, event.summaryText, BUSINESS_WHATSAPP_NUMBER)
                }
                is QuickOrderViewModel.QuickOrderEvent.Failed -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val itemCount = quantities.size
    val total by viewModel.totalAmount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick-Order Pad") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        bottomBar = {
            if (itemCount > 0) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("$itemCount item(s)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text("₹${total.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.placeOrder() },
                            enabled = !isPlacingOrder
                        ) {
                            if (isPlacingOrder) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            else Text("Place Order")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 16.dp)
            ) {
                items(products) { product ->
                    QuickOrderRow(
                        product = product,
                        qty = quantities[product.id] ?: 0,
                        resolvedPrice = resolvedPrices[product.id],
                        onQtyChange = { newQty -> viewModel.setQuantity(product, newQty) }
                    )
                }
            }
        }
    }
}

@Composable
fun QuickOrderRow(
    product: Product,
    qty: Int,
    resolvedPrice: ResolvedPrice?,
    onQtyChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "/${product.unitOfMeasure}" + (product.material?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            if (resolvedPrice != null && qty > 0) {
                val label = when (resolvedPrice.source) {
                    PriceSource.MOQ_SLAB -> "₹${resolvedPrice.unitPrice}/unit · MOQ price"
                    PriceSource.WHOLESALE_BASE -> "₹${resolvedPrice.unitPrice}/unit · base wholesale"
                    else -> "₹${resolvedPrice.unitPrice}/unit"
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }

        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onQtyChange(qty - 1) }, enabled = qty > 0) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                Text("$qty", modifier = Modifier.padding(horizontal = 4.dp))
                IconButton(onClick = { onQtyChange(qty + 1) }) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }
        }
    }
    Divider(color = Color.LightGray.copy(alpha = 0.3f))
}
