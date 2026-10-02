package com.example.vishnu.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.vishnu.uicomponents.BrandTopBar
import com.example.vishnu.uicomponents.StoreSection
import com.example.vishnu.uicomponents.StoreSectionTabs
import kotlinx.coroutines.launch
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
    onGiftingClick: () -> Unit,
    onWholesaleClick: () -> Unit,
    onMyOrdersClick: () -> Unit,
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
    var showBriefHint by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Grid index of the budget form (tabs, title, branding header, branding cards, packs header).
    val budgetFormIndex = 4

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(brief) { if (brief != null) showBriefHint = false }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BrandTopBar(
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationDescription = "Back",
                onNavigationClick = onBack,
                actionIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
                actionDescription = "My orders",
                onActionClick = onMyOrdersClick
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = {
                        // A custom proposal is always for a budget × headcount brief.
                        if (brief != null) {
                            showRequestDialog = true
                        } else {
                            showBriefHint = true
                            scope.launch { gridState.animateScrollToItem(budgetFormIndex) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Request Custom Proposal", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 0 — section tiles
            item(span = { GridItemSpan(maxLineSpan) }) {
                StoreSectionTabs(
                    selected = StoreSection.CORPORATE,
                    onSectionClick = { section ->
                        when (section) {
                            StoreSection.ALL -> onBack()
                            StoreSection.GIFTING -> onGiftingClick()
                            StoreSection.WHOLESALE -> onWholesaleClick()
                            StoreSection.CORPORATE -> Unit
                        }
                    }
                )
            }
            // 1 — title
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 4.dp)) {
                    Text("Corporate & B2B Solutions", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Gifts for employees, clients & dealers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 2 — branding & proofing
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Custom Branding & Proofing", style = MaterialTheme.typography.titleLarge)
                    Row(
                        Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BrandingStepCard(
                            icon = Icons.Outlined.FileUpload,
                            title = "Upload Brand Logo",
                            body = "Add your logo & print instructions when you order a pack",
                            highlighted = false,
                            onClick = {
                                scope.launch { gridState.animateScrollToItem(budgetFormIndex) }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        BrandingStepCard(
                            icon = Icons.Outlined.Verified,
                            title = "Proof Branding",
                            body = "Approve a digital proof before production starts",
                            highlighted = true,
                            onClick = onMyOrdersClick,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
            // 3 — packs header + budget presets
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("Budget-Tier Gift Packs", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Presets only fill the budget field — same as typing it.
                        GiftingRules.BUDGET_TIERS.mapNotNull { it.maxPerPack }.forEach { amount ->
                            val value = amount.toInt().toString()
                            FilterChip(
                                selected = budgetText == value,
                                onClick = { viewModel.budgetText.value = value },
                                label = { Text("Up to ${formatRupees(amount)}") },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
            // 4 — budget form
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp)) {
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
                        if (showBriefHint) {
                            Text(
                                "Enter your budget and headcount, then tap Show gift options — " +
                                    "you can request a custom proposal from there.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.showOptions() },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground)
                        ) {
                            Text("Show gift options", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }

            // Proposals for the current brief
            brief?.let { (budget, headcount) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (proposals.isEmpty()) "No ready-made pack fits ${formatRupees(budget)} per person"
                        else "${proposals.size} option${if (proposals.size == 1) "" else "s"} within ${formatRupees(budget)} per person",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(proposals, key = { it.id }) { pack ->
                    ProposalCard(pack, budget, headcount, onClick = { onOpenPack(pack.id, headcount, budget) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        (if (proposals.isEmpty()) "Let us put together a proposal — " else "Looking for something different? ") +
                            "tap Request Custom Proposal and our team will prepare a pack for " +
                            "${formatRupees(budget)} per person × $headcount people.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (requests.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Your proposal requests",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(requests, key = { "request-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { request ->
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
private fun BrandingStepCard(
    icon: ImageVector,
    title: String,
    body: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val content = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = if (highlighted) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProposalCard(pack: GiftPack, budget: Double, headcount: Int, onClick: () -> Unit) {
    val price = pack.toDraft().pricePerPack
    val imageUrl = pack.imageUrl ?: pack.items.firstOrNull()?.product?.imageUrl
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            AsyncImage(
                model = imageUrl,
                contentDescription = pack.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    pack.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pack.items.joinToString(" · ") { "${it.qty} × ${it.product.name}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatRupees(price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Custom logo",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    "${formatRupees(budget - price)} under budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    "Total for $headcount: ${formatRupees(price * headcount)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ProposalRequestCard(request: CorporateProposalRequest, onOpen: (() -> Unit)?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${formatRupees(request.budgetPerPerson)} per person × ${request.headcount} people",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "\"${request.notes}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (request.status) {
                CorporateProposalRequest.OPEN ->
                    Text("Our team is preparing your proposal", color = Color(0xFFB26A00), style = MaterialTheme.typography.bodyMedium)
                CorporateProposalRequest.PROPOSED -> {
                    Text("✓ Your proposal is ready", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                    request.adminNote?.let { Text("Note from our team: $it", style = MaterialTheme.typography.bodySmall) }
                    if (onOpen != null) {
                        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                            Text("View proposal & order")
                        }
                    }
                }
                else -> {
                    Text("Closed", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
