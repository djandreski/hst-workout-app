package com.djand.hst.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.djand.hst.data.local.entity.SetStatus
import com.djand.hst.domain.model.Equipment
import com.djand.hst.domain.model.SetKind
import com.djand.hst.domain.progression.PlateCalculator
import com.djand.hst.domain.progression.WarmupCalculator
import com.djand.hst.ui.format.DisplayFormat

/**
 * The workout screen — one exercise card at a time, big check buttons, a rest
 * timer after every set, and automatic advancement to the next exercise. This is
 * the StrongLifts-style core of the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    onFinished: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val restTimer by viewModel.restTimer.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val expandedOverride = remember { mutableStateListOf<String>() }
    var warmupFor by remember { mutableStateOf<WorkoutViewModel.ExerciseUi?>(null) }
    var platesFor by remember { mutableStateOf<WorkoutViewModel.ExerciseUi?>(null) }
    var notesFor by remember { mutableStateOf<WorkoutViewModel.ExerciseUi?>(null) }

    LaunchedEffect(Unit) { viewModel.start() }

    LaunchedEffect(finished) { if (finished) onFinished() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Auto-advance: keep the first unresolved exercise on screen.
    LaunchedEffect(state.currentIndex, state.loading) {
        if (!state.loading && state.currentIndex >= 0) {
            listState.animateScrollToItem(state.currentIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Workout ${state.workout}")
                        Text(
                            "Week ${state.week} · Session ${state.sessionNumber}" +
                                if (state.isDeload) " · Deload" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinished) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("Loading…") }

            state.missing -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("This session no longer exists.") }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(
                    count = state.exercises.size,
                    key = { state.exercises[it].exerciseId },
                ) { index ->
                    val exercise = state.exercises[index]
                    val expanded = (index == state.currentIndex && !exercise.resolved) ||
                        exercise.exerciseId in expandedOverride
                    ExerciseCard(
                        exercise = exercise,
                        expanded = expanded,
                        onToggle = {
                            if (exercise.exerciseId in expandedOverride) {
                                expandedOverride.remove(exercise.exerciseId)
                            } else {
                                expandedOverride.add(exercise.exerciseId)
                            }
                        },
                        onCheckSet = viewModel::checkSet,
                        onUncheckSet = viewModel::uncheckSet,
                        onAdjustReps = viewModel::adjustReps,
                        onSkip = { viewModel.skipExercise(exercise.exerciseId) },
                        onUnskip = { viewModel.unskipExercise(exercise.exerciseId) },
                        onWarmup = { warmupFor = exercise },
                        onPlates = { platesFor = exercise },
                        onNotes = { notesFor = exercise },
                    )
                }

                if (state.allResolved) {
                    item(key = "finish") {
                        Button(
                            onClick = viewModel::finish,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                        ) {
                            Text("Finish Workout", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }

    restTimer?.let { timer ->
        RestTimerDialog(
            timer = timer,
            onSkip = viewModel::stopRest,
            onRestart = viewModel::restartRest,
        )
    }

    warmupFor?.let { exercise ->
        WarmupDialog(exercise = exercise, onDismiss = { warmupFor = null })
    }

    platesFor?.let { exercise ->
        PlatesDialog(
            exercise = exercise,
            barWeightKg = state.barWeightKg,
            onDismiss = { platesFor = null },
        )
    }

    notesFor?.let { exercise ->
        NotesDialog(
            exercise = exercise,
            onDismiss = { notesFor = null },
            onSave = { notes ->
                viewModel.saveNotes(exercise.exerciseId, notes)
                notesFor = null
            },
        )
    }
}

// ---------------------------------------------------------------- exercise card

@Composable
private fun ExerciseCard(
    exercise: WorkoutViewModel.ExerciseUi,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCheckSet: (WorkoutViewModel.SetUi) -> Unit,
    onUncheckSet: (WorkoutViewModel.SetUi) -> Unit,
    onAdjustReps: (WorkoutViewModel.SetUi, Int) -> Unit,
    onSkip: () -> Unit,
    onUnskip: () -> Unit,
    onWarmup: () -> Unit,
    onPlates: () -> Unit,
    onNotes: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        if (!expanded) {
            CollapsedExercise(exercise = exercise, onToggle = onToggle, onUnskip = onUnskip)
        } else {
            ExpandedExercise(
                exercise = exercise,
                onToggle = onToggle,
                onCheckSet = onCheckSet,
                onUncheckSet = onUncheckSet,
                onAdjustReps = onAdjustReps,
                onSkip = onSkip,
                onUnskip = onUnskip,
                onWarmup = onWarmup,
                onPlates = onPlates,
                onNotes = onNotes,
            )
        }
    }
}

@Composable
private fun CollapsedExercise(
    exercise: WorkoutViewModel.ExerciseUi,
    onToggle: () -> Unit,
    onUnskip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (exercise.skipped) {
                    "Skipped"
                } else {
                    val reps = exercise.sets.joinToString(", ") { (it.completedReps ?: 0).toString() }
                    "${DisplayFormat.exerciseLoad(exercise.equipment, exercise.firstWeightKg)} × $reps"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (exercise.skipped) {
            TextButton(onClick = onUnskip) { Text("Undo") }
        } else {
            Text("✓", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExpandedExercise(
    exercise: WorkoutViewModel.ExerciseUi,
    onToggle: () -> Unit,
    onCheckSet: (WorkoutViewModel.SetUi) -> Unit,
    onUncheckSet: (WorkoutViewModel.SetUi) -> Unit,
    onAdjustReps: (WorkoutViewModel.SetUi, Int) -> Unit,
    onSkip: () -> Unit,
    onUnskip: () -> Unit,
    onWarmup: () -> Unit,
    onPlates: () -> Unit,
    onNotes: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle),
            )
            if (exercise.isPr) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        "PR",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Text(
            "${DisplayFormat.exerciseLoad(exercise.equipment, exercise.firstWeightKg)} × ${exercise.targetReps}",
            style = MaterialTheme.typography.displaySmall,
        )

        exercise.previousSummary?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        exercise.notes?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                "Notes: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (exercise.skipped) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Skipped", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onUnskip) { Text("Undo skip") }
            }
        } else {
            exercise.sets.forEach { set ->
                SetRow(
                    exercise = exercise,
                    set = set,
                    onCheck = { onCheckSet(set) },
                    onUncheck = { onUncheckSet(set) },
                    onAdjust = { delta -> onAdjustReps(set, delta) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onWarmup) { Text("Warm-up") }
            if (exercise.equipment == Equipment.BARBELL) {
                TextButton(onClick = onPlates) { Text("Plates") }
            }
            TextButton(onClick = onNotes) { Text("Notes") }
            Spacer(Modifier.weight(1f))
            if (!exercise.skipped) {
                TextButton(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

@Composable
private fun SetRow(
    exercise: WorkoutViewModel.ExerciseUi,
    set: WorkoutViewModel.SetUi,
    onCheck: () -> Unit,
    onUncheck: () -> Unit,
    onAdjust: (Int) -> Unit,
) {
    val kindLabel = when (set.kind) {
        SetKind.TOP -> " · Top set"
        SetKind.BACK_OFF -> " · Back-off"
        SetKind.NORMAL -> ""
    }
    when (set.status) {
        SetStatus.DONE -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Set ${set.setIndex + 1}$kindLabel",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            val reps = set.completedReps ?: set.targetReps
            val missed = reps < set.minReps
            OutlinedButton(onClick = { onAdjust(-1) }) { Text("−") }
            Text(
                "$reps",
                style = MaterialTheme.typography.headlineSmall,
                color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            OutlinedButton(onClick = { onAdjust(1) }) { Text("+") }
            TextButton(onClick = onUncheck) { Text("Undo") }
        }

        else -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Set ${set.setIndex + 1}$kindLabel", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${DisplayFormat.exerciseLoad(exercise.equipment, set.weightKg)} × ${set.targetReps}",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Button(
                onClick = onCheck,
                modifier = Modifier.height(64.dp),
            ) {
                Text("Done", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

// ---------------------------------------------------------------------- dialogs

@Composable
private fun RestTimerDialog(
    timer: WorkoutViewModel.RestTimerUi,
    onSkip: () -> Unit,
    onRestart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Rest") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${timer.remainingSeconds}",
                    style = MaterialTheme.typography.displayLarge,
                )
                Text("seconds", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = {
                        if (timer.totalSeconds == 0) 0f
                        else timer.remainingSeconds.toFloat() / timer.totalSeconds
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSkip) { Text("Skip") } },
        dismissButton = { TextButton(onClick = onRestart) { Text("Restart") } },
    )
}

@Composable
private fun WarmupDialog(
    exercise: WorkoutViewModel.ExerciseUi,
    onDismiss: () -> Unit,
) {
    val warmup = remember(exercise) {
        if (exercise.firstWeightKg > 0) {
            WarmupCalculator.calculate(exercise.firstWeightKg, exercise.incrementKg)
        } else {
            emptyList()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Warm-up — ${exercise.name}") },
        text = {
            if (warmup.isEmpty()) {
                Text("No warm-up needed at this weight.")
            } else {
                Column {
                    warmup.forEach { set ->
                        Text(
                            "${DisplayFormat.weight(set.weightKg)} × ${set.reps}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun PlatesDialog(
    exercise: WorkoutViewModel.ExerciseUi,
    barWeightKg: Double,
    onDismiss: () -> Unit,
) {
    val load = remember(exercise, barWeightKg) {
        PlateCalculator.calculate(exercise.firstWeightKg, barWeightKg)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plates — ${DisplayFormat.weight(exercise.firstWeightKg)}") },
        text = {
            Column {
                if (load.perSide.isEmpty()) {
                    Text("Empty bar (${DisplayFormat.weight(barWeightKg)})")
                } else {
                    Text(
                        "Per side: " + load.perSide.joinToString(" + ") { DisplayFormat.weight(it) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (load.remainderKg > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Closest loadable: ${DisplayFormat.weight(load.totalKg)} " +
                            "(${DisplayFormat.weight(load.remainderKg)} short)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun NotesDialog(
    exercise: WorkoutViewModel.ExerciseUi,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable(exercise.exerciseId) { mutableStateOf(exercise.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notes — ${exercise.name}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Optional notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
