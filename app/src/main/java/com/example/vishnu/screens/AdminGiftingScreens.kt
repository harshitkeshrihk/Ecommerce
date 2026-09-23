package com.example.vishnu.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.model.BrandAsset
import com.example.vishnu.model.GiftPack
import com.example.vishnu.model.GiftingOrder
import com.example.vishnu.model.ProofStatus
import com.example.vishnu.uicomponents.PrivateImage
import com.example.vishnu.uicomponents.ProofHistory
import com.example.vishnu.uicomponents.proofStatusColor
import com.example.vishnu.uicomponents.proofStatusLabel
import com.example.vishnu.uicomponents.GiftProductPickerDialog
import com.example.vishnu.uicomponents.PackContentsEditor
import com.example.vishnu.utils.formatRupees
import com.example.vishnu.viewModels.AdminGiftPackEditViewModel
import com.example.vishnu.viewModels.AdminGiftPacksViewModel
import com.example.vishnu.viewModels.AdminGiftingOrdersViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ---------------------------------------------------------------------
// Gift pack list (admin)
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminGiftPacksScreen(
    onBack: () -> Unit,
    onEditPack: (packId: String?) -> Unit,
    onGiftingOrdersClick: () -> Unit,
    onProductionCalendarClick: () -> Unit,
    viewModel: AdminGiftPacksViewModel = hiltViewModel()
) {
    val packs by viewModel.packs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Refresh every time we come back from the editor.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gift Packs") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onGiftingOrdersClick) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = "Gifting orders")
                    }
                    IconButton(onClick = onProductionCalendarClick) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Production calendar")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEditPack(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New pack") }
            )
        }
    ) { padding ->
        when {
            isLoading && packs.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            packs.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No gift packs yet. First mark products as \"Suitable for gifting\" in the product form, then create a pack.",
                    color = Color.Gray
                )
            }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(packs, key = { it.id }) { pack ->
                    AdminGiftPackRow(
                        pack = pack,
                        onClick = { onEditPack(pack.id) },
                        onActiveChange = { viewModel.setActive(pack, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminGiftPackRow(pack: GiftPack, onClick: () -> Unit, onActiveChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pack.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${pack.items.size} items · ${formatRupees(pack.toDraft().pricePerPack)} / pack",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = pack.isActive, onCheckedChange = onActiveChange)
                Text(if (pack.isActive) "Live" else "Hidden", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ---------------------------------------------------------------------
// Gift pack editor (admin)
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminGiftPackEditScreen(
    packId: String?,
    onBack: () -> Unit,
    viewModel: AdminGiftPackEditViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val name by viewModel.name.collectAsState()
    val description by viewModel.description.collectAsState()
    val isActive by viewModel.isActive.collectAsState()
    val lines by viewModel.lines.collectAsState()
    val giftingProducts by viewModel.giftingProducts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(packId) { viewModel.load(packId) }
    LaunchedEffect(Unit) {
        viewModel.saveResult.collect { error ->
            if (error == null) {
                Toast.makeText(context, "Pack saved", Toast.LENGTH_SHORT).show()
                onBack()
            } else {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (packId == null) "New gift pack" else "Edit gift pack") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }, enabled = !isLoading) {
                        Icon(Icons.Default.Check, "Save")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.name.value = it },
                label = { Text("Pack name") },
                placeholder = { Text("e.g. Steel Return-Gift Set") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { viewModel.description.value = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Visible to customers", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = isActive, onCheckedChange = { viewModel.isActive.value = it })
            }

            Text("Contents of ONE pack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Add the gift box / packaging as an item too. Price per pack is the sum of the items' retail prices.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            PackContentsEditor(
                lines = lines,
                onQtyChange = viewModel::setQtyPerPack,
                onAddItemClick = { showPicker = true }
            )
            Text(
                "Price per pack: ${formatRupees(lines.sumOf { it.product.priceRetail * it.qtyPerPack })}",
                fontWeight = FontWeight.SemiBold
            )
            Button(onClick = { viewModel.save() }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Save pack")
            }
        }
    }

    if (showPicker) {
        GiftProductPickerDialog(
            products = giftingProducts,
            onPick = { viewModel.addProduct(it); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

// ---------------------------------------------------------------------
// Gifting orders (admin) — soonest ship-by first
// ---------------------------------------------------------------------

private val shipByFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminGiftingOrdersScreen(
    onBack: () -> Unit,
    viewModel: AdminGiftingOrdersViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val orders by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val uploadingOrderId by viewModel.uploadingOrderId.collectAsState()

    // Proof upload: note dialog first, then the image picker.
    var proofNoteFor by remember { mutableStateOf<GiftingOrder?>(null) }
    var pendingProof by remember { mutableStateOf<Pair<GiftingOrder, String>?>(null) }
    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val pending = pendingProof
        pendingProof = null
        if (uri != null && pending != null) viewModel.uploadProof(pending.first, uri, pending.second)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gifting Orders") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            orders.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No gifting orders yet", color = Color.Gray)
            }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders, key = { it.orderId }) { order ->
                    GiftingOrderCard(
                        order = order,
                        resolveUrl = viewModel::imageUrl,
                        isUploading = uploadingOrderId == order.orderId,
                        onUploadProof = { proofNoteFor = order }
                    )
                }
            }
        }
    }

    proofNoteFor?.let { order ->
        ProofNoteDialog(
            version = (order.latestProof?.version ?: 0) + 1,
            lastComment = order.latestProof?.takeIf { it.status == BrandAsset.REVISION_REQUESTED }?.customerComment,
            onContinue = { note ->
                proofNoteFor = null
                pendingProof = order to note
                proofPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = { proofNoteFor = null }
        )
    }
}

@Composable
private fun GiftingOrderCard(
    order: GiftingOrder,
    resolveUrl: suspend (String) -> String?,
    isUploading: Boolean,
    onUploadProof: () -> Unit
) {
    val shipBy = runCatching { LocalDate.parse(order.shipByDate) }.getOrNull()
    val daysLeft = shipBy?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${order.orderId} · ${order.occasionType.replaceFirstChar { it.uppercase() }}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(order.order.status) })
            }
            Text(
                "Ship by ${shipBy?.format(shipByFormat) ?: order.shipByDate}" +
                    (daysLeft?.let { if (it >= 0) " · $it days left" else " · ${-it} days overdue" } ?: ""),
                color = if (daysLeft != null && daysLeft < 7) MaterialTheme.colorScheme.error else Color.Unspecified,
                fontWeight = FontWeight.SemiBold
            )
            Text("${order.packCount} × ${order.packName}  ·  ${formatRupees(order.order.totalAmount)}")
            GiftingPaymentLine(order)
            Text(
                "Each pack: " + order.packContents.joinToString(", ") { "${it.qtyPerPack} × ${it.productName}" },
                style = MaterialTheme.typography.bodySmall
            )
            order.personalizationText?.let {
                Text("Engrave: \"$it\"", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(order.order.shippingAddress, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            if (order.isCorporate) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                AdminBrandingSection(order, resolveUrl, isUploading, onUploadProof)
            }
        }
    }
}

@Composable
private fun AdminBrandingSection(
    order: GiftingOrder,
    resolveUrl: suspend (String) -> String?,
    isUploading: Boolean,
    onUploadProof: () -> Unit
) {
    Text("Logo & proof", fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.Top) {
        order.logoPath?.let { PrivateImage(it, resolveUrl, Modifier.size(72.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                proofStatusLabel(order),
                color = proofStatusColor(order.proofStatus),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            order.logoNotes?.let { Text("Placement: $it", style = MaterialTheme.typography.bodySmall) }
            if (!order.canEnterProduction) {
                Text(
                    "Can't move to PROCESSING until the customer approves a proof.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
    ProofHistory(order, resolveUrl)

    // A new version is needed when there's no proof yet or the customer asked for changes.
    if (order.proofStatus == ProofStatus.AWAITING_PROOF || order.proofStatus == ProofStatus.REVISION_REQUESTED) {
        Button(
            onClick = onUploadProof,
            enabled = !isUploading && order.order.userId != null,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            if (isUploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Upload proof v${(order.latestProof?.version ?: 0) + 1}")
        }
    }
}

@Composable
private fun ProofNoteDialog(
    version: Int,
    lastComment: String?,
    onContinue: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Proof v$version") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lastComment?.let {
                    Text("Customer asked: \"$it\"", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text("Note to the customer (optional)") },
                    placeholder = { Text("e.g. Logo laser-engraved, 4 cm wide, centred") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Next you'll pick the proof image (photo of a sample or a mockup).", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        confirmButton = { TextButton(onClick = { onContinue(note) }) { Text("Choose image") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
