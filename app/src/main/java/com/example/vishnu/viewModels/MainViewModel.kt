package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.UserRole
import com.example.vishnu.repository.AuthRepository
import com.example.vishnu.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null) // Null means "still loading"
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    // Used for route-level guards (e.g. admin_dashboard, add_edit_product)
    val role = dataStoreManager.role
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserRole.RETAIL)

    init {
        viewModelScope.launch {
            dataStoreManager.isLoggedIn.collect { isLoggedIn ->
                if (isLoggedIn) {
                    dataStoreManager.role.collect { role ->
                        _startDestination.value = if (role == UserRole.ADMIN) "admin_dashboard" else "catalog"
                    }
                } else {
                    _startDestination.value = "auth_screen" // Your Login route
                }
            }
        }
    }
}