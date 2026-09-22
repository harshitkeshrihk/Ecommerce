package com.example.vishnu.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.model.PricingTier
import com.example.vishnu.model.UserProfile
import com.example.vishnu.viewModels.KycQueueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KycQueueScreen(
    onBack: () -> Unit,
    viewModel: KycQueueViewModel = hiltViewModel()
) {
    val pending by viewModel.pendingRequests.collectAsState()
    val tiers by viewModel.pricingTiers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KYC Verification Queue") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (pending.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No pending applications.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
            ) {
                items(pending) { profile ->
                    KycRequestCard(
                        profile = profile,
                        tiers = tiers,
                        onApprove = { tierId -> viewModel.approve(profile, tierId) },
                        onReject = { reason -> viewModel.reject(profile, reason) }
                    )
                }
            }
        }
    }
}

@Composable
fun KycRequestCard(
    profile: UserProfile,
    tiers: List<PricingTier>,
    onApprove: (tierId: String) -> Unit,
    onReject: (reason: String) -> Unit
) {
    var selectedTierId by remember(tiers) { mutableStateOf(tiers.firstOrNull()?.id) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(profile.businessName ?: "Unnamed business", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Requested role: ${profile.requestedRole ?: "-"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("GSTIN: ${profile.gstin ?: "-"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("Applicant: ${profile.fullName ?: "-"} · ${profile.phoneNumber ?: "-"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            Spacer(Modifier.height(12.dp))

            if (tiers.isNotEmpty()) {
                Text("Assign pricing tier", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiers.forEach { tier ->
                        FilterChip(
                            selected = selectedTierId == tier.id,
                            onClick = { selectedTierId = tier.id },
                            label = { Text(tier.name) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { selectedTierId?.let(onApprove) },
                    enabled = selectedTierId != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Approve")
                }
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject")
                }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject application") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("Reason (shown to applicant)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (rejectReason.isNotBlank()) {
                            onReject(rejectReason)
                            showRejectDialog = false
                        }
                    }
                ) { Text("Reject") }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text("Cancel") }
            }
        )
    }
}
