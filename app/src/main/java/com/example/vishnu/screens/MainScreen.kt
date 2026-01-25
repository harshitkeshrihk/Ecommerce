package com.example.vishnu.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.vishnu.uicomponents.NavBarItem // Import your sealed class
// Imports for the library
import com.exyte.animatednavbar.AnimatedNavigationBar
import com.exyte.animatednavbar.animation.balltrajectory.Parabolic
import com.exyte.animatednavbar.animation.indendshape.Height
import com.exyte.animatednavbar.items.dropletbutton.DropletButton

@Composable
fun MainScreen(
    onProductClick: (String) -> Unit,
    onCartClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    val navigationBarItems = remember {
        listOf(NavBarItem.Home, NavBarItem.Cart, NavBarItem.Profile)
    }
    var selectedIndex by remember { mutableIntStateOf(0) }

    // This Scaffold holds the BottomBar
    Scaffold(
        bottomBar = {
            AnimatedNavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(56.dp),
                selectedIndex = selectedIndex,
                ballAnimation = Parabolic(tween(300)),
                indentAnimation = Height(tween(300)),
                barColor = MaterialTheme.colorScheme.primaryContainer,
                ballColor = MaterialTheme.colorScheme.primary
            ) {
                navigationBarItems.forEachIndexed { index, item ->
                    // Using DropletButton from the library for cool effects
                    DropletButton(
                        modifier = Modifier.fillMaxSize(),
                        isSelected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = item.icon,
                        dropletColor = MaterialTheme.colorScheme.primary,
                        animationSpec = tween(300)
                    )
                }
            }
        }
    ) { paddingValues ->
        // This Box fills the gap! content is pushed up by the bottom bar
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            when (selectedIndex) {
                0 -> {
                    // Show Catalog (Home)
                    CatalogScreen(
                        onProductClick = onProductClick,
                        onProfileClick = { selectedIndex = 2 } // Jump to profile tab
                    )
                }
                1 -> {
                    // Show Cart (You can render your CartScreen here directly)
                    // Or trigger the navigation callback if you want a separate full screen
                    LaunchedEffect(Unit) { onCartClick() }
                }
                2 -> {
                    // Show Profile
                    LaunchedEffect(Unit) { onProfileClick() }
                }
            }
        }
    }
}