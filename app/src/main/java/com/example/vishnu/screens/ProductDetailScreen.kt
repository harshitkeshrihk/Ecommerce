package com.example.vishnu.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.vishnu.viewModels.ProductDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    onBackClick: () -> Unit,
    onEditClick:() -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    // In a real app, we would fetch the product from a ViewModel using the ID.
    // For now, we'll just grab the first mock product to test the UI.
    val product by viewModel.product.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val isAdmin by viewModel.isAdmin.collectAsState()

    // 1. TRIGGER FETCH ON LAUNCH
    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    var isPlayingVideo by remember { mutableStateOf(false) }
    val exoPlayer = viewModel.player

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = onEditClick,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Product", tint = Color.White)
                }
            }
        },
        bottomBar = {
            // Sticky Bottom Bar for Action
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Price Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Total Price",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "₹${product?.priceRetail?.toInt()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Add Button
                    Button(
                        onClick = {
                            viewModel.addToCart(product)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Cart")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. HERO IMAGE & VIDEO SECTION
            Box( // <--- THIS BOX IS CRITICAL. It enables 'Alignment.Center/TopEnd'
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp) // Gives the box a fixed height
                    .background(Color.Black)
            ) {
                if (isPlayingVideo && product?.videoUrl != null) {

                    // 1. Tell ViewModel to load the video (Safely happens once)
                    LaunchedEffect(product?.videoUrl) {
                        if(product?.videoUrl!=null){
                            viewModel.initializePlayer(product?.videoUrl!!)
                        }
                    }

                    // 2. SHOW THE PLAYER (Connected to ViewModel)
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = viewModel.player // <--- The Magic: Using the injected player!
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Close Video Button (X) - Top Right
                    IconButton(
                        onClick = {
                            isPlayingVideo = false
                            viewModel.pause() // Good practice to pause when closing overlay
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                }  else {
                    // SHOW IMAGE (Original Code)
                    AsyncImage(
                        model = product?.imageUrl,
                        contentDescription = product?.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Play Button Overlay
                    if (product?.videoUrl != null) {
                        Button(
                            onClick = { isPlayingVideo = true }, // <--- Action!
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black.copy(
                                    alpha = 0.6f
                                )
                            ),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .border(1.dp, Color.White, RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Watch Clink Test", color = Color.White)
                        }
                    }
                }

                // Back Button (Always visible)
                if (!isPlayingVideo) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.TopStart)
                            .background(Color.White.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            }

            // 2. PRODUCT INFO
            Column(modifier = Modifier.padding(16.dp)) {
                // Name and Stock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    product?.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // The Trust Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Factory Direct • Verified Quality", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. WHOLESALE PRICING CARD
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Business Buying?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Unlock wholesale rates on 12+ pcs", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "₹${product?.priceWholesale?.toInt()}/pc",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. TECH SPECS TABLE
                Text("Technical Specifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                ProductSpecRow("Material", product?.material)
                Divider()
                ProductSpecRow("Gauge / Thickness", product?.gauge)
                Divider()
                ProductSpecRow("Weight", product?.weight)
                Divider()
                ProductSpecRow("Origin", "Made in India 🇮🇳")

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Description: This premium quality ${product?.name} is crafted from the finest ${product?.material}. Perfect for daily use or special occasions. Guaranteed durability.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun ProductSpecRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DetailPreview() {
    ProductDetailScreen(productId = "1", onBackClick = {},{})
}