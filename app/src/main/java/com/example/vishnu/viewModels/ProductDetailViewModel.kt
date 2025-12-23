package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
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
class ProductDetailViewModel @Inject constructor(
    val player: ExoPlayer, // <--- Hilt injects this automatically!
    private val cartRepository: CartRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _product = MutableStateFlow<Product?>(null)
    val product = _product.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
                cartRepository.addToCart(product.id)
            }
        }else {

        }
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