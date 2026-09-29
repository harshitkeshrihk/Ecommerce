package com.example.vishnu.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import com.example.vishnu.model.CorporateProposalRequest
import com.example.vishnu.model.GiftPack
import com.example.vishnu.model.GiftingRules
import com.example.vishnu.utils.formatRupees
import com.example.vishnu.viewModels.AdminProposalRequestsViewModel
import com.example.vishnu.viewModels.CorporateGiftingViewModel

/** Opens the pack builder for a corporate order: pack, headcount as pack count, budget. */
typealias OpenCorporateBuilder = (packId: String, headcount: Int, budgetPerPerson: Double) -> Unit

// ---------------------------------------------------------------------
// Customer: budget per person + headcount → proposal
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorporateGiftingScreen(
    onBack: () -> Unit,
    onOpenPack: OpenCorporateBuilder,
    viewModel: CorporateGiftingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val budgetText by viewModel.budgetText.collectAsState()
    val headcountText by viewModel.headcountText.collectAsState()
    val brief by viewModel.brief.collectAsState()
    val proposals by viewModel.proposals.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showRequestDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Corporate gifting")
                        Text("Gifts for employees, clients & dealers", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Tell us your budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { t -> viewModel.budgetText.value = t.filter { it.isDigit() || it == '.' }.take(8) },
                        label = { Text("Budget per person (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = headcountText,
                        onValueChange = { t -> viewModel.headcountText.value = t.filter(Char::isDigit).take(6) },
                        label = { Text("People") },
                        supportingText = { Text("Min ${GiftingRules.MIN_PACK_COUNT}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.showOptions() }, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                    Text("Show gift options")
                }
            }

            brief?.let { (budget, headcount) ->
                item {
                    Text(
                        if (proposals.isEmpty()) "No ready-made pack fits ${formatRupees(budget)} per person"
                        else "${proposals.size} option${if (proposals.size == 1) "" else "s"} within ${formatRupees(budget)} per person",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(proposals, key = { it.id }) { pack ->
                    ProposalCard(pack, budget, headcount, onClick = { onOpenPack(pack.id, headcount, budget) })
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (proposals.isEmpty()) "Let us put together a proposal" else "Looking for something different?",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Tell us what you have in mind and our team will prepare a custom pack " +
                                    "for ${formatRupees(budget)} per person × $headcount people.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(onClick = { showRequestDialog = true }) { Text("Request a custom proposal") }
                        }
                    }
                }
            }

            if (requests.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Your proposal requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(requests, key = { it.id }) { request ->
                    ProposalRequestCard(
                        request,
                        onOpen = request.proposedPackId?.let { packId ->
                            { onOpenPack(packId, request.headcount, request.budgetPerPerson) }
                        }
                    )
                }
            }
        }
    }

    if (showRequestDialog) {
        CustomProposalDialog(
            onSend = { notes ->
                viewModel.requestCustomProposal(notes)
                showRequestDialog = false
            },
            onDismiss = { showRequestDialog = false }
        )
    }
}

@Composable
private fun ProposalCard(pack: GiftPack, budget: Double, headcount: Int, onClick: () -> Unit) {
    val price = pack.toDraft().pricePerPack
    Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pack.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatRupees(price)} per person · ${formatRupees(budget - price)} under budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    pack.items.joinToString(" · ") { "${it.qty} × ${it.product.name}" },
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Total for $headcount people: ${formatRupees(price * headcount)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun ProposalRequestCard(request: CorporateProposalRequest, onOpen: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${formatRupees(request.budgetPerPerson)} per person × ${request.headcount} people",
                fontWeight = FontWeight.SemiBold
            )
            Text("\"${request.notes}\"", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            when (request.status) {
                CorporateProposalRequest.OPEN ->
                    Text("Our team is preparing your proposal", color = Color(0xFFEF6C00), style = MaterialTheme.typography.bodyMedium)
                CorporateProposalRequest.PROPOSED -> {
                    Text("✓ Your proposal is ready", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                    request.adminNote?.let { Text("Note from our team: $it", style = MaterialTheme.typography.bodySmall) }
                    if (onOpen != null) {
                        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("View proposal & order") }
                    }
                }
                else -> {
                    Text("Closed", color = Color.Gray)
                    request.adminNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun CustomProposalDialog(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom proposal") },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(1000) },
                label = { Text("What are you looking for?") },
                placeholder = { Text("e.g. Premium copper bottle + tumbler in a branded box, needed by 20 Oct") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSend(notes) }, enabled = notes.isNotBlank()) { Text("Send request") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------
// Admin: proposal requests
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProposalRequestsScreen(
    onBack: () -> Unit,
    onCreatePack: () -> Unit,
    viewModel: AdminProposalRequestsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val requests by viewModel.requests.collectAsState()
    val packs by viewModel.packs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var respondTo by remember { mutableStateOf<CorporateProposalRequest?>(null) }
    var closeRequest by remember { mutableStateOf<CorporateProposalRequest?>(null) }

    // Reload on return (e.g. after creating a pack for a request).
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proposal requests") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        when {
            isLoading && requests.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            requests.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No proposal requests yet", color = Color.Gray)
            }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(requests, key = { it.id }) { request ->
                    AdminProposalRequestCard(
                        request = request,
                        proposedPack = packs.firstOrNull { it.id == request.proposedPackId },
                        onRespond = { respondTo = request },
                        onClose = { closeRequest = request }
                    )
                }
            }
        }
    }

    respondTo?.let { request ->
        RespondWithPackDialog(
            request = request,
            packs = packs,
            onCreatePack = { respondTo = null; onCreatePack() },
            onSend = { pack, note -> viewModel.respond(request, pack, note); respondTo = null },
            onDismiss = { respondTo = null }
        )
    }
    closeRequest?.let { request ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { closeRequest = null },
            title = { Text("Close without a proposal?") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text("Reason shown to the customer") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.close(request, note); closeRequest = null }) { Text("Close request") } },
            dismissButton = { TextButton(onClick = { closeRequest = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminProposalRequestCard(
    request: CorporateProposalRequest,
    proposedPack: GiftPack?,
    onRespond: () -> Unit,
    onClose: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatRupees(request.budgetPerPerson)} / person × ${request.headcount}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(onClick = {}, label = { Text(request.status.replaceFirstChar { it.uppercase() }) })
            }
            Text(
                "Total budget ≈ ${formatRupees(request.budgetPerPerson * request.headcount)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text("\"${request.notes}\"", style = MaterialTheme.typography.bodyMedium)
            if (request.status == CorporateProposalRequest.PROPOSED) {
                Text(
                    "Proposed: ${proposedPack?.name ?: "pack"}" +
                        (proposedPack?.let { " · ${formatRupees(it.toDraft().pricePerPack)} / person" } ?: ""),
                    color = Color(0xFF2E7D32),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            request.adminNote?.let { Text("Your note: $it", style = MaterialTheme.typography.bodySmall) }
            if (request.status != CorporateProposalRequest.CLOSED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(onClick = onRespond) {
                        Text(if (request.status == CorporateProposalRequest.OPEN) "Send a proposal" else "Change proposal")
                    }
                    if (request.status == CorporateProposalRequest.OPEN) {
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RespondWithPackDialog(
    request: CorporateProposalRequest,
    packs: List<GiftPack>,
    onCreatePack: () -> Unit,
    onSend: (GiftPack, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<GiftPack?>(null) }
    var note by remember { mutableStateOf("") }
    val sellable = packs.filter { it.items.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Propose a pack") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Budget ${formatRupees(request.budgetPerPerson)} per person. Hidden packs can be proposed — " +
                        "only this customer will see them.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onCreatePack) { Text("+ Create a new pack first") }
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(sellable, key = { it.id }) { pack ->
                        val price = pack.toDraft().pricePerPack
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = pack }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected?.id == pack.id, onClick = { selected = pack })
                            Column(Modifier.weight(1f)) {
                                Text(pack.name + if (pack.isActive) "" else " (hidden)", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatRupees(price)} / person",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (price > request.budgetPerPerson + 0.005) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text("Note to the customer (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let { onSend(it, note) } }, enabled = selected != null) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
