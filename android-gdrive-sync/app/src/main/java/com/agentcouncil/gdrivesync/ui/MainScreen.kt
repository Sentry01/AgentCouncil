package com.agentcouncil.gdrivesync.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agentcouncil.gdrivesync.R
import com.agentcouncil.gdrivesync.SyncViewModel
import com.agentcouncil.gdrivesync.UiState
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: SyncViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // ---- Google Sign-In launcher ----
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            task.addOnSuccessListener { account ->
                account.email?.let { viewModel.onSignedIn(it) }
            }
        }
    }

    fun launchSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        signInLauncher.launch(client.signInIntent)
    }

    // ---- Local folder picker ----
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.setLocalFolder(it) }
    }

    // ---- Drive folder dialog state ----
    var showDriveFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("ObsidianVault") }

    // ---- Error snackbar ----
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.isSignedIn) {
                FloatingActionButton(onClick = { viewModel.syncNow() }) {
                    Icon(Icons.Default.Sync, contentDescription = stringResource(R.string.sync_now))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Auth card ----
            AccountCard(state, onSignIn = { launchSignIn() }, onSignOut = { viewModel.signOut() })

            if (state.isSignedIn) {
                // ---- Local folder card ----
                SectionCard(title = stringResource(R.string.local_folder)) {
                    if (state.config.localFolderUri.isEmpty()) {
                        Text(
                            stringResource(R.string.no_folder_selected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            state.config.localFolderUri,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            folderPickerLauncher.launch(
                                if (state.config.localFolderUri.isEmpty()) null
                                else Uri.parse(state.config.localFolderUri)
                            )
                        }
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.pick_folder))
                    }
                }

                // ---- Drive folder card ----
                SectionCard(title = stringResource(R.string.drive_folder)) {
                    if (state.config.driveFolderId.isEmpty()) {
                        Text(
                            stringResource(R.string.no_drive_folder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            state.config.driveFolderName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "ID: ${state.config.driveFolderId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        viewModel.loadDriveFolders()
                        showDriveFolderDialog = true
                    }) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.pick_drive_folder))
                    }
                }

                // ---- Settings card ----
                SectionCard(title = stringResource(R.string.settings)) {
                    // Sync toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.auto_sync),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(
                                    R.string.auto_sync_desc,
                                    state.config.syncIntervalMinutes
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.config.syncEnabled,
                            onCheckedChange = { viewModel.toggleSync(it) },
                            enabled = state.config.localFolderUri.isNotEmpty() &&
                                    state.config.driveFolderId.isNotEmpty()
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // Interval slider
                    val intervals = listOf(15, 30, 60, 120, 360)
                    var intervalIdx by remember {
                        mutableIntStateOf(
                            intervals.indexOfFirst { it >= state.config.syncIntervalMinutes }
                                .coerceAtLeast(0)
                        )
                    }
                    Text(
                        stringResource(R.string.sync_interval, intervals[intervalIdx]),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = intervalIdx.toFloat(),
                        onValueChange = { intervalIdx = it.toInt() },
                        onValueChangeFinished = { viewModel.setInterval(intervals[intervalIdx]) },
                        valueRange = 0f..(intervals.size - 1).toFloat(),
                        steps = intervals.size - 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // Subfolders toggle
                    SettingSwitch(
                        label = stringResource(R.string.sync_subfolders),
                        checked = state.config.syncSubfolders,
                        onCheckedChange = { viewModel.setSyncSubfolders(it) }
                    )

                    // Delete orphans toggle
                    SettingSwitch(
                        label = stringResource(R.string.delete_orphans),
                        checked = state.config.deleteOrphans,
                        onCheckedChange = { viewModel.setDeleteOrphans(it) }
                    )
                }

                // ---- Sync status ----
                if (state.isSyncing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // ---- Drive folder picker dialog ----
    if (showDriveFolderDialog) {
        DriveFolderDialog(
            folders = state.driveFolders,
            newFolderName = newFolderName,
            onNewFolderNameChange = { newFolderName = it },
            onSelectExisting = { id, name ->
                viewModel.selectDriveFolder(id, name)
                showDriveFolderDialog = false
            },
            onCreateNew = {
                viewModel.createAndSetDriveFolder(newFolderName)
                showDriveFolderDialog = false
            },
            onDismiss = { showDriveFolderDialog = false }
        )
    }
}

@Composable
private fun AccountCard(
    state: UiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.google_account)) {
        if (state.isSignedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.accountName ?: "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.signed_in),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
            }
        } else {
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Login, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.sign_in_with_google))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DriveFolderDialog(
    folders: List<Pair<String, String>>,
    newFolderName: String,
    onNewFolderNameChange: (String) -> Unit,
    onSelectExisting: (String, String) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_drive_folder)) },
        text = {
            Column {
                if (folders.isNotEmpty()) {
                    Text(
                        stringResource(R.string.existing_folders),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    folders.forEach { (id, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            leadingContent = { Icon(Icons.Default.Folder, null) },
                            modifier = Modifier.clickable { onSelectExisting(id, name) }
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                Text(
                    stringResource(R.string.create_new_folder),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = onNewFolderNameChange,
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onCreateNew, enabled = newFolderName.isNotBlank()) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
