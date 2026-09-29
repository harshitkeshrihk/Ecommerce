package com.example.vishnu.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.example.vishnu.uicomponents.BrandTopBar
import com.example.vishnu.uicomponents.StoreSection
import com.example.vishnu.uicomponents.StoreSectionTabs
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.model.GiftPack
import com.example.vishnu.model.GiftingRules
import com.example.vishnu.model.OccasionType
import com.example.vishnu.uicomponents.GiftProductPickerDialog
import com.example.vishnu.uicomponents.PrivateImage
import com.example.vishnu.uicomponents.PackContentsEditor
import com.example.vishnu.utils.formatRupees
import com.example.vishnu.viewModels.GiftPackBuilderViewModel
import com.example.vishnu.viewModels.GiftingHomeViewModel
import com.example.vishnu.viewModels.PayChoice
import com.example.vishnu.viewModels.PaymentPlan
import com.example.vishnu.viewModels.ShipByCapacity
import com.example.vishnu.viewModels.capacityConflictMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------
// Gifting home: pick a ready-made pack (filtered by budget) or build your own
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftingHomeScreen(
    onBack: () -> Unit,
    onPackClick: (packId: String) -> Unit,
    onBuildOwnClick: () -> Unit,
    onMyOrdersClick: () -> Unit,
    onCorporateClick: () -> Unit,
    viewModel: GiftingHomeViewModel = hiltViewModel()
) {
    val packs by viewModel.visiblePacks.collectAsState()
    val selectedTier by viewModel.selectedTier.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

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
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StoreSectionTabs(
                    selected = StoreSection.GIFTING,
                    onSectionClick = { section ->
                        when (section) {
                            StoreSection.ALL -> onBack()
                            // Gifting is this screen; Wholesale / Corporate UI is not designed yet.
                            StoreSection.GIFTING, StoreSection.WHOLESALE, StoreSection.CORPORATE -> Unit
                        }
                    }
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 4.dp)) {
                    Text(
                        "Bulk Gifting",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Return gifts for weddings, functions & festivals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                GiftingOptionCard(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Build your own pack",
                    subtitle = "Choose any items from the gifting collection",
                    onClick = onBuildOwnClick
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                GiftingOptionCard(
                    icon = Icons.Outlined.Business,
                    title = "Corporate gifting",
                    subtitle = "Enter a budget per person and get gift options with your logo",
                    onClick = onCorporateClick
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 4.dp)) {
                    Text(
                        "Ready-made Packs",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BudgetChip(
                            label = "All budgets",
                            selected = selectedTier == null,
                            onClick = { viewModel.selectTier(null) }
                        )
                        GiftingRules.BUDGET_TIERS.forEach { tier ->
                            BudgetChip(
                                label = tier.label,
                                selected = selectedTier == tier,
                                onClick = { viewModel.selectTier(tier) }
                            )
                        }
                    }
                }
            }
            when {
                isLoading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                packs.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No ready-made packs in this budget yet — try building your own.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                else -> items(packs, key = { it.id }) { pack ->
                    GiftPackCard(pack, onClick = { onPackClick(pack.id) })
                }
            }
        }
    }
}

@Composable
private fun GiftingOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Medium) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            labelColor = MaterialTheme.colorScheme.onBackground,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            selectedBorderColor = Color.Transparent,
            borderWidth = 1.dp
        )
    )
}

