package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.Order
import com.example.vishnu.model.OrderItemDetail
import com.example.vishnu.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.query.Order as SupabaseOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {

    // Form State
    var name = MutableStateFlow("")
    var phone = MutableStateFlow("")
    var address = MutableStateFlow("")

    // Business KYC form state (Phase 1 wholesale/distributor application)
    var kycRole = MutableStateFlow("wholesale") // "wholesale" | "distributor"
    var kycGstin = MutableStateFlow("")
    var kycBusinessName = MutableStateFlow("")

    private val _currentRole = MutableStateFlow("retail")
    val currentRole = _currentRole.asStateFlow()

    private val _kycStatus = MutableStateFlow<String?>(null) // null | pending | verified | rejected
    val kycStatus = _kycStatus.asStateFlow()

    private val _kycRejectionReason = MutableStateFlow<String?>(null)
    val kycRejectionReason = _kycRejectionReason.asStateFlow()

    private val _kycSubmitStatus = MutableStateFlow<String?>(null)
    val kycSubmitStatus = _kycSubmitStatus.asStateFlow()

    // UI States
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _updateStatus = MutableStateFlow<String?>(null) // Message for Toast
    val updateStatus = _updateStatus.asStateFlow()

    private val _activeOrders = MutableStateFlow<List<Order>>(emptyList())
    val activeOrders = _activeOrders.asStateFlow()

    private val _pastOrders = MutableStateFlow<List<Order>>(emptyList())
    val pastOrders = _pastOrders.asStateFlow()

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
                _currentRole.value = profile.role ?: "retail"
                _kycStatus.value = profile.kycStatus
                _kycRejectionReason.value = profile.kycRejectionReason
            }
            _isLoading.value = false
        }
    }

    fun submitKycApplication() {
        viewModelScope.launch {
            if (kycGstin.value.isBlank() || kycBusinessName.value.isBlank()) {
                _kycSubmitStatus.value = "GSTIN and business name are required."
                return@launch
            }
            _isLoading.value = true
            val success = repository.submitKycApplication(kycRole.value, kycGstin.value, kycBusinessName.value)
            _kycSubmitStatus.value = if (success) {
                _kycStatus.value = "pending"
                "Application submitted. We'll review it shortly."
            } else {
                "Failed to submit application. Please try again."
            }
            _isLoading.value = false
        }
    }

    fun clearKycSubmitStatus() {
        _kycSubmitStatus.value = null
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

    // Clear message after showing Toast
    fun clearStatus() {
        _updateStatus.value = null
    }
}