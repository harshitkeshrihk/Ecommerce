package com.example.vishnu.viewModels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.Product
import com.example.vishnu.model.Store
import com.example.vishnu.repository.AddToCartResult
import com.example.vishnu.repository.CartRepository
import com.example.vishnu.repository.ProductRepository
import com.example.vishnu.utils.LocationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val cartRepository: CartRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    // 2. Loading State (To show a spinner while fetching)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userLocation = MutableStateFlow("Fetching location...")
    val userLocation = _userLocation.asStateFlow()

    // 3. Category Logic
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _stores = MutableStateFlow<List<Store>>(emptyList())
    val stores = _stores.asStateFlow()

    private val _selectedStoreId = MutableStateFlow<String?>(null)
    val selectedStoreId = _selectedStoreId.asStateFlow()

    var showClearCartDialog by mutableStateOf(false)
        private set

    private var pendingProductToAdd: Product? = null

    private val _toastEvent = Channel<String>()
    val toastEvent = _toastEvent.receiveAsFlow()

    val categories: StateFlow<List<String>> = _products.map { productList ->
        listOf("All") + productList.map { it.category }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    val filteredProducts = combine(_products, _selectedCategory,_searchQuery) { list, category,query ->
        if(query.isNotBlank()){
            // SEARCH MODE: Ignore tabs, search everything (Name or Subcategory)
            list.filter { product ->
                product.name.contains(query, ignoreCase = true) ||
                        (product.subcategory?.contains(query, ignoreCase = true) == true)
            }
        }else {
            if (category == "All") {
                list
            } else {
                list.filter { it.category.equals(category, ignoreCase = true) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
//        fetchProducts()
        initializeCatalog()
        fetchLocation()
    }

    private fun initializeCatalog() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Fetch Stores
                val storeList = repository.getStores()
                _stores.value = storeList

                // 2. Select default store (First one)
                if (storeList.isNotEmpty()) {
                    selectStore(storeList[0].id)
                } else {
                    _isLoading.value = false // No stores found
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Error init catalog", e)
                _isLoading.value = false
            }
        }
    }

    fun selectStore(storeId: String) {
        _selectedStoreId.value = storeId
        _selectedCategory.value = "All" // Reset category when switching stores

        viewModelScope.launch {
            _isLoading.value = true
            // 3. Fetch products for THIS store only
            val list = repository.getProductsByStore(storeId)
            _products.value = list
            _isLoading.value = false
        }
    }

//    private fun fetchProducts() {
//        viewModelScope.launch {
//            _isLoading.value = true
//            try {
//                // The Network Call 🌍
//                val list = repository.getProducts()
//                android.util.Log.d("DEBUG_APP", "Fetched size: ${list.size}")
//                if (list.isNotEmpty()) {
//                    android.util.Log.d("DEBUG_APP", "First Item: ${list[0].name}")
//                } else {
//                    android.util.Log.e("DEBUG_APP", "List is EMPTY! Check Supabase RLS or Keys.")
//                }
//                _products.value = list
//            } catch (e: Exception) {
//                e.printStackTrace() // Log errors if any
//            } finally {
//                _isLoading.value = false
//            }
//        }
//    }

    fun addToCart(product: Product?) {
        if (product!=null){
            viewModelScope.launch {
                val result = cartRepository.addToCart(product)

                when(result){
                    is AddToCartResult.Success -> {
                        Log.d("Cart", "Item added")
                        _toastEvent.send("${product.name} added to cart")
                    }
                    is AddToCartResult.DifferentStoreConflict -> {
                        // 2. CONFLICT FOUND! Save product & Show Dialog
                        pendingProductToAdd = product
                        showClearCartDialog = true
                    }
                    is AddToCartResult.Error -> {
                        Log.e("Cart", "Error: ${result.message}")
                        _toastEvent.send("Failed to add: ${result.message}")
                    }
                }
            }
        }else {
           Log.d("DEBUG_APP", "Product is null!")
        }
    }

    fun confirmClearAndAdd() {
        viewModelScope.launch {
            val product = pendingProductToAdd
            if (product != null) {
                // 3. The Actual Call
                cartRepository.clearAndAdd(product)
                _toastEvent.send("Cart cleared. ${product.name} added!")
            }
            // Reset State
            showClearCartDialog = false
            pendingProductToAdd = null
        }
    }

    fun cancelClearCart() {
        showClearCartDialog = false
        pendingProductToAdd = null
    }

    fun fetchLocation() {
        viewModelScope.launch {
            val locationManager = LocationManager(context)
            val address = locationManager.getCurrentAddress()
            _userLocation.value = address ?: "Location Unavailable"
        }
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }
}