@Composable
private fun GiftPackCard(pack: GiftPack, onClick: () -> Unit) {
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
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    pack.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pack.items.joinToString(" · ") { "${it.qty} × ${it.product.name}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatRupees(pack.toDraft().pricePerPack),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        " / pack",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Bulk Pack Builder: contents → quantity → personalization → ship-by → pay
// ---------------------------------------------------------------------

private val displayDate = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftPackBuilderScreen(
    packId: String?,
    initialOccasion: OccasionType? = null,
    initialPackCount: Int? = null,
    budgetPerPerson: Double? = null,
    reorderOf: Long? = null,
    onBack: () -> Unit,
    onInitiatePayment: (amount: Double, email: String, phone: String) -> Unit,
    onOrderPlaced: () -> Unit,
    viewModel: GiftPackBuilderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val draft by viewModel.draft.collectAsState()
    val packCountText by viewModel.packCountText.collectAsState()
    val personalization by viewModel.personalization.collectAsState()
    val shipByDate by viewModel.shipByDate.collectAsState()
    val address by viewModel.address.collectAsState()
    val total by viewModel.total.collectAsState()
    val shipByCapacity by viewModel.shipByCapacity.collectAsState()
    val paymentPlan by viewModel.paymentPlan.collectAsState()
    val payChoice by viewModel.payChoice.collectAsState()
    val customPayText by viewModel.customPayText.collectAsState()
    val occasion by viewModel.occasion.collectAsState()
    val logoUri by viewModel.logoUri.collectAsState()
    val logoNotes by viewModel.logoNotes.collectAsState()
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.logoUri.value = uri
    }
    val giftingProducts by viewModel.giftingProducts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var placedOrderId by remember { mutableStateOf<Long?>(null) }
    var placedWithCarriedProof by remember { mutableStateOf(false) }
    val reorderSource by viewModel.reorderSource.collectAsState()
    val proofWillCarryOver by viewModel.proofWillCarryOver.collectAsState()

    LaunchedEffect(packId, reorderOf) {
        viewModel.load(packId, initialOccasion, initialPackCount, budgetPerPerson, reorderOf)
    }
    val budget by viewModel.budgetPerPerson.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GiftPackBuilderViewModel.BuilderEvent.LaunchPayment ->
                    onInitiatePayment(event.amount, event.email, event.phone)
                is GiftPackBuilderViewModel.BuilderEvent.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
                is GiftPackBuilderViewModel.BuilderEvent.OrderPlaced -> {
                    placedWithCarriedProof = event.proofCarriedOver
                    placedOrderId = event.orderId
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            reorderOf != null -> "Reorder #$reorderOf"
                            packId == null -> "Build your pack"
                            else -> "Customize pack"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Pay now", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(
                            paymentPlan.payNow?.let(::formatRupees) ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text("of ${formatRupees(total)} total", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Button(onClick = { viewModel.checkout() }, enabled = !isProcessing && !isLoading) {
                        if (isProcessing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Pay & place order")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            reorderSource?.let { source ->
                ReorderBanner(source.orderId, source.pricePerPackAtOrder, draft.pricePerPack)
                Spacer(Modifier.height(16.dp))
            }
            Text("Occasion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.selectableOccasions.forEach { option ->
                    FilterChip(
                        selected = occasion == option,
                        onClick = { viewModel.occasion.value = option },
                        label = { Text(option.label) }
                    )
                }
            }

            if (occasion == OccasionType.CORPORATE) {
                Spacer(Modifier.height(16.dp))
                CorporateLogoSection(
                    logoUri = logoUri,
                    reusedLogoPath = reorderSource?.logoPath?.takeIf { logoUri == null },
                    reusedFromOrderId = reorderSource?.orderId,
                    proofWillCarryOver = proofWillCarryOver,
                    resolveUrl = viewModel::imageUrl,
                    notes = logoNotes,
                    onPick = {
                        logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onNotesChange = { viewModel.logoNotes.value = it.take(200) }
                )
            }

            SectionTitle("1. What's in each pack")
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::rename,
                label = { Text("Pack name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            PackContentsEditor(
                lines = draft.lines,
                onQtyChange = viewModel::setQtyPerPack,
                onAddItemClick = { showPicker = true }
            )
            Text(
                "Price per pack: ${formatRupees(draft.pricePerPack)}",
                fontWeight = FontWeight.SemiBold
            )
            budget?.takeIf { occasion == OccasionType.CORPORATE }?.let { perPerson ->
                val delta = perPerson - draft.pricePerPack
                Text(
                    "Budget ${formatRupees(perPerson)} per person · " +
                        if (delta >= -0.005) "${formatRupees(delta)} under budget" else "${formatRupees(-delta)} over budget",
                    color = if (delta >= -0.005) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            SectionTitle("2. How many packs")
            OutlinedTextField(
                value = packCountText,
                onValueChange = { viewModel.packCountText.value = it.filter(Char::isDigit).take(6) },
                label = { Text("Number of packs") },
                supportingText = { Text("Minimum ${GiftingRules.MIN_PACK_COUNT} packs") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("3. Personalization (optional)")
            OutlinedTextField(
                value = personalization,
                onValueChange = { viewModel.personalization.value = it.take(GiftingRules.MAX_PERSONALIZATION_LENGTH) },
                label = { Text("Name, initials or date to engrave") },
                placeholder = {
                    Text(
                        if (occasion == OccasionType.WEDDING) "e.g. Rahul ♥ Priya · 12.02.2027"
                        else "e.g. Sharma Family · Griha Pravesh 2027"
                    )
                },
                supportingText = { Text("${personalization.length}/${GiftingRules.MAX_PERSONALIZATION_LENGTH}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("4. Ship-by date")
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(shipByDate?.format(displayDate) ?: "Choose the date you need it shipped by")
            }
            ShipByCapacityNote(shipByCapacity, onUseDate = { viewModel.shipByDate.value = it })

            SectionTitle("5. Delivery address")
            OutlinedTextField(
                value = address,
                onValueChange = { viewModel.address.value = it },
                label = { Text("Address") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("6. Payment")
            PaymentSection(
                plan = paymentPlan,
                choice = payChoice,
                customText = customPayText,
                onChoice = { viewModel.payChoice.value = it },
                onCustomText = { viewModel.customPayText.value = it }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPicker) {
        GiftProductPickerDialog(
            products = giftingProducts,
            onPick = { viewModel.addProduct(it); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }

    if (showDatePicker) {
        ShipByDatePickerDialog(
            initial = shipByDate,
            onPicked = { viewModel.shipByDate.value = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }

    placedOrderId?.let { orderId ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(onClick = { placedOrderId = null; onOrderPlaced() }) { Text("Done") }
            },
            title = { Text("Order placed") },
            text = {
                Text(
                    "Your gifting order #$orderId is confirmed. " +
                        (if (placedWithCarriedProof) "Your approved logo proof was reused, so it can go straight into production. " else "") +
                        "You can see what's paid and pay any balance under Bulk Gifting → My orders."
                )
            }
        )
    }
}

@Composable
private fun ReorderBanner(sourceOrderId: Long, lastPricePerPack: Double, todayPricePerPack: Double) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text("Repeating order #$sourceOrderId", fontWeight = FontWeight.Bold)
            Text(
                "Same items, headcount and logo as last time — at today's prices. Pick a new ship-by date below.",
                style = MaterialTheme.typography.bodySmall
            )
            if (kotlin.math.abs(todayPricePerPack - lastPricePerPack) > 0.005) {
                Text(
                    "Price per pack: ${formatRupees(lastPricePerPack)} last time → ${formatRupees(todayPricePerPack)} today",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CorporateLogoSection(
    logoUri: android.net.Uri?,
    reusedLogoPath: String?,
    reusedFromOrderId: Long?,
    proofWillCarryOver: Boolean?,
    resolveUrl: suspend (String) -> String?,
    notes: String,
    onPick: () -> Unit,
    onNotesChange: (String) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Company logo", fontWeight = FontWeight.Bold)
            Text(
                "Our team will send you a proof showing exactly how your logo will look. " +
                    "Production starts only after you approve it.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logoUri != null) {
                    AsyncImage(
                        model = logoUri,
                        contentDescription = "Selected logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                } else if (reusedLogoPath != null) {
                    PrivateImage(reusedLogoPath, resolveUrl, Modifier.size(64.dp))
                    Spacer(Modifier.width(12.dp))
                }
                OutlinedButton(onClick = onPick) {
                    Text(
                        when {
                            logoUri != null -> "Change logo"
                            reusedLogoPath != null -> "Use a different logo"
                            else -> "Upload logo (PNG / JPG)"
                        }
                    )
                }
            }
            if (logoUri == null && reusedLogoPath != null) {
                Text("Using your logo from order #$reusedFromOrderId", style = MaterialTheme.typography.bodySmall)
            }
            when (proofWillCarryOver) {
                true -> Text(
                    "✓ Your approved logo proof will be reused — no new approval needed.",
                    color = Color(0xFF2E7D32),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                false -> Text(
                    "Our team will send a new logo proof for your approval before production " +
                        "(proofs are reused only when the logo and items are unchanged and were approved last time).",
                    color = Color(0xFFEF6C00),
                    style = MaterialTheme.typography.bodySmall
                )
                null -> Unit
            }
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text("Where should the logo go? (optional)") },
                placeholder = { Text("e.g. Engraved on the bottle, gold foil on the box lid") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PaymentSection(
    plan: PaymentPlan,
    choice: PayChoice,
    customText: String,
    onChoice: (PayChoice) -> Unit,
    onCustomText: (String) -> Unit
) {
    if (plan.fullRequired) {
        Text(
            "The ship-by date is too close for a later balance payment, so the full " +
                "${formatRupees(plan.total)} is due now.",
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    @Composable
    fun Option(value: PayChoice, title: String, subtitle: String) {
        Row(
            Modifier.fillMaxWidth().clickable { onChoice(value) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = choice == value, onClick = { onChoice(value) })
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }

    val balanceNote = plan.balanceDueDate
        ?.let { "Balance due by ${it.format(displayDate)}" }
        ?: "Balance due ${GiftingRules.BALANCE_DUE_DAYS_BEFORE_SHIP} days before the ship-by date"

    Option(
        PayChoice.MIN_ADVANCE,
        "Pay ${GiftingRules.MIN_ADVANCE_PERCENT}% advance · ${formatRupees(plan.minAdvance)}",
        balanceNote
    )
    Option(PayChoice.FULL, "Pay in full · ${formatRupees(plan.total)}", "Nothing left to pay later")
    Option(PayChoice.CUSTOM, "Pay a different amount", "At least ${formatRupees(plan.minAdvance)}. $balanceNote")
    if (choice == PayChoice.CUSTOM) {
        OutlinedTextField(
            value = customText,
            onValueChange = { text -> onCustomText(text.filter { it.isDigit() || it == '.' }.take(10)) },
            label = { Text("Amount to pay now (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(start = 48.dp)
        )
    }
}

@Composable
private fun ShipByCapacityNote(capacity: ShipByCapacity, onUseDate: (LocalDate) -> Unit) {
    when (capacity) {
        ShipByCapacity.NotChecked -> Unit
        ShipByCapacity.Available -> Text(
            "✓ The workshop has capacity to finish your packs before this date",
            color = Color(0xFF2E7D32),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )
        ShipByCapacity.Unknown -> Text(
            "Couldn't check workshop capacity right now — we'll check again when you pay.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )
        is ShipByCapacity.Conflict -> Column(Modifier.padding(top = 6.dp)) {
            Text(
                "✗ " + capacityConflictMessage(capacity),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            capacity.earliestAvailable?.let { earliest ->
                TextButton(onClick = { onUseDate(earliest) }) {
                    Text("Use ${earliest.format(displayDate)}")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShipByDatePickerDialog(
    initial: LocalDate?,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    // DatePicker works in UTC millis at midnight.
    val tomorrowUtc = LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= tomorrowUtc
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = state.selectedDateMillis != null
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}
