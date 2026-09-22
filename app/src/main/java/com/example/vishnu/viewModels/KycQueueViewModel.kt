package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.PricingTier
import com.example.vishnu.model.UserProfile
import com.example.vishnu.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KycQueueViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _pendingRequests = MutableStateFlow<List<UserProfile>>(emptyList())
    val pendingRequests = _pendingRequests.asStateFlow()

    private val _pricingTiers = MutableStateFlow<List<PricingTier>>(emptyList())
    val pricingTiers = _pricingTiers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _pendingRequests.value = adminRepository.getPendingKycRequests()
            _pricingTiers.value = adminRepository.getPricingTiers()
            _isLoading.value = false
        }
    }

    fun approve(profile: UserProfile, tierId: String) {
        val requestedRole = profile.requestedRole ?: return
        viewModelScope.launch {
            val success = adminRepository.approveKyc(profile.id, requestedRole, tierId)
            _toastMessage.emit(
                if (success) "${profile.businessName ?: profile.fullName} approved as $requestedRole"
                else "Failed to approve request. Try again."
            )
            if (success) refresh()
        }
    }

    fun reject(profile: UserProfile, reason: String) {
        viewModelScope.launch {
            val success = adminRepository.rejectKyc(profile.id, reason)
            _toastMessage.emit(
                if (success) "${profile.businessName ?: profile.fullName} rejected"
                else "Failed to reject request. Try again."
            )
            if (success) refresh()
        }
    }
}
