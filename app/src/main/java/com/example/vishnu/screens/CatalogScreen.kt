package com.example.vishnu.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.vishnu.ui.theme.Forest
import com.example.vishnu.uicomponents.ActiveSearchBar
import com.example.vishnu.uicomponents.BottomTab
import com.example.vishnu.uicomponents.BrandTopBar
import com.example.vishnu.uicomponents.ProductItem
import com.example.vishnu.uicomponents.StoreBottomBar
import com.example.vishnu.uicomponents.StoreSection
import com.example.vishnu.uicomponents.StoreSectionTabs
import com.example.vishnu.viewModels.CatalogViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CatalogScreen(
    onProductClick: (String) ->Unit,
    onProfileClick: () -> Unit,
    onGiftingClick: () -> Unit,
    onCorporateClick: () -> Unit,
    onCartClick: () -> Unit,
    // Set when another screen (e.g. Cart's bottom bar) asks for a specific tab.
    requestedTab: BottomTab? = null,
    onRequestedTabHandled: () -> Unit = {},
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val products by viewModel.filteredProducts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val location by viewModel.userLocation.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val allProducts by viewModel.products.collectAsState()

    var activeTab by rememberSaveable { mutableStateOf(BottomTab.HOME) }
    LaunchedEffect(requestedTab) {
        if (requestedTab == BottomTab.HOME || requestedTab == BottomTab.SHOP) {
            activeTab = requestedTab
        }
        if (requestedTab != null) onRequestedTabHandled()
    }

    // UI Logic: We are in "Search Mode" if the query is not empty
    val isSearching = searchQuery.isNotEmpty()

    val context = LocalContext.current

    // 1. Handle System Back Button
    // If searching, Back button clears search. If not, it does default action (exits app).
    BackHandler(enabled = isSearching) {
        viewModel.onSearchQueryChange("")
    }
    // Back from the Shop tab returns to Home instead of leaving the app.
    BackHandler(enabled = activeTab == BottomTab.SHOP) {
        activeTab = BottomTab.HOME
    }

    val locationPermissionState = rememberPermissionState(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    LaunchedEffect(Unit) {
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        } else {
            viewModel.fetchLocation()
        }
    }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            viewModel.fetchLocation()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }



    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Header rows above the product list (location, sections, hero, category tabs).
    val productsStartIndex = if (isSearching) 0 else 4

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                BrandTopBar(
                    navigationIcon = Icons.Outlined.Menu,
                    navigationDescription = "Profile",
                    onNavigationClick = onProfileClick,
                    actionIcon = Icons.Outlined.ShoppingCart,
                    actionDescription = "Go to Cart",
                    onActionClick = onCartClick
                )
                if (activeTab == BottomTab.HOME) {
                    ActiveSearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        isSearching = isSearching,
                        onBackClick = { viewModel.onSearchQueryChange("") } // Back arrow action
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        bottomBar = {
            StoreBottomBar(
                selected = activeTab,
                onTabClick = { tab ->
                    when (tab) {
                        BottomTab.HOME, BottomTab.SHOP -> activeTab = tab
                        BottomTab.CART -> onCartClick()
                        // Saved screen is not designed yet.
                        BottomTab.SAVED -> Unit
                    }
                }
            )
        }
    ) { paddingValues ->
        if (activeTab == BottomTab.SHOP) {
            ShopContent(
                products = allProducts,
                isLoading = isLoading,
                onProductClick = onProductClick,
                onAddToCart = { viewModel.addToCart(it) },
                modifier = Modifier.padding(paddingValues)
            )
            return@Scaffold
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = GridGutter, end = GridGutter, bottom = 16.dp),
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            if (!isSearching) {
                // --- 1. Delivery location ---
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Deliver to $location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // --- 2. Store sections ---
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StoreSectionTabs(
                        selected = StoreSection.ALL,
                        onSectionClick = { section ->
                            when (section) {
                                StoreSection.GIFTING -> onGiftingClick()
                                StoreSection.CORPORATE -> onCorporateClick()
                                // Wholesale UI is not designed yet.
                                StoreSection.ALL, StoreSection.WHOLESALE -> Unit
                            }
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                    )
                }

                // --- 3. Hero banner (full bleed) ---
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HeroBanner(
                        imageUrl = products.firstOrNull { it.isBestseller }?.imageUrl
                            ?: products.firstOrNull()?.imageUrl,
                        onShopNowClick = {
                            scope.launch { gridState.animateScrollToItem(productsStartIndex) }
                        },
                        onExploreClick = { /* Collection screen not designed yet */ },
                        modifier = Modifier.fullBleed(GridGutter)
                    )
                }

                // --- 4. Dynamic Category Tabs ---
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (categories.isNotEmpty()) {
                        CategoryTabs(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onCategorySelected = { viewModel.onCategorySelected(it) },
                            modifier = Modifier
                                .fullBleed(GridGutter)
                                .padding(bottom = 8.dp)
                        )
                    }
                }
            }

            // --- 5. The Product Grid ---
            when {
                isLoading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                products.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Choose Icon & Text based on mode
                        val icon = if (isSearching) Icons.Default.SearchOff else Icons.Default.Inventory2
                        val message = if (isSearching) {
                            "No items found matching '$searchQuery'"
                        } else {
                            "No items available in '$selectedCategory'"
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> items(products.size) { index ->
                    ProductItem(
                        product = products[index],
                        onProductClick = { onProductClick(products[index].id) },
                        onAddToCart = { viewModel.addToCart(products[index]) }
                    )
                }
            }
        }
    }
}

private val GridGutter = 10.dp

/** Lets a grid header ignore the grid's horizontal content padding and span edge to edge. */
private fun Modifier.fullBleed(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = gutter.roundToPx()
    val width = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-extra, 0)
    }
}

@Composable
private fun HeroBanner(
    imageUrl: String?,
    onShopNowClick: () -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Forest)
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.55f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DESIGNED FOR DAILY RITUALS",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Beautiful Kitchen",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Timeless essentials crafted to make every meal feel special.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onShopNowClick,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Forest,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("SHOP NOW", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp)
                }
                Button(
                    onClick = onExploreClick,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Forest
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("EXPLORE COLLECTION", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = categories.indexOf(selectedCategory)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceAtLeast(0),
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        edgePadding = 12.dp,
        indicator = { tabPositions ->
            if (selectedIndex >= 0) {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    height = 2.dp
                )
            }
        },
        divider = {}
    ) {
        categories.forEach { category ->
            val isSelected = selectedCategory == category
            Tab(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                text = {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
        }
    }
}

@Preview
@Composable
fun CatalogPreview() {
}