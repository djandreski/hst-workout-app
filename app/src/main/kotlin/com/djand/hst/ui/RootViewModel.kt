package com.djand.hst.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djand.hst.data.settings.AppSettings
import com.djand.hst.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Root-level state: the theme to apply and whether the setup wizard must run.
 * `null` means "not loaded yet" so the app never flashes the wrong screen.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .map { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
