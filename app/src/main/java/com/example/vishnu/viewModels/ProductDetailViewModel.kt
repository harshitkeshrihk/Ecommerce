package com.example.vishnu.viewModels

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.vishnu.model.Product
import com.example.vishnu.repository.AddToCartResult
import com.example.vishnu.repository.CartRepository
import com.example.vishnu.repository.ProductRepository
import com.example.vishnu.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    val player: ExoPlayer, // <--- Hilt injects this automatically!
    private val cartRepository: CartRepository,
    private val productRepository: ProductRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _product = MutableStateFlow<Product?>(null)
    val product = _product.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isAdmin = dataStoreManager.isAdmin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    var showClearCartDialog by mutableStateOf(false)
        private set

    private var pendingProductToAdd: Product? = null

    private val _toastEvent = Channel<String>()
    val toastEvent = _toastEvent.receiveAsFlow()

    // Prepare the video (Safe to call multiple times)
    fun initializePlayer(videoUrl: String) {
        // Only prepare if not already playing or different URL
        if (player.mediaItemCount == 0) {
            val mediaItem = MediaItem.fromUri(videoUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun addToCart(product: Product?) {
        if (product!=null){
            viewModelScope.launch {
                val result = cartRepository.addToCart(product)
                when (result) {
                    is AddToCartResult.Success -> {
                        // Optional: Show Success Toast
                        _toastEvent.send("${product.name} added to cart")
                    }
                    is AddToCartResult.DifferentStoreConflict -> {
                        // Trigger Dialog
                        pendingProductToAdd = product
                        showClearCartDialog = true
                    }
                    is AddToCartResult.Error -> {
                        // Handle error (log it)
                        _toastEvent.send("Failed to add: ${result.message}")
                    }
                }
            }
        }else {

        }
    }

    fun confirmClearAndAdd() {
        viewModelScope.launch {
            pendingProductToAdd?.let {
                cartRepository.clearAndAdd(it)
                _toastEvent.send("Cart cleared. ${pendingProductToAdd!!.name} added!")
            }
            showClearCartDialog = false
            pendingProductToAdd = null
        }
    }

    fun cancelClearCart() {
        showClearCartDialog = false
        pendingProductToAdd = null
    }


    fun loadProduct(productId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            // Fetch from Cloud
            val fetchedProduct = productRepository.getProductById(productId)
            _product.value = fetchedProduct

            // If product has a video, get player ready immediately
            fetchedProduct?.videoUrl?.let { url ->
                if (url.isNotEmpty()) initializePlayer(url)
            }

            _isLoading.value = false
        }
    }

    // CLEANUP: When user hits 'Back', this runs automatically
    override fun onCleared() {
        super.onCleared()
        player.release() // Free up the memory!
    }
}