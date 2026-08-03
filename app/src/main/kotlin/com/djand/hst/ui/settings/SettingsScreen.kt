package com.djand.hst.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.djand.hst.data.settings.ThemeMode
import com.djand.hst.domain.model.Equipment
import com.djand.hst.ui.format.DisplayFormat
import java.time.LocalDate

/**
 * Settings (DESIGN.md §10.9): a plain list of grouped text rows — rest timer,
 * bar weight, per-equipment increments, theme, export/import, reset cycle. No
 * icon toolbar, no nested screens; every row opens a small dialog at most.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<SettingDialog?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBackup) }

    LaunchedEffect(actionState.message, actionState.error) {
        val text = actionState.message ?: actionState.error
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val loaded = settings
        if (loaded == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { SectionHeader("Workout") }
            item {
                SettingsRow(
                    title = "Rest between sets",
                    subtitle = "${loaded.restSeconds} s",
                    onClick = { dialog = SettingDialog.Rest },
                )
            }
            item {
                SettingsRow(
                    title = "Bar weight",
                    subtitle = DisplayFormat.weight(loaded.barWeightKg),
                    onClick = { dialog = SettingDialog.BarWeight },
                )
            }

            item { SectionHeader("Weight increments") }
            equipmentRows.forEach { (equipment, label) ->
                item(key = "increment_$equipment") {
                    SettingsRow(
                        title = label,
                        subtitle = DisplayFormat.weight(loaded.incrementFor(equipment)),
                        onClick = { dialog = SettingDialog.Increment(equipment, label) },
                    )
                }
            }

            item { SectionHeader("Appearance") }
            item {
                SettingsRow(
                    title = "Theme",
                    subtitle = themeLabel(loaded.themeMode),
                    onClick = { dialog = SettingDialog.Theme },
                )
            }

            item { SectionHeader("Backup") }
            item {
                SettingsRow(
                    title = "Export backup",
                    subtitle = "Save everything as a JSON file",
                    onClick = { exportLauncher.launch("hst-backup-${LocalDate.now()}.json") },
                )
            }
            item {
                SettingsRow(
                    title = "Import backup",
                    subtitle = "Replace all data from a backup file",
                    onClick = { dialog = SettingDialog.ImportConfirm },
                )
            }

            item { SectionHeader("Cycle") }
            item {
                SettingsRow(
                    title = "Reset cycle",
                    subtitle = "Erase all progress and rerun setup",
                    onClick = { dialog = SettingDialog.ResetConfirm },
                )
            }
        }
    }

    when (val shown = dialog) {
        null -> Unit
        SettingDialog.Rest -> NumberSettingDialog(
            title = "Rest between sets",
            unitLabel = "seconds",
            initial = settings?.restSeconds?.toString().orEmpty(),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.setRestSeconds(it.toInt()); dialog = null },
        )

        SettingDialog.BarWeight -> NumberSettingDialog(
            title = "Bar weight",
            unitLabel = "kg",
            initial = settings?.barWeightKg?.let(::trimNumber).orEmpty(),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.setBarWeight(it); dialog = null },
        )

        is SettingDialog.Increment -> NumberSettingDialog(
            title = "${shown.label} increment",
            unitLabel = "kg",
            initial = settings?.incrementFor(shown.equipment)?.let(::trimNumber).orEmpty(),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.setIncrement(shown.equipment, it); dialog = null },
        )

        SettingDialog.Theme -> ThemeDialog(
            current = settings?.themeMode ?: ThemeMode.SYSTEM,
            onDismiss = { dialog = null },
            onSelect = { viewModel.setThemeMode(it); dialog = null },
        )

        SettingDialog.ImportConfirm -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Import backup?") },
            text = { Text("Importing replaces ALL current data with the backup file.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    importLauncher.launch(arrayOf("application/json"))
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )

        SettingDialog.ResetConfirm -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Reset cycle?") },
            text = { Text("All sessions and progress are erased and the setup wizard runs again. Export a backup first if you want to keep your history.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    viewModel.resetAllProgress()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

private sealed interface SettingDialog {
    data object Rest : SettingDialog
    data object BarWeight : SettingDialog
    data class Increment(val equipment: Equipment, val label: String) : SettingDialog
    data object Theme : SettingDialog
    data object ImportConfirm : SettingDialog
    data object ResetConfirm : SettingDialog
}

private val equipmentRows = listOf(
    Equipment.BARBELL to "Barbell",
    Equipment.DUMBBELL to "Dumbbell",
    Equipment.MACHINE to "Machine",
    Equipment.CABLE to "Cable",
    Equipment.BODYWEIGHT to "Bodyweight",
)

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NumberSettingDialog(
    title: String,
    unitLabel: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val valid = text.toDoubleOrNull()?.let { it > 0 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(unitLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.toDouble()) },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ThemeDialog(
    current: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = null)
                        Text(
                            themeLabel(mode),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
