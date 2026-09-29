package com.example.vishnu.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bulk Gifting")
                        Text(
                            "Return gifts for weddings, functions & festivals",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = onMyOrdersClick) { Text("My orders") }
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
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onBuildOwnClick() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Build your own pack", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Choose any items from the gifting collection",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onCorporateClick() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Business, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Corporate gifting", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Enter a budget per person and get gift options with your logo",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            item {
                Text("Ready-made packs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTier == null,
                        onClick = { viewModel.selectTier(null) },
                        label = { Text("All budgets") }
                    )
                    GiftingRules.BUDGET_TIERS.forEach { tier ->
                        FilterChip(
                            selected = selectedTier == tier,
                            onClick = { viewModel.selectTier(tier) },
                            label = { Text(tier.label) }
                        )
                    }
                }
            }
            when {
                isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                packs.isEmpty() -> item {
                    Text(
                        "No ready-made packs in this budget yet — try building your own.",
                        color = Color.Gray,
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
private fun GiftPackCard(pack: GiftPack, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${formatRupees(pack.toDraft().pricePerPack)} / pack",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            pack.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                pack.items.joinToString(" · ") { "${it.qty} × ${it.product.name}" },
                style = MaterialTheme.typography.bodySmall
            )
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

    LaunchedEffect(packId) { viewModel.load(packId, initialOccasion, initialPackCount, budgetPerPerson) }
    val budget by viewModel.budgetPerPerson.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GiftPackBuilderViewModel.BuilderEvent.LaunchPayment ->
                    onInitiatePayment(event.amount, event.email, event.phone)
                is GiftPackBuilderViewModel.BuilderEvent.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
                is GiftPackBuilderViewModel.BuilderEvent.OrderPlaced ->
                    placedOrderId = event.orderId
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (packId == null) "Build your pack" else "Customize pack") },
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
                        "You can see what's paid and pay any balance under Bulk Gifting → My orders."
                )
            }
        )
    }
}

@Composable
private fun CorporateLogoSection(
    logoUri: android.net.Uri?,
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
                }
                OutlinedButton(onClick = onPick) {
                    Text(if (logoUri == null) "Upload logo (PNG / JPG)" else "Change logo")
                }
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
