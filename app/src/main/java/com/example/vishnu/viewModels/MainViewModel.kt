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
import kotlinx.coroutines.flow.first
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
        // Resolve the start destination ONCE at app launch. It must not track
        // later DataStore writes: changing NavHost's startDestination rebuilds
        // the graph, so a login (which saves the session) would re-mount the
        // landing screen on top of the navigationEvent-driven navigation.
        viewModelScope.launch {
            _startDestination.value = if (dataStoreManager.isLoggedIn.first()) {
                when (dataStoreManager.role.first()) {
                    UserRole.ADMIN -> "admin_dashboard"
                    UserRole.WHOLESALE, UserRole.DISTRIBUTOR -> "wholesale_home"
                    else -> "catalog"
                }
            } else {
                "auth_screen" // Your Login route
            }
        }
    }
}