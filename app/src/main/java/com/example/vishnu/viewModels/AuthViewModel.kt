package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.UserRole
import com.example.vishnu.model.roleEnum
import com.example.vishnu.repository.AuthRepository
import com.example.vishnu.repository.ProfileRepository
import com.example.vishnu.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val dataStoreManager: DataStoreManager,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    // Inputs
    var email = MutableStateFlow("")
    var password = MutableStateFlow("")
    var fullName = MutableStateFlow("") // Only for Sign Up

    // State
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    sealed class AuthDestination {
        object AdminDashboard : AuthDestination()
        object Catalog : AuthDestination()
        object WholesaleHome : AuthDestination()
    }

    private val _navigationEvent = MutableSharedFlow<AuthDestination>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    // To decide navigation (App vs Login Screen)
    val sessionStatus = repository.sessionStatus

    fun onSignUp() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                repository.signUp(email.value, password.value, fullName.value)
                _authState.value = AuthState.Success("Sign up successful! Please check your email.")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Sign up failed")
            }
        }
    }

    fun onSignIn() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                repository.signIn(email.value, password.value)

                // Phase 1 store_id cleanup: admin access now comes from
                // profiles.role only. Owning a `stores` row no longer grants
                // ADMIN — that row is still looked up elsewhere (AdminRepository/
                // AdminViewModel) purely to attach store_id to new products/
                // orders for the delivery-partner app, not for authorization.
                val role = profileRepository.getUserProfile().roleEnum()

                dataStoreManager.saveUserSession(true, role)

                when (role) {
                    UserRole.ADMIN -> _navigationEvent.emit(AuthDestination.AdminDashboard)
                    UserRole.WHOLESALE, UserRole.DISTRIBUTOR -> _navigationEvent.emit(AuthDestination.WholesaleHome)
                    else -> _navigationEvent.emit(AuthDestination.Catalog)
                }

                _authState.value = AuthState.Success("Welcome back!")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            repository.signOut()
            dataStoreManager.clearSession() // Clear local flag
        }
    }
}

// Simple State Wrapper
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val error: String) : AuthState()
}