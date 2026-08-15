package com.example.vishnu.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vishnu.model.UserRole
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
        val ROLE_KEY = stringPreferencesKey("user_role")
    }

    suspend fun saveUserSession(isLoggedIn: Boolean, role: UserRole) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN_KEY] = isLoggedIn
            preferences[ROLE_KEY] = role.name
        }
    }

    // Read Login State (Returns a Flow so UI updates automatically)
    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_LOGGED_IN_KEY] ?: false // Default to false
        }

    val role: Flow<UserRole> = context.dataStore.data
        .map { preferences -> UserRole.fromString(preferences[ROLE_KEY]) }

    val isAdmin: Flow<Boolean> = role.map { it == UserRole.ADMIN }

    // Clear Data (Useful for Logout)
    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}