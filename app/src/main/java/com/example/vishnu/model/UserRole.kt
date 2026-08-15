package com.example.vishnu.model

enum class UserRole {
    RETAIL,
    WHOLESALE,
    DISTRIBUTOR,
    ADMIN;

    companion object {
        fun fromString(value: String?): UserRole =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: RETAIL
    }
}

fun UserProfile?.roleEnum(): UserRole = UserRole.fromString(this?.role)
