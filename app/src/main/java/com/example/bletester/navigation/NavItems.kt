package com.example.bletester.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

data class NavItems(
    val label: String,
    val icon: ImageVector,
    val route: String
)
val listOfNavItems = listOf(
    NavItems(
        label = "Diapason",
        icon = Icons.Default.Home,
        route = Screens.DeviceListScreen.name
    ),
    NavItems(
        label = "Report",
        icon = Icons.Default.DateRange,
        route = Screens.ReportScreen.name
    ),
    NavItems(
        label = "Logs",
        icon = Icons.Default.Build,
        route = Screens.LogsScreen.name
    )

)