package com.kevinnzou.sample

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kevinnzou.sample.navigation.Home
import com.kevinnzou.sample.navigation.Personal

/**
 * Created By Kevin Zou On 2023/12/8
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationSample(navHostController: NavHostController? = null) {
    val tabNavController = rememberNavController()
    val currentBackStackEntry = tabNavController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry.value?.destination?.route
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "SaveState Sample") },
                navigationIcon = {
                    IconButton(onClick = {
                        navHostController?.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        content = { paddingValues ->
            NavHost(
                navController = tabNavController,
                startDestination = "home",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("home") {
                    Home()
                }
                composable("personal") {
                    Personal()
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == "home",
                    onClick = { 
                        tabNavController.navigate("home") {
                            popUpTo(tabNavController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                )
                NavigationBarItem(
                    selected = currentRoute == "personal",
                    onClick = { 
                        tabNavController.navigate("personal") {
                            popUpTo(tabNavController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Personal") },
                )
            }
        },
    )
}
