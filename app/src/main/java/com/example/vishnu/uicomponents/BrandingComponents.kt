package com.example.vishnu.uicomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.vishnu.model.BrandAsset
import com.example.vishnu.model.GiftingOrder
import com.example.vishnu.model.ProofStatus

/** Image from the private brand-assets bucket, shown via a short-lived signed URL. Tap to enlarge. */
@Composable
fun PrivateImage(
    path: String,
    resolveUrl: suspend (String) -> String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var url by remember(path) { mutableStateOf<String?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    var enlarged by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        url = resolveUrl(path)
        failed = url == null
    }

    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF1F1F1))
            .clickable(enabled = url != null) { enlarged = true },
        contentAlignment = Alignment.Center
    ) {
        when {
            url != null -> AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
            failed -> Text("Image unavailable", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            else -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }

    if (enlarged && url != null) {
        Dialog(onDismissRequest = { enlarged = false }) {
            Surface(shape = RoundedCornerShape(12.dp)) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).clickable { enlarged = false }.padding(8.dp)
                )
            }
        }
    }
}

/** One-line label for where a corporate order is in proofing. */
fun proofStatusLabel(order: GiftingOrder): String = when (order.proofStatus) {
    ProofStatus.NOT_REQUIRED -> ""
    ProofStatus.AWAITING_PROOF -> "Awaiting logo proof from the team"
    ProofStatus.IN_REVIEW -> "Proof v${order.latestProof?.version} sent — awaiting customer approval"
    ProofStatus.REVISION_REQUESTED -> "Customer requested changes to proof v${order.latestProof?.version}"
    ProofStatus.APPROVED -> "✓ Logo approved (v${order.proofs.first { it.status == BrandAsset.APPROVED }.version})"
}

@Composable
fun proofStatusColor(status: ProofStatus): Color = when (status) {
    ProofStatus.APPROVED -> Color(0xFF2E7D32)
    ProofStatus.NOT_REQUIRED -> Color.Unspecified
    else -> Color(0xFFEF6C00)
}

/** Proof history, newest first: version, status, notes/comments. */
@Composable
fun ProofHistory(order: GiftingOrder, resolveUrl: suspend (String) -> String?) {
    order.proofs.sortedByDescending { it.version }.forEach { proof ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
            PrivateImage(proof.proofPath, resolveUrl, Modifier.size(64.dp), ContentScale.Crop)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Proof v${proof.version} · " + when (proof.status) {
                        BrandAsset.APPROVED -> "Approved"
                        BrandAsset.REVISION_REQUESTED -> "Changes requested"
                        else -> "Awaiting approval"
                    },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
                proof.adminNote?.let { Text("Team: $it", style = MaterialTheme.typography.bodySmall) }
                proof.customerComment?.let { Text("Customer: $it", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            }
        }
    }
}
