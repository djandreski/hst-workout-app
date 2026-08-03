package com.djand.hst.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.djand.hst.domain.progression.ProgressionEngine
import com.djand.hst.ui.format.DisplayFormat
import com.djand.hst.ui.theme.HstAttention
import com.djand.hst.ui.theme.HstButtonShape

/**
 * Home: today's workout with a big Start button, cycle progress, banners
 * (deload, pull-up suggestion), last workout, and the bodyweight quick-log.
 * This is the only hub of the app — five seconds from launch to lifting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartWorkout: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showBodyweightDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(actionState.error) {
        val error = actionState.error
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HST") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ----------------------------------------------------- today's workout
            item {
                if (state.cycleFinished) {
                    CycleFinishedCard(
                        busy = actionState.busy,
                        onStartNextCycle = viewModel::startNextCycle,
                    )
                } else if (state.upcoming != null) {
                    TodayCard(
                        upcoming = state.upcoming!!,
                        onStart = { onStartWorkout(state.upcoming!!.sessionId) },
                    )
                }
            }

            if (state.upcoming?.isDeload == true) {
                item {
                    BannerCard(
                        title = "Time for Deload",
                        body = "85% of your last weights, half the sets, no failure.",
                    )
                }
            }

            if (state.pullUpSuggestion) {
                item {
                    BannerCard(
                        title = "Pull-ups",
                        body = "3 sets of 8 reached — start adding weight.",
                    )
                }
            }

            // -------------------------------------------------------- cycle status
            state.upcoming?.let { upcoming ->
                item {
                    CycleProgressCard(
                        cycleNumber = state.cycleNumber ?: 1,
                        week = upcoming.week,
                        isDeload = upcoming.isDeload,
                        completedSessions = state.completedSessions,
                    )
                }
            }

            // --------------------------------------------------------- last workout
            state.lastWorkout?.let { last ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    "Last workout",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Workout ${last.workout} · ${last.date}" +
                                        (last.duration?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            TextButton(onClick = onOpenHistory) { Text("History") }
                        }
                    }
                }
            }

            // ----------------------------------------------------------- bodyweight
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                "Bodyweight",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                state.latestBodyweightKg?.let(DisplayFormat::weight) ?: "—",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        TextButton(onClick = { showBodyweightDialog = true }) { Text("Log") }
                    }
                }
            }

            // --------------------------------------------------------------- stats
            item {
                OutlinedButton(
                    onClick = onOpenStats,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Statistics")
                }
            }
        }
    }

    if (showBodyweightDialog) {
        BodyweightDialog(
            onDismiss = { showBodyweightDialog = false },
            onConfirm = { text ->
                viewModel.logBodyweight(text)
                showBodyweightDialog = false
            },
        )
    }
}

@Composable
private fun TodayCard(upcoming: HomeViewModel.UpcomingUi, onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (upcoming.isDeload) "Deload" else "Today's Workout",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Workout ${upcoming.workout}",
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                "Week ${upcoming.week} · Session ${upcoming.sessionNumber}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onStart,
                shape = HstButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                Text(
                    if (upcoming.inProgress) "Resume Workout" else "Start Workout",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}

@Composable
private fun CycleFinishedCard(busy: Boolean, onStartNextCycle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Cycle complete", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your achieved weights become the starting point of the next cycle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onStartNextCycle,
                enabled = !busy,
                shape = HstButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                Text(
                    if (busy) "Calculating…" else "Start Next Cycle",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}

@Composable
private fun BannerCard(title: String, body: String) {
    // Notices, not primary actions: the accent is amber "attention" (DESIGN.md §10.10).
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = HstAttention.attention)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CycleProgressCard(
    cycleNumber: Int,
    week: Int,
    isDeload: Boolean,
    completedSessions: Int,
) {
    val totalSessions = ProgressionEngine.SESSIONS_PER_CYCLE + ProgressionEngine.WORKOUTS_PER_ROTATION
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Cycle $cycleNumber",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (isDeload) "Deload week" else "Week $week of 8",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (completedSessions.toFloat() / totalSessions).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$completedSessions of $totalSessions sessions done",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BodyweightDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log bodyweight") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.toDoubleOrNull()?.let { it > 0 } == true) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
