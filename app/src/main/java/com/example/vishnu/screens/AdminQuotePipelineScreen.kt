package com.example.vishnu.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.model.Rfq
import com.example.vishnu.viewModels.AdminQuotePipelineViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminQuotePipelineScreen(
    onBack: () -> Unit,
    viewModel: AdminQuotePipelineViewModel = hiltViewModel()
) {
    val rfqs by viewModel.rfqs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quote Pipeline") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (rfqs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No open requests.", color = Color.Gray)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                items(rfqs) { rfq ->
                    AdminRfqCard(rfq = rfq, onSendQuote = { prices, terms -> viewModel.sendQuote(rfq, prices, terms) })
                }
            }
        }
    }
}

@Composable
fun AdminRfqCard(rfq: Rfq, onSendQuote: (pricesByRfqItemId: Map<String, Double>, terms: String?) -> Unit) {
    // One price field per RFQ line — a single price for the whole request
    // doesn't work once it spans more than one distinct product.
    val prices = remember(rfq.id) {
        mutableStateMapOf<String, String>().apply {
            rfq.items.forEach { item ->
                put(item.id, rfq.latestQuote?.priceFor(item.id)?.toString() ?: "")
            }
        }
    }
    var terms by remember(rfq.id) { mutableStateOf(rfq.latestQuote?.terms ?: "") }

    val parsedPrices = rfq.items.mapNotNull { item -> prices[item.id]?.toDoubleOrNull()?.let { item.id to it } }.toMap()
    val allPricesEntered = parsedPrices.size == rfq.items.size

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Request #${rfq.id.take(8)} · ${rfq.status.uppercase()}", fontWeight = FontWeight.Bold)
            rfq.neededByDate?.let { Text("Needed by: $it", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            if (!rfq.notes.isNullOrBlank()) Text("Notes: ${rfq.notes}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            Spacer(Modifier.height(12.dp))
            rfq.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.product.name} x${item.qty}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text("wholesale base ₹${item.product.priceWholesale}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    OutlinedTextField(
                        value = prices[item.id] ?: "",
                        onValueChange = { prices[item.id] = it },
                        label = { Text("₹/unit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = terms,
                onValueChange = { terms = it },
                label = { Text("Terms (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (allPricesEntered) onSendQuote(parsedPrices, terms.ifBlank { null }) },
                enabled = allPricesEntered,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (rfq.quotes.isEmpty()) "Send Quote" else "Send Revised Quote (v${rfq.quotes.size + 1})")
            }
        }
    }
}
