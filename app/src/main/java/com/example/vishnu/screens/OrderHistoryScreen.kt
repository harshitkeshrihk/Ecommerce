//package com.example.vishnu.screens
//
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material3.Tab
//import androidx.compose.material3.TabRow
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.unit.dp
//import androidx.hilt.navigation.compose.hiltViewModel
//import com.example.vishnu.model.Order
//import com.example.vishnu.uicomponents.OrderItemCard
//import com.example.vishnu.viewModels.ProfileViewModel
//
//@Composable
//fun OrderHistoryScreen(
//    onOrderClick: (Order) -> Unit,
//    viewModel: ProfileViewModel = hiltViewModel()
//) {
//    var selectedTab by remember { mutableIntStateOf(0) }
//    val tabs = listOf("Active", "History")
//
//    val activeOrders by viewModel.activeOrders.collectAsState()
//    val pastOrders by viewModel.pastOrders.collectAsState()
//
//    Column(modifier = Modifier.fillMaxSize()) {
//        // TAB ROW
//        TabRow(selectedTabIndex = selectedTab) {
//            tabs.forEachIndexed { index, title ->
//                Tab(
//                    selected = selectedTab == index,
//                    onClick = { selectedTab = index },
//                    text = { Text(title) }
//                )
//            }
//        }
//
//        // CONTENT
//        val ordersToShow = if (selectedTab == 0) activeOrders else pastOrders
//
//        if (ordersToShow.isEmpty()) {
//            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                Text(text = "No ${tabs[selectedTab]} orders found", color = Color.Gray)
//            }
//        } else {
//            LazyColumn(contentPadding = PaddingValues(16.dp)) {
//                items(ordersToShow) { order ->
//                    // Reuse your existing OrderItemCard here!
//                    OrderItemCard(
//                        order = order,
//                        onOrderClick = { onOrderClick(order) }
//                    )
//                }
//            }
//        }
//    }
//}