package com.example.vishnu.screens

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.uicomponents.ActiveSearchBar
import com.example.vishnu.uicomponents.ProductItem
import com.example.vishnu.viewModels.CatalogViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CatalogScreen(
    onProductClick: (String) ->Unit,
    onProfileClick: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val products by viewModel.filteredProducts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val location by viewModel.userLocation.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // UI Logic: We are in "Search Mode" if the query is not empty
    val isSearching = searchQuery.isNotEmpty()

    // 1. Handle System Back Button
    // If searching, Back button clears search. If not, it does default action (exits app).
    BackHandler(enabled = isSearching) {
        viewModel.onSearchQueryChange("")
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



    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(bottom = 8.dp)
            ) {
                // --- 1. Header (Location & Profile) ---
                if (!isSearching) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFE91E63) // Pinkish Red pin
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = location.split(",").firstOrNull()?.trim() ?: "Home",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = onProfileClick) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFE91E63),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("V", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }else{
                    // Spacer to give search bar some room at the top when header is gone
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // --- 2. Search Bar Stub ---
                ActiveSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    isSearching = isSearching,
                    onBackClick = { viewModel.onSearchQueryChange("") } // Back arrow action
                )

                // --- 3. Dynamic Category Tabs ---
                if (!isSearching && categories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ScrollableTabRow(
                        selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        edgePadding = 16.dp,
                        indicator = { tabPositions ->
                            if (categories.contains(selectedCategory)) {
                                val index = categories.indexOf(selectedCategory)
                                TabRowDefaults.Indicator(
                                    Modifier.tabIndicatorOffset(tabPositions[index]),
                                    color = Color(0xFFE91E63), // Pink indicator
                                    height = 3.dp
                                )
                            }
                        },
                        divider = {} // No underline
                    ) {
                        categories.forEach { category ->
                            Tab(
                                selected = selectedCategory == category,
                                onClick = { viewModel.onCategorySelected(category) },
                                text = {
                                    Text(
                                        text = category,
                                        style = if(selectedCategory == category)
                                            MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                        else
                                            MaterialTheme.typography.bodyMedium
                                    )
                                },
                                selectedContentColor = Color(0xFFE91E63),
                                unselectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // --- 4. The Product Grid ---
        if (isLoading || products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
            ) {
                items(products.size) { index ->
                    // Assuming you have this composable from before
                    // Pass the whole product object to it
                    ProductItem(
                        product = products[index],
                        onProductClick = { onProductClick(products[index].id) },
                         onAddToCart = { viewModel.addToCart(products[index]) } // If you have this logic ready
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun CatalogPreview() {
}