package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null) // Null means "still loading"
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            // Optional: Add a small delay if you want to show a Splash Screen logo
            // delay(1000)

            dataStoreManager.isLoggedIn.collect { isLoggedIn ->
                if (isLoggedIn) {
                    _startDestination.value = "catalog" // Or your Home route
                } else {
                    _startDestination.value = "auth_screen" // Your Login route
                }
            }
        }
    }
}