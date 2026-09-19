package com.mories.control_droid.features.controller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mories.control_droid.core.model.DeviceAction
import com.mories.control_droid.core.model.Macro
import com.mories.control_droid.ui.components.AppToolbar
import java.util.UUID

@Composable
fun MacroScreen(
    macros: List<Macro>,
    onSave: (Macro) -> Unit,
    onDelete: (Macro) -> Unit,
    onRunOnAll: (Macro) -> Unit,
    onBackClick: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf(DeviceAction.GLOBAL_HOME)) }
    val availableActions = listOf(
        DeviceAction.GLOBAL_BACK,
        DeviceAction.GLOBAL_HOME,
        DeviceAction.GLOBAL_RECENT
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppToolbar(title = "Macros", onBackClick = onBackClick)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Buat macro baru", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Gabungkan beberapa aksi navigasi untuk dijalankan berurutan.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nama macro") },
                        singleLine = true
                    )
                    Text("Pilih aksi", style = MaterialTheme.typography.titleMedium)
                    availableActions.forEach { action ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Checkbox(
                                checked = action in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + action else selected - action
                                }
                            )
                            Text("${action.emoji} ${action.label}", modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    Button(
                        onClick = {
                            onSave(Macro(UUID.randomUUID().toString(), name.trim(), selected.toList()))
                            name = ""
                        },
                        enabled = name.isNotBlank() && selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Simpan macro") }
                }
            }

            Text("Macro tersimpan", style = MaterialTheme.typography.titleLarge)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(macros, key = { it.id }) { macro ->
                    ElevatedCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(macro.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                macro.actions.joinToString(" → ") { it.label },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { onRunOnAll(macro) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Jalankan") }
                                TextButton(onClick = { onDelete(macro) }) { Text("Hapus") }
                            }
                        }
                    }
                }
            }
        }
    }
}
