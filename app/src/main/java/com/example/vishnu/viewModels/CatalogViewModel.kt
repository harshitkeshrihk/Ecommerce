package com.example.vishnu.viewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.Product
import com.example.vishnu.repository.CartRepository
import com.example.vishnu.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val cartRepository: CartRepository
) : ViewModel() {

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    // 2. Loading State (To show a spinner while fetching)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        fetchProducts()
    }

    private fun fetchProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // The Network Call 🌍
                val list = repository.getProducts()
                android.util.Log.d("DEBUG_APP", "Fetched size: ${list.size}")
                if (list.isNotEmpty()) {
                    android.util.Log.d("DEBUG_APP", "First Item: ${list[0].name}")
                } else {
                    android.util.Log.e("DEBUG_APP", "List is EMPTY! Check Supabase RLS or Keys.")
                }
                _products.value = list
            } catch (e: Exception) {
                e.printStackTrace() // Log errors if any
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addToCart(product: Product?) {
        if (product!=null){
            viewModelScope.launch {
                cartRepository.addToCart(product.id)
            }
        }else {
           Log.d("DEBUG_APP", "Product is null!")
        }
    }
}