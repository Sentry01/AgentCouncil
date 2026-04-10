package com.agentcouncil.gdrivesync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "DriveRepository"
private const val MIME_FOLDER = "application/vnd.google-apps.folder"

data class SyncResult(
    val uploaded: Int = 0,
    val skipped: Int = 0,
    val deleted: Int = 0,
    val errors: Int = 0,
    val errorMessages: List<String> = emptyList(),
)

class DriveRepository(private val context: Context) {

    private fun buildDriveService(accountName: String): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        ).apply { selectedAccountName = accountName }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(context.getString(R.string.app_name)).build()
    }

    /**
     * Creates (or retrieves) the top-level Drive folder with [name] inside [parentId].
     * Pass "root" as [parentId] to create at the Drive root.
     */
    suspend fun getOrCreateFolder(
        accountName: String,
        name: String,
        parentId: String = "root"
    ): String = withContext(Dispatchers.IO) {
        val drive = buildDriveService(accountName)

        // Search for existing folder
        val query = "mimeType='$MIME_FOLDER' and name='$name' " +
                "and '$parentId' in parents and trashed=false"
        val existing = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
            .files

        if (existing.isNotEmpty()) {
            Log.d(TAG, "Found existing folder '$name': ${existing[0].id}")
            return@withContext existing[0].id
        }

        // Create new folder
        val meta = DriveFile().apply {
            this.name    = name
            mimeType     = MIME_FOLDER
            parents      = listOf(parentId)
        }
        val created = drive.files().create(meta)
            .setFields("id")
            .execute()
        Log.d(TAG, "Created folder '$name': ${created.id}")
        created.id
    }

    /**
     * Syncs all files under [localFolderUri] to the Drive folder [driveFolderId].
     * If [recursive] is true, subdirectories are also synced (matching the local tree).
     * If [deleteOrphans] is true, Drive files not present locally are deleted.
     */
    suspend fun syncFolder(
        accountName: String,
        localFolderUri: Uri,
        driveFolderId: String,
        recursive: Boolean = true,
        deleteOrphans: Boolean = false,
    ): SyncResult = withContext(Dispatchers.IO) {
        val drive = buildDriveService(accountName)
        syncDirectory(drive, localFolderUri, driveFolderId, recursive, deleteOrphans)
    }

    private fun syncDirectory(
        drive: Drive,
        localUri: Uri,
        driveFolderId: String,
        recursive: Boolean,
        deleteOrphans: Boolean,
    ): SyncResult {
        var uploaded = 0; var skipped = 0; var deleted = 0; var errors = 0
        val errorMessages = mutableListOf<String>()

        // List local entries
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            localUri,
            DocumentsContract.getTreeDocumentId(localUri)
        )
        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null
        ) ?: run {
            Log.e(TAG, "Cannot query $localUri")
            return SyncResult(errors = 1, errorMessages = listOf("Cannot query $localUri"))
        }

        // Build map of local entries: name -> (documentId, mimeType, lastModified)
        data class LocalEntry(
            val documentId: String,
            val mimeType: String,
            val lastModifiedMs: Long,
        )
        val localEntries = mutableMapOf<String, LocalEntry>()
        cursor.use {
            val idCol   = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modCol  = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (it.moveToNext()) {
                localEntries[it.getString(nameCol)] = LocalEntry(
                    documentId    = it.getString(idCol),
                    mimeType      = it.getString(mimeCol),
                    lastModifiedMs = it.getLong(modCol),
                )
            }
        }

        // List existing Drive files in this folder
        val driveFiles = mutableMapOf<String, DriveFile>()
        var pageToken: String? = null
        do {
            val result = drive.files().list()
                .setQ("'$driveFolderId' in parents and trashed=false")
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name, mimeType, modifiedTime)")
                .setPageToken(pageToken)
                .execute()
            result.files.forEach { driveFiles[it.name] = it }
            pageToken = result.nextPageToken
        } while (pageToken != null)

        // Upload / update local files to Drive
        for ((name, local) in localEntries) {
            try {
                val isDir = local.mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                if (isDir && recursive) {
                    // Recurse into subdirectory
                    val subDriveId = if (driveFiles.containsKey(name)) {
                        driveFiles[name]!!.id
                    } else {
                        val meta = DriveFile().apply {
                            this.name = name
                            mimeType  = MIME_FOLDER
                            parents   = listOf(driveFolderId)
                        }
                        drive.files().create(meta).setFields("id").execute().id
                    }
                    val subUri = DocumentsContract.buildDocumentUriUsingTree(
                        localUri, local.documentId
                    )
                    val subResult = syncDirectory(drive, subUri, subDriveId, recursive, deleteOrphans)
                    uploaded += subResult.uploaded
                    skipped  += subResult.skipped
                    deleted  += subResult.deleted
                    errors   += subResult.errors
                    errorMessages += subResult.errorMessages
                    continue
                }

                if (isDir) continue  // not recursing, skip dirs

                val existingDrive = driveFiles[name]
                if (existingDrive != null) {
                    // Compare modification times – skip if Drive is up to date
                    val driveModMs = existingDrive.modifiedTime?.value ?: 0L
                    if (local.lastModifiedMs <= driveModMs) {
                        skipped++
                        continue
                    }
                    // Update existing file
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(
                        localUri, local.documentId
                    )
                    context.contentResolver.openInputStream(docUri)?.use { stream ->
                        val content = InputStreamContent(local.mimeType, stream)
                        drive.files().update(existingDrive.id, null, content)
                            .setFields("id, modifiedTime")
                            .execute()
                    }
                } else {
                    // Upload new file
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(
                        localUri, local.documentId
                    )
                    context.contentResolver.openInputStream(docUri)?.use { stream ->
                        val meta = DriveFile().apply {
                            this.name = name
                            parents   = listOf(driveFolderId)
                        }
                        val content = InputStreamContent(local.mimeType, stream)
                        drive.files().create(meta, content)
                            .setFields("id")
                            .execute()
                    }
                }
                uploaded++
                Log.d(TAG, "Uploaded: $name")
            } catch (e: Exception) {
                errors++
                errorMessages += "Error uploading $name: ${e.message}"
                Log.e(TAG, "Error uploading $name", e)
            }
        }

        // Optionally delete Drive files that no longer exist locally
        if (deleteOrphans) {
            for ((name, driveFile) in driveFiles) {
                if (name !in localEntries) {
                    try {
                        drive.files().delete(driveFile.id).execute()
                        deleted++
                        Log.d(TAG, "Deleted orphan: $name")
                    } catch (e: Exception) {
                        errors++
                        errorMessages += "Error deleting $name: ${e.message}"
                        Log.e(TAG, "Error deleting orphan $name", e)
                    }
                }
            }
        }

        SyncResult(uploaded, skipped, deleted, errors, errorMessages)
    }

    /**
     * Lists all Drive folders accessible by the account (top-level only, for the picker).
     */
    suspend fun listDriveFolders(accountName: String): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val drive = buildDriveService(accountName)
            val result = drive.files().list()
                .setQ("mimeType='$MIME_FOLDER' and 'root' in parents and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setOrderBy("name")
                .execute()
            result.files.map { it.id to it.name }
        }
}
