package com.example.vishnu.viewModels

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.CartItem
import com.example.vishnu.repository.CartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository
) : ViewModel() {

    // 1. Observe the Repository directly
    val cartItems: StateFlow<List<CartItem>> = cartRepository.cartItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            cartRepository.fetchCartItems()
        }
    }

    // 2. Calculated Total Price (Updates automatically)
    val totalPrice: StateFlow<Double> = cartItems.map { items ->
        items.sumOf { it.product.priceRetail * it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun fetchCartItems(){
        viewModelScope.launch {
            cartRepository.fetchCartItems()
        }
    }

    // 3. Actions
    fun removeFromCart(productId: String) {
        viewModelScope.launch {
            cartRepository.removeFromCart(productId)
        }
    }

    fun increaseQty(item: CartItem) {
        viewModelScope.launch {
            cartRepository.incrementQuantity(item.product.id)
        }
    }

    fun decreaseQty(item: CartItem) {
        viewModelScope.launch {
            cartRepository.decrementQuantity(item.product.id)
        }
    }



    // 4. The WhatsApp Checkout Logic
    fun checkoutOnWhatsApp(context: Context) {
        val items = cartItems.value
        if (items.isEmpty()) return

        val phoneNumber = "919839633958" // REPLACE THIS with your real number later!

        val orderList = items.joinToString("\n") { item ->
            "▪ ${item.product.name} (x${item.quantity}) - ₹${(item.product.priceRetail * item.quantity).toInt()}"
        }

        val total = items.sumOf { it.product.priceRetail * it.quantity }

        val message = """
            👋 *New Order Request*
            
            $orderList
            
            ----------------
            💰 *Total Estimate: ₹${total.toInt()}*
            
            Please confirm availability and delivery time.
        """.trimIndent()

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(message)}")
                setPackage("com.whatsapp") // Tries to open specifically WhatsApp
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback if standard WhatsApp is not found (e.g. try WA Business or browser)
            val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(message)}")
            }
            context.startActivity(browserIntent)
        }
    }
}