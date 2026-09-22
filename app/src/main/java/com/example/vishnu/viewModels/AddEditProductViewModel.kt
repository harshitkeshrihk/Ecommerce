package com.example.vishnu.viewModels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.MoqSlab
import com.example.vishnu.model.Product
import com.example.vishnu.repository.PricingRepository
import com.example.vishnu.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddEditProductViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val pricingRepository: PricingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Form States
    var name = MutableStateFlow("")
    var material = MutableStateFlow("")
    var weight = MutableStateFlow("")
    var price = MutableStateFlow("") // String for easy text field handling
    var wholesalePrice = MutableStateFlow("") // Phase 1 — base price MOQ slabs override
    var unitOfMeasure = MutableStateFlow("piece") // piece | dozen | kg | set
    var stock = MutableStateFlow("")
    var category = MutableStateFlow("General")
    var imageUrl = MutableStateFlow("")
    var isAvailable = MutableStateFlow(true)

    var selectedImageUri = MutableStateFlow<Uri?>(null)

    // Phase 1 — MOQ slabs (only editable once the product has an id, i.e. Edit mode)
    private val _moqSlabs = MutableStateFlow<List<MoqSlab>>(emptyList())
    val moqSlabs = _moqSlabs.asStateFlow()

    // Internal State
    private var currentProductId: String? = null // Null = New Product
    private var currentStoreId: String? = null
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _saveStatus = MutableStateFlow<Boolean?>(null) // True=Success, False=Fail
    val saveStatus = _saveStatus.asStateFlow()

    fun setStoreId(id: String) {
        this.currentStoreId = id
    }

    fun addMoqSlab(minQty: Int, pricePerUnit: Double) {
        val productId = currentProductId ?: return
        viewModelScope.launch {
            if (pricingRepository.addSlab(productId, minQty, pricePerUnit)) {
                _moqSlabs.value = pricingRepository.getSlabsForProduct(productId)
            }
        }
    }

    fun deleteMoqSlab(slabId: String) {
        val productId = currentProductId ?: return
        viewModelScope.launch {
            if (pricingRepository.deleteSlab(slabId)) {
                _moqSlabs.value = pricingRepository.getSlabsForProduct(productId)
            }
        }
    }

    // Load data if editing an existing product
    fun loadProduct(productId: String?) {
        if (productId == null) return // Add Mode: Do nothing

        currentProductId = productId
        viewModelScope.launch {
            _isLoading.value = true
            val product = repository.getProductById(productId)
            product?.let {

                currentStoreId = it.storeId

                name.value = it.name
                material.value = it.material ?: ""
                weight.value = it.weight ?: ""
                price.value = it.priceRetail?.toString() ?: ""
                wholesalePrice.value = it.priceWholesale.toString()
                unitOfMeasure.value = it.unitOfMeasure
                stock.value = it.stockCount?.toString() ?: "0"
                category.value = it.category ?: "General"
                imageUrl.value = it.imageUrl ?: ""
                isAvailable.value = it.isAvailable ?: true
            }
            _moqSlabs.value = pricingRepository.getSlabsForProduct(productId)
            _isLoading.value = false
        }
    }

    fun saveProduct() {
        if(currentStoreId == null){
            _saveStatus.value = false
            return
        }
        viewModelScope.launch {
            _isLoading.value = true

            var finalImageUrl = imageUrl.value // Start with existing URL
            val uri = selectedImageUri.value

            if (uri != null) {
                try {
                    val contentResolver = context.contentResolver
                    // Read file bytes from the URI
                    val imageBytes = contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    }

                    if (imageBytes != null) {
                        val uploadedUrl = repository.uploadProductImage(imageBytes)
                        if (uploadedUrl != null) {
                            finalImageUrl = uploadedUrl
                        } else {
                            // Upload failed (Network error, etc.)
                            _isLoading.value = false
                            _saveStatus.value = false
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    _isLoading.value = false
                    _saveStatus.value = false
                    return@launch
                }
            }

            // Validation: Image is mandatory
            if (finalImageUrl.isBlank()) {
                _isLoading.value = false
                // Optional: Emit a specific error message state here
                return@launch
            }

            val priceDouble = price.value.toDoubleOrNull() ?: 0.0
            val stockInt = stock.value.toIntOrNull() ?: 0

            // If new, generate ID. If edit, use existing ID.
            val idToUse = currentProductId ?: UUID.randomUUID().toString()

            val productToSave = Product(
                id = idToUse,
                name = name.value,
                material = material.value,
                weight = weight.value,
                priceRetail = priceDouble,
                stockCount = stockInt,
                category = category.value,
                imageUrl = finalImageUrl,
                isAvailable = isAvailable.value,
                gauge = "22",
                subcategory = "subcategory",
                priceWholesale = wholesalePrice.value.toDoubleOrNull() ?: 0.0,
                unitOfMeasure = unitOfMeasure.value,
                videoUrl = null,
                isBestseller = false,
                createdAt = Instant.now().toString(),
                storeId = currentStoreId!!
            )

            val success = repository.upsertProduct(productToSave)
            _saveStatus.value = success
            _isLoading.value = false
        }
    }

    fun resetStatus() { _saveStatus.value = null }
}