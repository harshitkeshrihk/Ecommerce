package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.Order
import com.example.vishnu.repository.AdminRepository
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
    private val adminRepository: AdminRepository
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
}