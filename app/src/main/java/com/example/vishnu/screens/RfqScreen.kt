package com.example.vishnu.screens

import android.widget.Toast
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
import com.example.vishnu.model.Rfq
import com.example.vishnu.utils.BUSINESS_WHATSAPP_NUMBER
import com.example.vishnu.utils.shareOrderSummaryOnWhatsApp
import com.example.vishnu.viewModels.RfqViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RfqScreen(
    onBack: () -> Unit,
    viewModel: RfqViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("New Request", "My Requests")
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RfqViewModel.RfqEvent.Submitted -> {
                    Toast.makeText(context, "Request sent. We'll quote you shortly.", Toast.LENGTH_SHORT).show()
                    selectedTab = 1
                }
                is RfqViewModel.RfqEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is RfqViewModel.RfqEvent.OrderConfirmed -> shareOrderSummaryOnWhatsApp(context, event.summaryText, BUSINESS_WHATSAPP_NUMBER)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request a Quote") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
            if (selectedTab == 0) NewRfqTab(viewModel) else MyRfqsTab(viewModel)
        }
    }
}

@Composable
fun NewRfqTab(viewModel: RfqViewModel) {
    val products by viewModel.products.collectAsState()
    val quantities by viewModel.quantities.collectAsState()
    val neededByDate by viewModel.neededByDate.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(products) { product ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(product.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    val qty = quantities[product.id] ?: 0
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.setQuantity(product, qty - 1) }, enabled = qty > 0) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }
                            Text("$qty", modifier = Modifier.padding(horizontal = 4.dp))
                            IconButton(onClick = { viewModel.setQuantity(product, qty + 1) }) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }
                }
                Divider(color = Color.LightGray.copy(alpha = 0.3f))
            }
        }

        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = neededByDate,
                onValueChange = { viewModel.neededByDate.value = it },
                label = { Text("Needed by (yyyy-mm-dd, optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { viewModel.notes.value = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.submitRfq() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = quantities.isNotEmpty() && !isSubmitting
            ) {
                if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Send Request (${quantities.size} item(s))")
            }
        }
    }
}

@Composable
fun MyRfqsTab(viewModel: RfqViewModel) {
    val rfqs by viewModel.myRfqs.collectAsState()

    if (rfqs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No requests yet.", color = Color.Gray)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(rfqs) { rfq ->
                RfqCard(rfq = rfq, onAcceptQuote = { quote -> viewModel.acceptQuote(rfq, quote) })
            }
        }
    }
}

@Composable
fun RfqCard(rfq: Rfq, onAcceptQuote: (com.example.vishnu.model.Quote) -> Unit) {
    val (statusColor, statusBg) = when (rfq.status) {
        "won" -> Color(0xFF4CAF50) to Color(0xFFE8F5E9)
        "lost" -> Color(0xFFF44336) to Color(0xFFFFEBEE)
        "quoted" -> Color(0xFF2196F3) to Color(0xFFE3F2FD)
        else -> Color(0xFFFF9800) to Color(0xFFFFF3E0)
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Request #${rfq.id.take(8)}", fontWeight = FontWeight.Bold)
                Surface(color = statusBg, shape = RoundedCornerShape(50)) {
                    Text(rfq.status.uppercase(), color = statusColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            val quote = rfq.latestQuote

            Spacer(Modifier.height(8.dp))
            rfq.items.forEach { item ->
                val quotedPrice = quote?.priceFor(item.id)
                val line = if (quotedPrice != null) {
                    "• ${item.product.name} x${item.qty} — ₹$quotedPrice/unit"
                } else {
                    "• ${item.product.name} x${item.qty}"
                }
                Text(line, style = MaterialTheme.typography.bodySmall)
            }

            if (quote != null) {
                Spacer(Modifier.height(8.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                val total = rfq.items.sumOf { it.qty * (quote.priceFor(it.id) ?: 0.0) }
                Text("Quoted total: ₹${total}", fontWeight = FontWeight.SemiBold)
                if (!quote.terms.isNullOrBlank()) {
                    Text("Terms: ${quote.terms}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                if (rfq.status == "quoted") {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onAcceptQuote(quote) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Accept & Confirm Order")
                    }
                }
            }
        }
    }
}
