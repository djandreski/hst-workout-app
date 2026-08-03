package com.djand.hst.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.djand.hst.ui.theme.HstAttention

/**
 * History (DESIGN.md §10.8): a reverse-chronological list of completed sessions
 * (workout letter, date, duration), each expandable to its per-set detail. No
 * calendar view, no tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expandedIds = remember { mutableStateListOf<Long>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.sessions.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No completed workouts yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(
                    count = state.sessions.size,
                    key = { state.sessions[it].id },
                ) { index ->
                    val session = state.sessions[index]
                    SessionCard(
                        session = session,
                        expanded = session.id in expandedIds,
                        onToggle = {
                            if (session.id in expandedIds) {
                                expandedIds.remove(session.id)
                            } else {
                                expandedIds.add(session.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: HistoryViewModel.SessionUi,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        session.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        session.exercises.forEachIndexed { index, exercise ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            if (exercise.skipped) {
                                Text(
                                    "Skipped",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    repsLine(exercise),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "70 kg × 15, 14" — missed reps in attention amber, deviating weights prefixed. */
@Composable
private fun repsLine(exercise: HistoryViewModel.ExerciseUi): AnnotatedString {
    val attention = HstAttention.attention
    val meta = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(exercise, attention, meta) {
        buildAnnotatedString {
            append(exercise.weightLabel)
            append(" × ")
            exercise.sets.forEachIndexed { index, set ->
                if (index > 0) append(", ")
                when {
                    set.skipped -> withStyle(SpanStyle(color = meta)) { append("–") }
                    set.missed -> {
                        set.weightPrefix?.let { append("$it × ") }
                        withStyle(SpanStyle(color = attention)) { append(set.repsText) }
                    }

                    else -> {
                        set.weightPrefix?.let { append("$it × ") }
                        append(set.repsText)
                    }
                }
            }
        }
    }
}
