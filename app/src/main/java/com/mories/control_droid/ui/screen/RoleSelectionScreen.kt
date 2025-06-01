package com.mories.control_droid.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mories.control_droid.core.model.DeviceRole

@Composable
fun RoleSelectionScreen(onRoleSelected: () -> Unit) {
    val context = LocalContext.current
    val roleManager = remember { com.mories.control_droid.core.auth.RoleManager(context) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Choose device role", fontSize = 20.sp, modifier = Modifier.padding(16.dp))

        Button(onClick = {
            roleManager.setRole(DeviceRole.CONTROLLER)
            onRoleSelected()
        }) { Text("I want to control other device") }

        Button(onClick = {
            roleManager.setRole(DeviceRole.TARGET)
            onRoleSelected()
        }) { Text("I want to be controlled") }
    }
}