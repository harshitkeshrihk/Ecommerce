import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- 1. Data Models ---
// Using a simplified model for the preview, mapping to your actual 'Store' class
data class StoreUiModel(
    val id: String,
    val name: String,
    val type: String, // e.g., "PHARMACY", "GROCERY", "MALL"
    val isActive: Boolean = true
)

// --- 2. Custom Color Palette (Modern & Premium) ---
private val ActiveTabContainer = Color(0xFF9777E7) // Deep Charcoal / Almost Black
private val ActiveTabContent = Color(0xFFFFFFFF)   // Pure White
private val InactiveTabContainer = Color(0xFFFFFFFF) // Pure White
private val InactiveTabContent = Color(0xFFAD92F1)   // Dark Grey
private val InactiveBorder = Color(0xFFE0E2EC)       // Soft Grey Border
private val AccentDot = Color(0xFF29B6F6)            // Cyan Accent for "Active" indicator





// --- 3. The Main Tabs Composable ---
@Composable
fun StoreTabs(
    stores: List<StoreUiModel>,
    selectedStoreId: String?,
    onStoreSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp), // Breathing room top/bottom
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(stores) { store ->
            val isSelected = store.id == selectedStoreId

            TabItem(
                name = store.name,
                type = store.type,
                isSelected = isSelected,
                onClick = { onStoreSelected(store.id) }
            )
        }
    }
}

// --- 4. Individual Tab Item ---
@Composable
fun TabItem(
    name: String,
    type: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Animations for smooth transitions
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) ActiveTabContainer else InactiveTabContainer,
        animationSpec = tween(300),
        label = "color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) ActiveTabContent else InactiveTabContent,
        animationSpec = tween(300),
        label = "contentColor"
    )
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 6.dp else 0.dp,
        animationSpec = tween(300),
        label = "elevation"
    )

    // Helper to pick icon based on type (You can expand this logic)
    val icon = when(type.uppercase()) {
        "PHARMACY" -> Icons.Outlined.LocalPharmacy
        "GROCERY" -> Icons.Outlined.ShoppingCart
        else -> Icons.Default.Storefront
    }

    Surface(
        modifier = Modifier
            .height(48.dp) // Generous touch target
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp), // Fully rounded pill shape
        color = containerColor,
        border = if (isSelected) null else BorderStroke(1.dp, InactiveBorder),
        shadowElevation = elevation
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Icon Layout
            Box(
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    // Active State: Show a distinct check or dot
                    Icon(
                        imageVector = Icons.Default.LocalMall,
                        contentDescription = null,
                        tint = AccentDot, // Pop of Cyan on Dark background
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    // Inactive State: Show generic store icon
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text Layout
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                ),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

// --- 5. Integration Example (Preview) ---
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
fun PreviewCatalogScreenWithTabs() {
    // Dummy Data
    val dummyStores = listOf(
        StoreUiModel("1", "V-Mall", "MALL"),
        StoreUiModel("2", "OM Medicals", "PHARMACY"),
        StoreUiModel("3", "Fresh Mart", "GROCERY"),
        StoreUiModel("4", "Tech Zone", "ELECTRONICS")
    )

    var selectedStoreId by remember { mutableStateOf("1") }

    Scaffold(
        containerColor = Color(0xFFF5F5F5), // Light grey background like Zepto
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .padding(bottom = 8.dp)
            ) {
                // Fake Header Row
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Delivering to ",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Gray
                    )
                    Text(
                        "Home - Gurugram",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // --- THE CUSTOM TABS ---
                StoreTabs(
                    stores = dummyStores,
                    selectedStoreId = selectedStoreId,
                    onStoreSelected = { selectedStoreId = it }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Selected Store: ${dummyStores.find { it.id == selectedStoreId }?.name}",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Gray
            )
        }
    }
}