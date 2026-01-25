package com.example.vishnu.uicomponents

import androidx.annotation.DrawableRes
import com.example.vishnu.R

sealed class NavBarItem(@DrawableRes val icon: Int, val title: String) {
    // Make sure you have these icons in res/drawable folder
    object Home : NavBarItem(R.drawable.outline_home_24, "Home")
    object Cart : NavBarItem(R.drawable.outline_shopping_cart_24, "Cart")
    object Profile : NavBarItem(R.drawable.outline_person_24, "Profile")
}