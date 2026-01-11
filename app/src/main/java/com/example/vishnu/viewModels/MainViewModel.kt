package com.example.vishnu.viewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.repository.AuthRepository
import com.example.vishnu.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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

    val isAdmin = dataStoreManager.isAdmin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            // Optional: Add a small delay if you want to show a Splash Screen logo
            // delay(1000)
            val isLoggedIn = dataStoreManager.isLoggedIn.first()
                if (isLoggedIn) {
                    val isAdmin  = dataStoreManager.isAdmin.first()
                    if(isAdmin) {
                        _startDestination.value = "admin_dashboard"
                    }else {
                        _startDestination.value = "catalog"
                    }
                } else {
                    _startDestination.value = "auth_screen" // Your Login route
                }
        }
    }
}