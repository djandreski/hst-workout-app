package com.djand.hst.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.djand.hst.data.local.entity.SetStatus
import com.djand.hst.domain.model.Equipment
import com.djand.hst.domain.model.SetKind
import com.djand.hst.domain.progression.PlateCalculator
import com.djand.hst.domain.progression.WarmupCalculator
import com.djand.hst.ui.format.DisplayFormat
import com.djand.hst.ui.theme.HstAttention
import com.djand.hst.ui.theme.HstButtonShape
import com.djand.hst.ui.theme.RestBarColor
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The workout screen — one exercise card at a time, one-tap set pips, a docked
 * rest-timer bar after every set, and automatic advancement to the next exercise.
 * This is the StrongLifts-style core of the app (DESIGN.md §10).
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
        // The rest timer is a persistent bottom bar, not a modal dialog: the
        // exercise list stays scrollable while the countdown runs (DESIGN.md §10.3).
        bottomBar = {
            restTimer?.let { timer ->
                RestTimerBar(
                    timer = timer,
                    onSkip = viewModel::stopRest,
                    onRestart = viewModel::restartRest,
                )
            }
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
                contentPadding = PaddingValues(16.dp),
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
                            shape = HstButtonShape,
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
    // The correction stepper of one tapped "done" pip; progressive disclosure
    // instead of always-visible steppers (DESIGN.md §10.2).
    var adjustingSetId by rememberSaveable { mutableStateOf<Long?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(targetState = expanded, label = "exerciseExpand") { isExpanded ->
            if (!isExpanded) {
                CollapsedExercise(exercise = exercise, onToggle = onToggle, onUnskip = onUnskip)
            } else {
                ExpandedExercise(
                    exercise = exercise,
                    adjustingSet = exercise.sets.firstOrNull {
                        it.id == adjustingSetId && it.status == SetStatus.DONE
                    },
                    onToggle = onToggle,
                    onPipClick = { set ->
                        when (set.status) {
                            SetStatus.PENDING -> onCheckSet(set)
                            SetStatus.DONE ->
                                adjustingSetId = if (adjustingSetId == set.id) null else set.id
                            SetStatus.SKIPPED -> Unit
                        }
                    },
                    onUncheckSet = {
                        adjustingSetId = null
                        onUncheckSet(it)
                    },
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
        Text(
            exercise.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (exercise.skipped) {
            Text(
                "Skipped",
                style = MaterialTheme.typography.labelLarge,
                color = HstAttention.attention,
            )
            TextButton(onClick = onUnskip) { Text("Undo") }
        } else {
            // Compact summary line: "{sets}×{reps} {weight}" (DESIGN.md §10.4).
            Text(
                collapsedSummary(exercise),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun collapsedSummary(exercise: WorkoutViewModel.ExerciseUi): String {
    val load = DisplayFormat.exerciseLoad(exercise.equipment, exercise.firstWeightKg)
    if (!exercise.resolved) {
        return "${exercise.sets.size}×${exercise.targetReps} $load"
    }
    val allOnTarget = exercise.sets.all { (it.completedReps ?: it.targetReps) == it.targetReps }
    return if (allOnTarget) {
        "✓ ${exercise.sets.size}×${exercise.targetReps} $load"
    } else {
        val reps = exercise.sets.joinToString(", ") { (it.completedReps ?: 0).toString() }
        "✓ $load × $reps"
    }
}

@Composable
private fun ExpandedExercise(
    exercise: WorkoutViewModel.ExerciseUi,
    adjustingSet: WorkoutViewModel.SetUi?,
    onToggle: () -> Unit,
    onPipClick: (WorkoutViewModel.SetUi) -> Unit,
    onUncheckSet: (WorkoutViewModel.SetUi) -> Unit,
    onAdjustReps: (WorkoutViewModel.SetUi, Int) -> Unit,
    onSkip: () -> Unit,
    onUnskip: () -> Unit,
    onWarmup: () -> Unit,
    onPlates: () -> Unit,
    onNotes: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (exercise.isPr) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    Text(
                        "PR",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
            }
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Collapse",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // The big weight×reps hero lives only here, not in the collapsed header.
        Text(
            "${DisplayFormat.exerciseLoad(exercise.equipment, exercise.firstWeightKg)} × ${exercise.targetReps}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
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
            SetPipRow(exercise = exercise, onPipClick = onPipClick)

            adjustingSet?.let { set ->
                Spacer(Modifier.height(8.dp))
                SetCorrectionRow(
                    set = set,
                    onAdjust = { delta -> onAdjustReps(set, delta) },
                    onUncheck = { onUncheckSet(set) },
                )
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

// -------------------------------------------------------------------- set pips

/**
 * One pip per prescribed set (DESIGN.md §10.2): tap a grey pip to mark it done at
 * the target reps; tap a filled pip to reveal the inline correction stepper.
 */
@Composable
private fun SetPipRow(
    exercise: WorkoutViewModel.ExerciseUi,
    onPipClick: (WorkoutViewModel.SetUi) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        exercise.sets.forEach { set ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    setLabel(set),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SetPip(set = set, onClick = { onPipClick(set) })
                // Back-off sets differ in weight from the top set; show it under the pip.
                if (set.weightKg != exercise.firstWeightKg) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        DisplayFormat.weight(set.weightKg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun setLabel(set: WorkoutViewModel.SetUi): String = when (set.kind) {
    SetKind.TOP -> "Top set"
    SetKind.BACK_OFF -> "Back-off"
    SetKind.NORMAL -> "Set ${set.setIndex + 1}"
}

@Composable
private fun SetPip(
    set: WorkoutViewModel.SetUi,
    onClick: () -> Unit,
) {
    val done = set.status == SetStatus.DONE
    val reps = set.completedReps ?: set.targetReps
    val missed = done && reps < set.minReps

    val fill = if (done && !missed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        done && !missed -> MaterialTheme.colorScheme.onPrimary
        missed -> HstAttention.attention
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .then(if (missed) Modifier.border(2.dp, HstAttention.attention, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$reps",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (done) FontWeight.Bold else FontWeight.Normal,
            color = content,
        )
    }
}

/** The inline correction UI revealed by tapping a done pip: −/+ actual reps, or Undo. */
@Composable
private fun SetCorrectionRow(
    set: WorkoutViewModel.SetUi,
    onAdjust: (Int) -> Unit,
    onUncheck: () -> Unit,
) {
    val reps = set.completedReps ?: set.targetReps
    val missed = reps < set.minReps
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            setLabel(set),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = { onAdjust(-1) },
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Text("−") }
        Text(
            "$reps",
            style = MaterialTheme.typography.headlineSmall,
            color = if (missed) HstAttention.attention else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        OutlinedButton(
            onClick = { onAdjust(1) },
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Text("+") }
        TextButton(onClick = onUncheck) { Text("Undo") }
    }
}

// ------------------------------------------------------------------ rest timer

private val RestBarMuted = Color.White.copy(alpha = 0.65f)

/**
 * The docked rest-timer bar (DESIGN.md §10.3): near-black chrome, big tabular
 * countdown, "restart" as a small text link, skip on the right, primary progress
 * line underneath. Non-modal — the list above keeps scrolling.
 */
@Composable
private fun RestTimerBar(
    timer: WorkoutViewModel.RestTimerUi,
    onSkip: () -> Unit,
    onRestart: () -> Unit,
) {
    Surface(
        color = RestBarColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${timer.remainingSeconds}",
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Rest ${timer.totalSeconds}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RestBarMuted,
                    )
                    Text(
                        "Restart",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onRestart)
                            .padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSkip, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Skip rest",
                        tint = Color.White,
                    )
                }
            }
            LinearProgressIndicator(
                progress = {
                    if (timer.totalSeconds == 0) 0f
                    else timer.remainingSeconds.toFloat() / timer.totalSeconds
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.15f),
            )
        }
    }
}

// ---------------------------------------------------------------------- dialogs

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
                    PlateLoadBar(load.perSide)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Per side: " + load.perSide.joinToString(" + ") { trimmedKg(it) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * The plate-calculator visualization (DESIGN.md §10.6): a horizontal stack of
 * colored plate blocks, height and width scaled by denomination, standard kg
 * plate colors. One side of the bar is shown.
 */
@Composable
private fun PlateLoadBar(perSide: List<Double>) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        perSide.forEach { plate ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(plateWidth(plate))
                        .height(plateHeight(plate))
                        .then(
                            if (plate == 5.0) {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(2.dp),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clip(RoundedCornerShape(2.dp))
                        .background(plateColor(plate)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    trimmedKg(plate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Standard kg plate colors (red 25, blue 20, yellow 15, green 10, white 5). */
private fun plateColor(plate: Double): Color = when (plate) {
    25.0 -> Color(0xFFD32F2F)
    20.0 -> Color(0xFF1565C0)
    15.0 -> Color(0xFFF9A825)
    10.0 -> Color(0xFF2E7D32)
    5.0 -> Color(0xFFF5F5F5)
    2.5 -> Color(0xFF9E9E9E)
    1.25 -> Color(0xFF616161)
    else -> Color(0xFFBDBDBD)
}

private fun plateHeight(plate: Double): Dp = (18 + plate * 1.8).dp

private fun plateWidth(plate: Double): Dp = (10 + plate * 0.8).dp

private fun trimmedKg(value: Double): String =
    if (abs(value - value.roundToLong()) < 1e-9) value.roundToLong().toString() else value.toString()

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
