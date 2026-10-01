package com.example.vishnu.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.vishnu.model.CartItem
import com.example.vishnu.model.Product
import com.example.vishnu.uicomponents.BottomTab
import com.example.vishnu.uicomponents.StoreBottomBar
import com.example.vishnu.utils.formatRupees
import com.example.vishnu.viewModels.CartViewModel
import com.example.vishnu.viewModels.CatalogViewModel

// Tax and shipping are display-only placeholders for now: the amount charged is
// still CartViewModel.totalPrice, unchanged.
private const val TAX_AMOUNT = 0.0
private const val SHIPPING_AMOUNT = 0.0

@Composable
fun CartScreen(
    onBackClick: () -> Unit,
    onInitiatePayment: (amount: Double, email: String, phone: String) -> Unit,
    onProductClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    onShopClick: () -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
    // Read-only: supplies the product list for "You might also like".
    catalogViewModel: CatalogViewModel = hiltViewModel()
) {
    val cartItems by viewModel.cartItems.collectAsState()
    val totalPrice by viewModel.totalPrice.collectAsState()
    val email by viewModel.userEmail.collectAsState()
    val phone by viewModel.userPhone.collectAsState()
    val allProducts by catalogViewModel.products.collectAsState()
    val context = LocalContext.current

    val cartEvent = viewModel.cartEvent.collectAsState(initial = null)

    LaunchedEffect(cartEvent.value) {
        when(val event = cartEvent.value) {
            is CartViewModel.CartEvent.OrderPlacedSuccess -> {
//                navController.navigate("order_success_screen") {
//                    popUpTo("cart") { inclusive = true }
//                }
            }
            is CartViewModel.CartEvent.OrderFailed -> {
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
            null -> {} // Do nothing
        }
    }

    // Suggestions: available products not already in the cart, same categories first.
    val suggestions = remember(cartItems, allProducts) {
        val inCart = cartItems.map { it.product.id }.toSet()
        val cartCategories = cartItems.map { it.product.category }.toSet()
        allProducts
            .filter { it.id !in inCart && it.isAvailable }
            .sortedWith(
                compareByDescending<Product> { it.category in cartCategories }
                    .thenByDescending { it.isBestseller }
            )
            .take(8)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CartHeader(onBackClick = onBackClick) },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                if (cartItems.isNotEmpty()) {
                    CartSummary(
                        subtotal = totalPrice,
                        total = totalPrice,
                        onCheckout = { onInitiatePayment(totalPrice, email, phone) }
                    )
                }
                StoreBottomBar(
                    selected = BottomTab.CART,
                    onTabClick = { tab ->
                        when (tab) {
                            BottomTab.HOME -> onHomeClick()
                            BottomTab.SHOP -> onShopClick()
                            // Saved screen is not designed yet; Cart is this screen.
                            BottomTab.SAVED, BottomTab.CART -> Unit
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (cartItems.isEmpty()) {
            EmptyCartState(
                modifier = Modifier.padding(padding),
                onStartShopping = onBackClick
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(cartItems, key = { it.product.id }) { item ->
                    CartLineItem(
                        item = item,
                        onRemove = { viewModel.removeFromCart(item.product.id) },
                        onIncrease = { viewModel.increaseQty(item) },
                        onDecrease = { viewModel.decreaseQty(item) },
                        onItemClick = { onProductClick(item.product.id) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                if (suggestions.isNotEmpty()) {
                    item {
                        YouMightAlsoLike(
                            products = suggestions,
                            onProductClick = onProductClick,
                            onSeeAllClick = onShopClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartHeader(onBackClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .height(56.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onBackClick, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = "CART",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        )
    }
}

@Composable
private fun CartLineItem(
    item: CartItem,
    onRemove: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onItemClick: () -> Unit
) {
    val product = item.product
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp)
    ) {
        AsyncImage(
            model = product.imageUrl,
            contentDescription = product.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onItemClick() }
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onItemClick() }
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val details = listOfNotNull(product.material, product.gauge, product.weight)
                        .joinToString(" · ")
                    if (details.isNotEmpty()) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuantityStepper(item.quantity, onIncrease = onIncrease, onDecrease = onDecrease)
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatRupees(product.priceRetail * item.quantity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun QuantityStepper(
    quantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(32.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
    ) {
        IconButton(onClick = onDecrease, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
        }
        Text(
            text = "$quantity",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = 20.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onIncrease, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun YouMightAlsoLike(
    products: List<Product>,
    onProductClick: (String) -> Unit,
    onSeeAllClick: () -> Unit
) {
    Column(Modifier.padding(top = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "YOU MIGHT ALSO LIKE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "See all",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { onSeeAllClick() }
                    .padding(4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(products, key = { it.id }) { product ->
                Surface(
                    onClick = { onProductClick(product.id) },
                    modifier = Modifier.width(140.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 1.dp
                ) {
                    Column {
                        AsyncImage(
                            model = product.imageUrl,
                            contentDescription = product.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    product.name.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatRupees(product.priceRetail),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Outlined.NorthEast,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartSummary(
    subtotal: Double,
    total: Double,
    onCheckout: () -> Unit
) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
        SummaryRow("Subtotal", formatRupees(subtotal))
        SummaryRow("Tax", formatRupees(TAX_AMOUNT))
        SummaryRow("Shipping", if (SHIPPING_AMOUNT == 0.0) "Free" else formatRupees(SHIPPING_AMOUNT))
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TOTAL",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(formatRupees(total), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onCheckout,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("PROCEED TO CHECKOUT", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyCartState(
    modifier: Modifier = Modifier,
    onStartShopping: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.ShoppingBag,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Your cart is empty", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Looks like you haven't added anything yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = onStartShopping,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground)
            ) {
                Text(
                    "START SHOPPING",
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
