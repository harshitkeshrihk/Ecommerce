package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    // UI States
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _updateStatus = MutableStateFlow<String?>(null) // Message for Toast
    val updateStatus = _updateStatus.asStateFlow()

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

    // Clear message after showing Toast
    fun clearStatus() {
        _updateStatus.value = null
    }
}