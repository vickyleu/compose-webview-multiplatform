package com.kevinnzou.sample

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.kevinnzou.sample.navigation.HomeTab
import com.kevinnzou.sample.navigation.PersonalTab

/**
 * Created By Kevin Zou On 2023/12/8
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyagerNavigationSample(navHostController: NavHostController? = null) {
    TabNavigator(HomeTab) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "SaveState Sample") },
                    navigationIcon = {
                        IconButton(onClick = {
                            navHostController?.popBackStack()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
            },
            content = {
                CurrentTab()
            },
            bottomBar = {
                NavigationBar {
                    TabNavigationItem(HomeTab)
                    TabNavigationItem(PersonalTab)
                }
            },
        )
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    NavigationBarItem(
        selected = tabNavigator.current.key == tab.key,
        onClick = { tabNavigator.current = tab },
        icon = { Icon(painter = tab.options.icon!!, contentDescription = tab.options.title) },
    )
}
