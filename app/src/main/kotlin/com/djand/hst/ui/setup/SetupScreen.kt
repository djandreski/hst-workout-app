package com.djand.hst.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.djand.hst.domain.model.Equipment

/**
 * First-launch setup wizard: one scrollable list of every exercise, grouped by
 * workout A/B/C, with two large numeric fields (working weight x reps) each.
 * Submitting generates the first cycle; the root composable then swaps in Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Setup") }) },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
            Text(
                text = "Enter your approximate 15-rep max for each exercise " +
                    "(not your 1RM). The app derives your 10RM and 5RM and generates " +
                    "the full 24-workout HST cycle.",
                style = MaterialTheme.typography.bodyLarge,
            )
            }

            for (group in state.groups) {
                item(key = "header_${group.letter}") {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Workout ${group.letter}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    }
                }

                items(
                    count = group.entries.size,
                    key = { index -> group.entries[index].exerciseId },
                ) { index ->
                    val entry = group.entries[index]
                    SetupExerciseRow(
                        entry = entry,
                        onWeightChange = { viewModel.onWeightChange(entry.exerciseId, it) },
                        onRepsChange = { viewModel.onRepsChange(entry.exerciseId, it) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                if (state.error != null) {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = viewModel::startProgram,
                    enabled = state.canSubmit && !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator()
                    } else {
                        Text("Create my program", style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (!state.canSubmit) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Fill in a weight and rep count for every exercise to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SetupExerciseRow(
    entry: SetupViewModel.ExerciseEntry,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(entry.name, style = MaterialTheme.typography.titleMedium)
        if (entry.equipment == Equipment.BODYWEIGHT) {
            Text(
                "Added weight only — 0 kg means bodyweight alone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = entry.weightText,
                onValueChange = onWeightChange,
                label = { Text(if (entry.equipment == Equipment.BODYWEIGHT) "Added kg" else "Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text("×", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = entry.repsText,
                onValueChange = onRepsChange,
                label = { Text("Reps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.width(96.dp),
            )
        }
    }
}
