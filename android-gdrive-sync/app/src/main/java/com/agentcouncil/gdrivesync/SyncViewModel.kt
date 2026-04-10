package com.agentcouncil.gdrivesync

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val config: SyncConfig = SyncConfig(),
    val accountName: String? = null,
    val isSignedIn: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncResult: SyncResult? = null,
    val errorMessage: String? = null,
    val driveFolders: List<Pair<String, String>> = emptyList(),  // id to name
)

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs    = SyncPreferences(application)
    private val repo     = DriveRepository(application)
    private val authPrefs = application.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Load saved config
        viewModelScope.launch {
            prefs.configFlow.collect { config ->
                _uiState.update { it.copy(config = config) }
            }
        }
        // Restore account
        val savedAccount = authPrefs.getString("account_name", null)
        if (savedAccount != null) {
            _uiState.update { it.copy(accountName = savedAccount, isSignedIn = true) }
        }
    }

    fun onSignedIn(accountName: String) {
        authPrefs.edit().putString("account_name", accountName).apply()
        _uiState.update { it.copy(accountName = accountName, isSignedIn = true) }
    }

    fun signOut() {
        authPrefs.edit().remove("account_name").apply()
        _uiState.update { it.copy(accountName = null, isSignedIn = false) }
        SyncWorker.cancel(getApplication())
        viewModelScope.launch { prefs.setSyncEnabled(false) }
    }

    fun setLocalFolder(uri: Uri) {
        // Persist persistent URI permission so WorkManager can access it after reboot
        getApplication<Application>().contentResolver
            .takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        viewModelScope.launch { prefs.setLocalFolder(uri.toString()) }
    }

    fun loadDriveFolders() {
        val account = _uiState.value.accountName ?: return
        viewModelScope.launch {
            try {
                val folders = repo.listDriveFolders(account)
                _uiState.update { it.copy(driveFolders = folders) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to load Drive folders: ${e.message}") }
            }
        }
    }

    fun createAndSetDriveFolder(name: String) {
        val account = _uiState.value.accountName ?: return
        viewModelScope.launch {
            try {
                val id = repo.getOrCreateFolder(account, name)
                prefs.setDriveFolder(id, name)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to create folder: ${e.message}") }
            }
        }
    }

    fun selectDriveFolder(id: String, name: String) {
        viewModelScope.launch { prefs.setDriveFolder(id, name) }
    }

    fun setInterval(minutes: Int) {
        viewModelScope.launch { prefs.setInterval(minutes) }
    }

    fun setDeleteOrphans(v: Boolean) {
        viewModelScope.launch { prefs.setDeleteOrphans(v) }
    }

    fun setSyncSubfolders(v: Boolean) {
        viewModelScope.launch { prefs.setSyncSubfolders(v) }
    }

    fun toggleSync(enabled: Boolean) {
        val account = _uiState.value.accountName ?: return
        val config  = _uiState.value.config
        viewModelScope.launch {
            prefs.setSyncEnabled(enabled)
            if (enabled) {
                SyncWorker.schedule(getApplication(), account, config.syncIntervalMinutes)
            } else {
                SyncWorker.cancel(getApplication())
            }
        }
    }

    fun syncNow() {
        val account = _uiState.value.accountName ?: return
        _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
        SyncWorker.runNow(getApplication(), account)
        // Reset syncing flag after enqueueing – WorkManager handles the actual work
        _uiState.update { it.copy(isSyncing = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
