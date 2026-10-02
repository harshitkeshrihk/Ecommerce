package com.example.vishnu.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.vishnu.uicomponents.BrandTopBar
import com.example.vishnu.uicomponents.StoreSection
import com.example.vishnu.uicomponents.StoreSectionTabs
import com.example.vishnu.utils.formatRupees
import com.example.vishnu.viewModels.WholesaleAccess
import com.example.vishnu.viewModels.WholesaleProduct
import com.example.vishnu.viewModels.WholesaleViewModel

// ---------------------------------------------------------------------
// Wholesale section: open to every signed-in user. Ordering at slab prices
// is limited to approved business accounts (QuickOrderViewModel and the
// orders insert policy in supabase/011_unified_wholesale.sql).
// ---------------------------------------------------------------------

@Composable
fun WholesaleScreen(
    onBack: () -> Unit,
    onGiftingClick: () -> Unit,
    onCorporateClick: () -> Unit,
    onQuickOrderClick: () -> Unit,
    onRequestQuoteClick: () -> Unit,
    onProductClick: (productId: String) -> Unit,
    onApplyClick: () -> Unit,
    viewModel: WholesaleViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsState()
    val access by viewModel.access.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BrandTopBar(
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationDescription = "Back",
                onNavigationClick = onBack,
                actionIcon = Icons.Outlined.RequestQuote,
                actionDescription = "My quote requests",
                onActionClick = onRequestQuoteClick
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = onRequestQuoteClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Request a Quote (RFQ)", style = MaterialTheme.typography.titleMedium)
                }
            }
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
                    selected = StoreSection.WHOLESALE,
                    onSectionClick = { section ->
                        when (section) {
                            StoreSection.ALL -> onBack()
                            StoreSection.GIFTING -> onGiftingClick()
                            StoreSection.CORPORATE -> onCorporateClick()
                            StoreSection.WHOLESALE -> Unit
                        }
                    }
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 4.dp)) {
                    Text("Wholesale Ordering", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Bulk prices that drop as your quantity grows",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            access?.let { current ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WholesaleAccessBanner(access = current, onApplyClick = onApplyClick)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                WholesaleOptionCard(
                    icon = Icons.Outlined.GridView,
                    title = "Quick Order Pad",
                    subtitle = "Order by SKU & quantities",
                    onClick = onQuickOrderClick
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Minimum Order Quantity (MOQ) Slabs",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            when {
                isLoading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                products.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No MOQ slabs yet. Use Request a Quote for bulk pricing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> items(products, key = { it.product.id }) { item ->
                    MoqSlabCard(item = item, onClick = { onProductClick(item.product.id) })
                }
            }
        }
    }
}

@Composable
private fun WholesaleAccessBanner(access: WholesaleAccess, onApplyClick: () -> Unit) {
    val (icon, text) = when (access) {
        WholesaleAccess.BUSINESS -> Icons.Outlined.Verified to "Business account: slab prices apply to your Quick Orders."
        WholesaleAccess.KYC_PENDING -> Icons.Outlined.HourglassTop to
            "Your business account is under review. You can send quote requests meanwhile."
        WholesaleAccess.NONE -> Icons.Outlined.Storefront to
            "Slab prices are for approved business accounts. Anyone can request a quote."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            )
            if (access == WholesaleAccess.NONE) {
                TextButton(onClick = onApplyClick) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun WholesaleOptionCard(
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

@Composable
private fun MoqSlabCard(item: WholesaleProduct, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            AsyncImage(
                model = item.product.imageUrl,
                contentDescription = item.product.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    item.product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                item.slabs.take(3).forEach { slab ->
                    Text(
                        "MOQ: ${slab.minQty} ${item.product.unitOfMeasure}s | ${formatRupees(slab.pricePerUnit)} / unit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
