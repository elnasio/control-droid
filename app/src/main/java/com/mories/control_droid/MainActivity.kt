package com.mories.control_droid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mories.control_droid.core.auth.RoleManager
import com.mories.control_droid.core.diagnostics.CrashLogger
import com.mories.control_droid.core.model.DeviceRole.CONTROLLER
import com.mories.control_droid.core.model.DeviceRole.TARGET
import com.mories.control_droid.core.storage.PairedDeviceStore
import com.mories.control_droid.core.storage.MacroStore
import com.mories.control_droid.core.control.MacroRunner
import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.features.controller.AddDeviceScreen
import com.mories.control_droid.features.controller.DeviceControlScreen
import com.mories.control_droid.features.controller.RemotePreviewScreen
import com.mories.control_droid.features.controller.MacroScreen
import com.mories.control_droid.features.target.TargetWaitingScreen
import com.mories.control_droid.ui.NavigationTarget
import com.mories.control_droid.ui.screen.HomeScreen
import com.mories.control_droid.ui.screen.RoleSelectionScreen
import com.mories.control_droid.ui.theme.ControldroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLogger.install(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ControldroidTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val roleManager = remember { RoleManager(context) }
                val store = remember { PairedDeviceStore(context) }
                val macroStore = remember { MacroStore(context) }
                var lastCrash by remember { mutableStateOf(CrashLogger.readLastCrash(context)) }

                lastCrash?.let { crashText ->
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Crash terakhir terdeteksi") },
                        text = {
                            Text(
                                text = crashText,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .heightIn(max = 400.dp)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                CrashLogger.clear(context)
                                lastCrash = null
                            }) { Text("Tutup & hapus") }
                        }
                    )
                }

                val initialRoleManager = RoleManager(applicationContext)
                val startDestination = if (initialRoleManager.hasRole()) {
                    when (initialRoleManager.getRole()) {
                        CONTROLLER -> NavigationTarget.Home.route
                        TARGET -> NavigationTarget.Preview.withArg("local")
                    }
                } else {
                    NavigationTarget.RoleSelection.route
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(padding)
                    ) {
                        composable(NavigationTarget.RoleSelection.route) {
                            RoleSelectionScreen(
                                onRoleSelected = {
                                    val newStart = when (roleManager.getRole()) {
                                        CONTROLLER -> NavigationTarget.Home.route
                                        TARGET -> NavigationTarget.Preview.withArg("local")
                                    }
                                    navController.navigate(newStart) {
                                        popUpTo(NavigationTarget.RoleSelection.route) {
                                            inclusive = true
                                        }
                                    }
                                })
                        }

                        composable(NavigationTarget.Home.route) {
                            var homeDevices by remember { mutableStateOf(store.getAll()) }
                            LaunchedEffect(Unit) { homeDevices = store.getAll() }

                            HomeScreen(devices = homeDevices, onDeviceClick = { selected ->
                                navController.navigate(NavigationTarget.Control.withArg(selected.id))
                            }, onAddClick = {
                                navController.navigate(NavigationTarget.Pair.route)
                            }, onMacrosClick = {
                                navController.navigate(NavigationTarget.Macros.route)
                            }, onBroadcastHome = {
                                MacroRunner.broadcastAction(DeviceAction.GLOBAL_HOME, homeDevices)
                            })
                        }

                        composable(NavigationTarget.Macros.route) {
                            var macros by remember { mutableStateOf(macroStore.getAll()) }
                            MacroScreen(
                                macros = macros,
                                onSave = { macro ->
                                    macroStore.save(macro)
                                    macros = macroStore.getAll()
                                },
                                onDelete = { macro ->
                                    macroStore.delete(macro.id)
                                    macros = macroStore.getAll()
                                },
                                onRunOnAll = { macro ->
                                    MacroRunner.runOnDevices(macro, store.getAll())
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(NavigationTarget.Pair.route) {
                            AddDeviceScreen(
                                pairedDevices = store.getAll(),
                                onDeviceFound = { device ->
                                    val saved = store.saveDevice(device)
                                    navController.navigate(
                                        NavigationTarget.Control.withArg(saved.id)
                                    ) {
                                        popUpTo(NavigationTarget.Home.route)
                                    }
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = NavigationTarget.Control.withParam("id"),
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { entry ->
                            val id = entry.arguments?.getString("id").orEmpty()
                            store.getDeviceById(id)?.let {
                                DeviceControlScreen(navController, it)
                            }
                        }

                        composable(
                            route = NavigationTarget.Preview.withParam("id"),
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { entry ->
                            val id = entry.arguments?.getString("id").orEmpty()
                            val device = store.getDeviceById(id)
                            if (device != null) {
                                RemotePreviewScreen(navController, device)
                            } else {
                                TargetWaitingScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!", modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ControldroidTheme {
        Greeting("Android")
    }
}
