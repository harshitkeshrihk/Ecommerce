package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.DeliveryPartner
import com.example.vishnu.model.Order
import com.example.vishnu.repository.AdminRepository
import com.example.vishnu.repository.DeliveryRepository
import com.example.vishnu.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
//    private val repository: ProfileRepository,
    private val adminRepository: AdminRepository,
    private val deliveryRepository: DeliveryRepository
) : ViewModel() {

    private val _allOrders = MutableStateFlow<List<Order>>(emptyList())
    val allOrders = _allOrders.asStateFlow()

    // Status update feedback
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private val _storeName = MutableStateFlow("Loading...")
    val storeName = _storeName.asStateFlow()

    var currentStoreId: String? = null
        private set

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _availablePartners = MutableStateFlow<List<DeliveryPartner>>(emptyList())
    val availablePartners = _availablePartners.asStateFlow()

    private val _assignmentStatus = MutableStateFlow<String?>(null)
    val assignmentStatus = _assignmentStatus.asStateFlow()

    init {
//        loadAllOrders()
        initializeDashboard()
    }

    fun initializeDashboard() {
        viewModelScope.launch {
            _isLoading.value = true

            // 1. Get My Store Profile
            val myStore = adminRepository.getMyStore()

            if (myStore != null) {
                currentStoreId = myStore.id
                _storeName.value = myStore.name // e.g. "Vishnu Crockery"

                // 2. Load Orders for THIS store
                loadOrders(myStore.id)
            } else {
                _storeName.value = "Access Denied"
                _toastMessage.emit("You are not a registered store owner.")
            }
            _isLoading.value = false
        }
    }

//    fun loadAllOrders() {
//        viewModelScope.launch {
//            _allOrders.value = repository.getAllOrdersForAdmin()
//        }
//    }

    private fun loadOrders(storeId: String) {
        viewModelScope.launch {
            _allOrders.value = adminRepository.getOrdersForStore(storeId)
        }
    }

    fun changeStatus(orderId: Long, newStatus: String) {
        viewModelScope.launch {
            val success = adminRepository.updateOrderStatus(orderId, newStatus)
            if (success) {
                _toastMessage.emit("Order #$orderId updated to $newStatus")
//                loadAllOrders() // Refresh list
                currentStoreId?.let { loadOrders(it) }
            } else {
                _toastMessage.emit("Failed to update status")
            }
        }
    }

    fun fetchAvailablePartners() {
        viewModelScope.launch {
            _isLoading.value = true
            val partners = deliveryRepository.getAvailablePartners()
            _availablePartners.value = partners
            _isLoading.value = false
        }
    }

    fun assignOrder(order: Order, partner: DeliveryPartner) {
        viewModelScope.launch {
            _isLoading.value = true

            // Hardcoded Store Coordinates (Replace with actual store location from your Store object)
            val storeLat = 28.7041
            val storeLng = 77.1025

            // Hardcoded Delivery Coordinates (Ideally geocode the order.shippingAddress)
            // For now, let's assume we are sending dummy coords for the destination
            val destLat = 28.5355
            val destLng = 77.3910

            val success = deliveryRepository.assignOrderToPartner(
                orderId = order.id,
                partnerId = partner.id,
                pickupLat = storeLat,
                pickupLng = storeLng,
                destLat = destLat,
                destLng = destLng
            )

            if (success) {
                _toastMessage.emit("Order assigned to ${partner.name}")
                // Refresh orders to show updated status
//                loadOrders()
            } else {
                _toastMessage.emit("Failed to assign order")
            }
            _isLoading.value = false
        }
    }
}