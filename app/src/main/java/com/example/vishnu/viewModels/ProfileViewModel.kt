package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.Order
import com.example.vishnu.model.OrderItemDetail
import com.example.vishnu.model.UserAddress
import com.example.vishnu.repository.AddressRepository
import com.example.vishnu.repository.ProfileRepository
import com.example.vishnu.utils.LocationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.query.Order as SupabaseOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val addressRepository: AddressRepository,
) : ViewModel() {

    // Form State
    var name = MutableStateFlow("")
    var phone = MutableStateFlow("")
    var address = MutableStateFlow("")

    // UI States
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _updateStatus = MutableStateFlow<String?>(null) // Message for Toast
    val updateStatus = _updateStatus.asStateFlow()

    private val _activeOrders = MutableStateFlow<List<Order>>(emptyList())
    val activeOrders = _activeOrders.asStateFlow()

    private val _pastOrders = MutableStateFlow<List<Order>>(emptyList())
    val pastOrders = _pastOrders.asStateFlow()

    private val _addresses = MutableStateFlow<List<UserAddress>>(emptyList())
    val addresses = _addresses.asStateFlow()

    init {
        fetchProfile()
    }

    private fun fetchProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            val profile = repository.getUserProfile()
            if (profile != null) {
                name.value = profile.fullName ?: ""
                phone.value = profile.phoneNumber ?: ""
                address.value = profile.address ?: ""
            }
            _isLoading.value = false
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            val success = repository.updateProfile(
                name.value,
                phone.value,
                address.value
            )
            if (success) {
                _updateStatus.value = "Profile Updated Successfully! ✅"
            } else {
                _updateStatus.value = "Failed to update profile. ❌"
            }
            _isLoading.value = false
        }
    }

    fun loadOrderData() {
        viewModelScope.launch {
            _isLoading.value = true
            // Load both lists in parallel
            val active = async { repository.getActiveOrders() }
            val past = async { repository.getPastOrders() }

            _activeOrders.value = active.await()
            _pastOrders.value = past.await()
            _isLoading.value = false
        }
    }

    suspend fun getOrderItems(orderId: Long): List<OrderItemDetail> {
        return repository.getOrderItems(orderId)
    }

    fun loadAddresses(){
        viewModelScope.launch {
            _addresses.value = addressRepository.getUserAddresses()
        }
    }

    fun addNewAddress(label: String,address: String,phoneNumber: String){
        viewModelScope.launch {
            val newAddress = UserAddress(label = label, addressText = address, phoneNumber = phoneNumber)
            val success = addressRepository.addAddress(newAddress)
            if(success) loadAddresses()
        }
    }

    fun deleteAddress(id: String){
        viewModelScope.launch {
            addressRepository.deleteAddress(id)
            loadAddresses()
        }
    }

    fun setDefaultAddress(id: String){
        viewModelScope.launch {
            addressRepository.setDefaultAddress(id)
            loadAddresses()
        }
    }

    // Clear message after showing Toast
    fun clearStatus() {
        _updateStatus.value = null
    }
}