package com.agentcouncil.gdrivesync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

data class SyncConfig(
    val localFolderUri: String = "",
    val driveFolderId: String = "",
    val driveFolderName: String = "ObsidianVault",
    val syncIntervalMinutes: Int = 15,
    val syncEnabled: Boolean = false,
    val deleteOrphans: Boolean = false,   // remove Drive files not present locally
    val syncSubfolders: Boolean = true,
)

class SyncPreferences(private val context: Context) {

    companion object {
        val LOCAL_FOLDER_URI    = stringPreferencesKey("local_folder_uri")
        val DRIVE_FOLDER_ID     = stringPreferencesKey("drive_folder_id")
        val DRIVE_FOLDER_NAME   = stringPreferencesKey("drive_folder_name")
        val SYNC_INTERVAL_MIN   = intPreferencesKey("sync_interval_minutes")
        val SYNC_ENABLED        = booleanPreferencesKey("sync_enabled")
        val DELETE_ORPHANS      = booleanPreferencesKey("delete_orphans")
        val SYNC_SUBFOLDERS     = booleanPreferencesKey("sync_subfolders")
    }

    val configFlow: Flow<SyncConfig> = context.dataStore.data.map { prefs ->
        SyncConfig(
            localFolderUri      = prefs[LOCAL_FOLDER_URI]   ?: "",
            driveFolderId       = prefs[DRIVE_FOLDER_ID]    ?: "",
            driveFolderName     = prefs[DRIVE_FOLDER_NAME]  ?: "ObsidianVault",
            syncIntervalMinutes = prefs[SYNC_INTERVAL_MIN]  ?: 15,
            syncEnabled         = prefs[SYNC_ENABLED]       ?: false,
            deleteOrphans       = prefs[DELETE_ORPHANS]     ?: false,
            syncSubfolders      = prefs[SYNC_SUBFOLDERS]    ?: true,
        )
    }

    suspend fun update(block: suspend MutablePreferences.() -> Unit) {
        context.dataStore.edit { it.block() }
    }

    suspend fun setLocalFolder(uri: String) = update { this[LOCAL_FOLDER_URI] = uri }
    suspend fun setDriveFolder(id: String, name: String) = update {
        this[DRIVE_FOLDER_ID]   = id
        this[DRIVE_FOLDER_NAME] = name
    }
    suspend fun setInterval(minutes: Int) = update { this[SYNC_INTERVAL_MIN] = minutes }
    suspend fun setSyncEnabled(enabled: Boolean) = update { this[SYNC_ENABLED] = enabled }
    suspend fun setDeleteOrphans(v: Boolean) = update { this[DELETE_ORPHANS] = v }
    suspend fun setSyncSubfolders(v: Boolean) = update { this[SYNC_SUBFOLDERS] = v }
}
