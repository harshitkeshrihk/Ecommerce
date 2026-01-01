package com.example.vishnu.viewModels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.CartItem
import com.example.vishnu.model.UserAddress
import com.example.vishnu.repository.AddressRepository
import com.example.vishnu.repository.CartRepository
import com.example.vishnu.repository.ProfileRepository
import com.example.vishnu.utils.LocationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val profileRepository: ProfileRepository, // Inject Profile Repo
    private val addressRepository: AddressRepository,
    private val auth: Auth,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // 1. Observe the Repository directly
    val cartItems: StateFlow<List<CartItem>> = cartRepository.cartItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

//    private val _userLocation = MutableStateFlow("Fetching location...")
//    val userLocation = _userLocation.asStateFlow()

    private val _userEmail = MutableStateFlow("")
    val userEmail = _userEmail.asStateFlow()

    private val _userPhone = MutableStateFlow("")
    val userPhone = _userPhone.asStateFlow()

    private val _savedAddresses = MutableStateFlow<List<UserAddress>>(emptyList())
    val savedAddresses = _savedAddresses.asStateFlow()

    private val _selectedAddress = MutableStateFlow<UserAddress?>(null)
    val selectedAddress = _selectedAddress.asStateFlow()

    sealed class CartEvent {
        object OrderPlacedSuccess : CartEvent()
        data class OrderFailed(val message: String) : CartEvent()
    }

    private val _cartEvent = MutableSharedFlow<CartEvent>()
    val cartEvent = _cartEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            cartRepository.fetchCartItems()
//            fetchLocation()
            fetchUserDetails()
            fetchAddresses()
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

    private suspend fun fetchUserDetails() {
        // 1. Get Email directly from Auth User Session
        val currentUser = auth.currentUserOrNull()
        _userEmail.value = currentUser?.email ?: ""

        // 2. Get Phone: Try Auth first, then fallback to Profile Table
        var phone = currentUser?.phone

        if (phone.isNullOrBlank()) {
            // If Auth doesn't have phone, check the 'profiles' table
            val profile = profileRepository.getUserProfile()
            phone = profile?.phoneNumber
        }

        _userPhone.value = phone ?: ""
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

    fun onPaymentSuccess(paymentId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            val freshItems: List<CartItem> =  cartRepository.fetchCartItems()

            if (freshItems.isEmpty()) {
                _cartEvent.emit(CartEvent.OrderFailed("Cart is empty"))
                _isLoading.value = false
                return@launch
            }

            var freshCartPrice: Double = 0.0
            freshItems.forEach { items ->
                freshCartPrice += items.product.priceRetail * items.quantity
            }

            val addressToUse =
                _selectedAddress.value?.addressText
                    ?: savedAddresses.value.find { it.isDefault }?.addressText
                    ?: profileRepository.getUserProfile()?.address
                    ?: "Address Not Provided"

            val success = cartRepository.createOrder(
                paymentId = paymentId,
                amount = freshCartPrice,
                address = addressToUse,
                cartItems = freshItems
            )

            if (success) {
                cartRepository.clearCart()
                // You can expose a state here to Navigate to "Order Success Screen"
                _cartEvent.emit(CartEvent.OrderPlacedSuccess)
            }else{
                _cartEvent.emit(CartEvent.OrderFailed("Failed to create order on server"))
            }
            _isLoading.value = false
        }
    }

//    fun fetchLocation() {
//        viewModelScope.launch {
//            val locationManager = LocationManager(context)
//            val address = locationManager.getCurrentAddress()
//            _userLocation.value = address ?: "Location Unavailable"
//        }
//    }

    fun fetchAddresses(){
        viewModelScope.launch {
            val list = addressRepository.getUserAddresses()
            _savedAddresses.value = list
            if(_selectedAddress.value == null){
                _selectedAddress.value = list.find{it.isDefault} ?: list.firstOrNull()
            }
        }
    }

    fun selectAddress(address: UserAddress){
        _selectedAddress.value = address
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