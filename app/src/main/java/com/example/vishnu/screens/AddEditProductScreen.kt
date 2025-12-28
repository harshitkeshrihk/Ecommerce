package com.example.vishnu.screens

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.vishnu.viewModels.AddEditProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    productId: String? = null,
    storeId: String? = null, // Pass null to Add, ID to Edit
    onBack: () -> Unit,
    viewModel: AddEditProductViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Collect Form Data
    val name by viewModel.name.collectAsState()
    val price by viewModel.price.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val material by viewModel.material.collectAsState()
    val weight by viewModel.weight.collectAsState()
    val imageUrl by viewModel.imageUrl.collectAsState()
    val isAvailable by viewModel.isAvailable.collectAsState()


    val isLoading by viewModel.isLoading.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()

    val selectedUri by viewModel.selectedImageUri.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.selectedImageUri.value = uri // Send to ViewModel
    }

    LaunchedEffect(Unit) {
        // 1. If we have a Store ID (Add Mode), tell the ViewModel
        if (storeId != null) {
            viewModel.setStoreId(storeId)
        }
        // 2. Load Product Data (Edit Mode)
        viewModel.loadProduct(productId)
    }

    // Handle Save Result
    LaunchedEffect(saveStatus) {
        if (saveStatus == true) {
            Toast.makeText(context, "Product Saved Successfully!", Toast.LENGTH_SHORT).show()
            onBack() // Exit screen on success
            viewModel.resetStatus()
        } else if (saveStatus == false) {
            Toast.makeText(context, "Failed to save product", Toast.LENGTH_SHORT).show()
            viewModel.resetStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (productId == null) "Add New Product" else "Edit Product") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    // Save Button in App Bar
                    IconButton(onClick = { viewModel.saveProduct() }) {
                        Icon(Icons.Default.Check, "Save")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()), // Make it scrollable
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Image Preview (Optional but helpful) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            // Open Gallery
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedUri != null) {
                        // Priority 1: Show newly picked local image
                        AsyncImage(
                            model = selectedUri,
                            contentDescription = "Selected Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (imageUrl.isNotEmpty()) {
                        // Priority 2: Show existing remote image (Edit Mode)
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Current Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Priority 3: Show Placeholder
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tap to add image", color = Color.Gray)
                        }
                    }
                }

                // --- Form Fields ---
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.name.value = it },
                    label = { Text("Product Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { viewModel.price.value = it },
                        label = { Text("Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { viewModel.stock.value = it },
                        label = { Text("Stock Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = material,
                        onValueChange = { viewModel.material.value = it },
                        label = { Text("Material") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { viewModel.weight.value = it },
                        label = { Text("Weight") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // --- Availability Switch ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Available for Sale?", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = isAvailable,
                        onCheckedChange = { viewModel.isAvailable.value = it }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Big Save Button (Alternative to Top Bar)
                Button(
                    onClick = { viewModel.saveProduct() },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Save Product")
                }
            }
        }
    }
}