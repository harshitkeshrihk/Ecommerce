package com.example.vishnu.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.viewModels.AdminCapacitySettingsViewModel
import com.example.vishnu.viewModels.AdminProductionCalendarViewModel
import com.example.vishnu.viewModels.AtRiskOrder
import com.example.vishnu.viewModels.CalendarDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dayHeader = DateTimeFormatter.ofPattern("EEE, dd MMM")
private val fullDate = DateTimeFormatter.ofPattern("dd MMM yyyy")

// ---------------------------------------------------------------------
// Production calendar: planned load vs capacity, day by day
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProductionCalendarScreen(
    onBack: () -> Unit,
    onCapacitySettingsClick: () -> Unit,
    viewModel: AdminProductionCalendarViewModel = hiltViewModel()
) {
    val days by viewModel.days.collectAsState()
    val atRisk by viewModel.atRisk.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Re-plan whenever we return (e.g. from capacity settings).
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Production Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onCapacitySettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Capacity settings")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading && days.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { AtRiskBanner(atRisk) }
                items(days, key = { it.date.toString() }) { CalendarDayRow(it) }
            }
        }
    }
}

@Composable
private fun AtRiskBanner(atRisk: List<AtRiskOrder>) {
    if (atRisk.isEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), modifier = Modifier.fillMaxWidth()) {
            Text(
                "✓ Every open gifting order can be finished before its ship-by date.",
                color = Color(0xFF2E7D32),
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${atRisk.size} order${if (atRisk.size == 1) "" else "s"} will miss the ship-by date",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            atRisk.forEach { risk ->
                val o = risk.order
                Text(
                    "#${o.orderId} · ${o.packCount} × ${o.packName} · ship by ${LocalDate.parse(o.shipByDate).format(fullDate)} · " +
                        (risk.projectedFinish?.let { "finishes ${it.format(fullDate)}" } ?: "no capacity to finish"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                "Add capacity for the coming days (settings icon) or agree a later date with the customer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun CalendarDayRow(day: CalendarDay) {
    val closed = day.capacity == 0
    val utilisation = if (day.capacity > 0) day.planned.toFloat() / day.capacity else 0f

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (closed) Color(0xFFF1F1F1) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (closed) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (day.date == LocalDate.now()) "Today" else day.date.format(dayHeader),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    when {
                        closed -> "Closed"
                        else -> "${day.planned} / ${day.capacity} packs"
                    } + if (day.isOverridden) " · custom" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            if (!closed) {
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFE0E0E0))
                ) {
                    Box(
                        Modifier.fillMaxWidth(utilisation.coerceIn(0f, 1f)).fillMaxHeight()
                            .background(if (utilisation >= 1f) Color(0xFFEF6C00) else MaterialTheme.colorScheme.primary)
                    )
                }
            }
            day.work.forEach { (order, packs) ->
                Text(
                    "• $packs packs for #${order.orderId} (${order.packName})" +
                        if (order.canEnterProduction) "" else " · awaiting logo approval",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (order.canEnterProduction) Color.Unspecified else Color(0xFFEF6C00)
                )
            }
            day.due.forEach { order ->
                Text(
                    "⏰ Ship-by: #${order.orderId} · ${order.packCount} × ${order.packName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
// Capacity settings: default packs/day, weekly off days, date overrides
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminCapacitySettingsScreen(
    onBack: () -> Unit,
    viewModel: AdminCapacitySettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val defaultPacksText by viewModel.defaultPacksText.collectAsState()
    val closedWeekdays by viewModel.closedWeekdays.collectAsState()
    val overrides by viewModel.overrides.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showAddOverride by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workshop Capacity") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
            Text("Normal day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = defaultPacksText,
                onValueChange = { viewModel.defaultPacksText.value = it.filter(Char::isDigit).take(6) },
                label = { Text("Gift packs the workshop can finish per day") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Weekly off days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { dow ->
                    FilterChip(
                        selected = dow.value in closedWeekdays,
                        onClick = { viewModel.toggleWeekday(dow.value) },
                        label = { Text(dow.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                    )
                }
            }
            Button(onClick = { viewModel.saveSettings() }, modifier = Modifier.fillMaxWidth()) {
                Text("Save capacity")
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Specific dates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Holidays (0 packs) or days with extra staff. Overrides the normal day and weekly off days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                TextButton(onClick = { showAddOverride = true }) { Text("Add date") }
            }
            if (overrides.isEmpty()) {
                Text("No upcoming date overrides.", color = Color.Gray)
            }
            overrides.forEach { o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            runCatching { LocalDate.parse(o.day).format(fullDate) }.getOrDefault(o.day),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            (if (o.packs == 0) "Closed" else "${o.packs} packs") + (o.note?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = { viewModel.deleteOverride(o.day) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete override")
                    }
                }
            }
        }
    }

    if (showAddOverride) {
        AddOverrideDialog(
            onAdd = { date, packs, note -> viewModel.addOverride(date, packs, note); showAddOverride = false },
            onDismiss = { showAddOverride = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOverrideDialog(
    onAdd: (LocalDate, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val dateState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
        }
    )
    var packsText by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var pickingDate by remember { mutableStateOf(true) }

    if (pickingDate) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickingDate = false }, enabled = dateState.selectedDateMillis != null) {
                    Text("Next")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) { DatePicker(state = dateState) }
        return
    }

    val date = Instant.ofEpochMilli(dateState.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(fullDate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = packsText,
                    onValueChange = { packsText = it.filter(Char::isDigit).take(6) },
                    label = { Text("Packs that day (0 = closed)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(60) },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. Diwali holiday") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(date, packsText.toIntOrNull() ?: 0, note) },
                enabled = packsText.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
