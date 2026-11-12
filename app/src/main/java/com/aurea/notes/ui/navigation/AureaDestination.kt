package com.aurea.notes.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.aurea.notes.R

enum class AureaDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME(
        route = "home",
        labelRes = R.string.nav_home,
        unselectedIcon = Icons.Outlined.Home,
        selectedIcon = Icons.Rounded.Home
    ),
    NOTES(
        route = "notes",
        labelRes = R.string.nav_notes,
        unselectedIcon = Icons.Outlined.Description,
        selectedIcon = Icons.Rounded.Description
    ),
    JOURNAL(
        route = "journal",
        labelRes = R.string.nav_journal,
        unselectedIcon = Icons.Outlined.Book,
        selectedIcon = Icons.Rounded.Book
    ),
    SEARCH(
        route = "search",
        labelRes = R.string.nav_search,
        unselectedIcon = Icons.Outlined.Search,
        selectedIcon = Icons.Rounded.Search
    )
}
