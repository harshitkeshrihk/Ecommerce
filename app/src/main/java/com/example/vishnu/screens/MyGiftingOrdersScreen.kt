package com.example.vishnu.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.vishnu.model.ProofStatus
import com.example.vishnu.uicomponents.PrivateImage
import com.example.vishnu.uicomponents.ProofHistory
import com.example.vishnu.uicomponents.proofStatusColor
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
    onReorder: (orderId: Long) -> Unit,
    viewModel: MyGiftingOrdersViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val orders by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val payingOrderId by viewModel.payingOrderId.collectAsState()
    val brandingBusyOrderId by viewModel.brandingBusyOrderId.collectAsState()

    var changesFor by remember { mutableStateOf<GiftingOrder?>(null) }
    var replaceLogoFor by remember { mutableStateOf<GiftingOrder?>(null) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val order = replaceLogoFor
        replaceLogoFor = null
        if (uri != null && order != null) viewModel.replaceLogo(order, uri, order.logoNotes.orEmpty())
    }

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
                        onPayBalance = { viewModel.payBalance(order) },
                        brandingBusy = brandingBusyOrderId == order.orderId,
                        resolveUrl = viewModel::imageUrl,
                        onApproveProof = { viewModel.approveProof(order) },
                        onRequestChanges = { changesFor = order },
                        onReplaceLogo = {
                            replaceLogoFor = order
                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onReorder = { onReorder(order.orderId) }
                    )
                }
            }
        }
    }

    changesFor?.let { order ->
        RequestChangesDialog(
            onSend = { comment ->
                viewModel.requestProofChanges(order, comment)
                changesFor = null
            },
            onDismiss = { changesFor = null }
        )
    }
}

@Composable
private fun MyGiftingOrderCard(
    order: GiftingOrder,
    isPaying: Boolean,
    payEnabled: Boolean,
    onPayBalance: () -> Unit,
    brandingBusy: Boolean,
    resolveUrl: suspend (String) -> String?,
    onApproveProof: () -> Unit,
    onRequestChanges: () -> Unit,
    onReplaceLogo: () -> Unit,
    onReorder: () -> Unit
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
            if (order.isCorporate) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                CustomerBrandingSection(order, brandingBusy, resolveUrl, onApproveProof, onRequestChanges, onReplaceLogo)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
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
            if (order.isCorporate) {
                OutlinedButton(onClick = onReorder, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Reorder")
                }
            }
        }
    }
}

@Composable
private fun CustomerBrandingSection(
    order: GiftingOrder,
    busy: Boolean,
    resolveUrl: suspend (String) -> String?,
    onApprove: () -> Unit,
    onRequestChanges: () -> Unit,
    onReplaceLogo: () -> Unit
) {
    val status = order.proofStatus
    Text("Logo & proof", fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        order.logoPath?.let { PrivateImage(it, resolveUrl, Modifier.size(48.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(
            when (status) {
                ProofStatus.AWAITING_PROOF -> "Our team is preparing a proof of your logo"
                ProofStatus.IN_REVIEW -> "Proof v${order.latestProof?.version} is ready — please review it"
                ProofStatus.REVISION_REQUESTED -> "We're updating the proof based on your comments"
                ProofStatus.APPROVED -> "✓ Logo approved — your gifts can go into production"
                ProofStatus.NOT_REQUIRED -> ""
            },
            color = proofStatusColor(status),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }

    val latest = order.latestProof
    if (status == ProofStatus.IN_REVIEW && latest != null) {
        PrivateImage(latest.proofPath, resolveUrl, Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp))
        latest.adminNote?.let { Text("Note from our team: $it", style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApprove, enabled = !busy, modifier = Modifier.weight(1f)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Approve")
            }
            OutlinedButton(onClick = onRequestChanges, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Request changes")
            }
        }
    }
    if (order.proofs.size > (if (status == ProofStatus.IN_REVIEW) 1 else 0)) {
        Text("History", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
        ProofHistory(order.copy(proofs = order.proofs.filterNot { status == ProofStatus.IN_REVIEW && it.id == latest?.id }), resolveUrl)
    }
    if (status != ProofStatus.APPROVED) {
        TextButton(onClick = onReplaceLogo, enabled = !busy) { Text("Upload a different logo") }
    }
}

@Composable
private fun RequestChangesDialog(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What should change?") },
        text = {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(500) },
                placeholder = { Text("e.g. Make the logo bigger and centre it on the lid") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSend(comment) }, enabled = comment.isNotBlank()) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
