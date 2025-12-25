package com.example.vishnu.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Create the extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
        val IS_ADMIN_KEY = booleanPreferencesKey("is_admin")
    }

    // Save Login State
//    suspend fun saveLoginState(isLoggedIn: Boolean) {
//        context.dataStore.edit { preferences ->
//            preferences[IS_LOGGED_IN_KEY] = isLoggedIn
//        }
//    }

    suspend fun saveUserSession(isLoggedIn: Boolean, isAdmin: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN_KEY] = isLoggedIn
            preferences[IS_ADMIN_KEY] = isAdmin
        }
    }

    // Read Login State (Returns a Flow so UI updates automatically)
    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_LOGGED_IN_KEY] ?: false // Default to false
        }

    val isAdmin: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_ADMIN_KEY] ?: false // Default to false (Customer)
        }

    // Clear Data (Useful for Logout)
    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}