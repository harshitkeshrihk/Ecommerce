package com.example.vishnu.viewModels

import androidx.datastore.dataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.repository.AdminRepository
import com.example.vishnu.repository.AuthRepository
import com.example.vishnu.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.receiveAsFlow

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val dataStoreManager: DataStoreManager,
    private val adminRepository: AdminRepository
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
    }

    private val _navigationEvent = Channel<AuthDestination>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

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

                val myStore = adminRepository.getMyStore()
                val isStoreOwner = myStore != null

                dataStoreManager.saveUserSession(true,isStoreOwner)

                if(isStoreOwner){
                    _navigationEvent.send(AuthDestination.AdminDashboard)
                }else{
                    _navigationEvent.send(AuthDestination.Catalog)
                }

                _authState.value = AuthState.Success("Welcome back!")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            _authState.value = AuthState.Idle
            email.value = ""
            password.value = ""
            fullName.value = ""
            dataStoreManager.clearSession() // Clear local flag
            repository.signOut()
        }
    }


//    fun isAdmin(email: String?): Boolean {
//        return Constants.ADMIN_EMAILS.contains(email)
//    }
}

//object Constants {
//    val ADMIN_EMAILS = listOf(
//        "harshitkeshrihk@gmail.com",
//    )
//}

// Simple State Wrapper
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val error: String) : AuthState()
}