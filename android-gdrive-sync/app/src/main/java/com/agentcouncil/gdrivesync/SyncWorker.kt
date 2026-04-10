package com.agentcouncil.gdrivesync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val TAG          = "SyncWorker"
private const val CHANNEL_ID   = "gdrive_sync"
private const val NOTIF_ID     = 1001
private const val WORK_NAME    = "gdrive_periodic_sync"

const val KEY_ACCOUNT_NAME = "account_name"

class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_sync_title))
            .setContentText(context.getString(R.string.notif_sync_running))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIF_ID, notification)
    }

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

        val prefs  = SyncPreferences(context)
        val config = prefs.configFlow.first()

        if (!config.syncEnabled) {
            Log.d(TAG, "Sync disabled – skipping")
            return Result.success()
        }
        if (config.localFolderUri.isEmpty()) {
            Log.w(TAG, "No local folder configured")
            return Result.failure()
        }
        if (config.driveFolderId.isEmpty()) {
            Log.w(TAG, "No Drive folder configured")
            return Result.failure()
        }

        val accountName = inputData.getString(KEY_ACCOUNT_NAME) ?: run {
            Log.e(TAG, "No account name in WorkerParams")
            return Result.failure()
        }

        val repo   = DriveRepository(context)
        val result = repo.syncFolder(
            accountName      = accountName,
            localFolderUri   = Uri.parse(config.localFolderUri),
            driveFolderId    = config.driveFolderId,
            recursive        = config.syncSubfolders,
            deleteOrphans    = config.deleteOrphans,
        )

        showResultNotification(result)

        Log.i(TAG, "Sync complete: $result")
        return if (result.errors == 0) Result.success() else Result.retry()
    }

    private fun createNotificationChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun showResultNotification(result: SyncResult) {
        createNotificationChannel()
        val text = context.getString(
            R.string.notif_sync_result,
            result.uploaded, result.skipped, result.errors
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_sync_title))
            .setContentText(text)
            .setSmallIcon(
                if (result.errors == 0) android.R.drawable.ic_menu_upload
                else android.R.drawable.ic_dialog_alert
            )
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID + 1, notification)
    }

    companion object {
        /** Schedule (or reschedule) the periodic sync work. */
        fun schedule(context: Context, accountName: String, intervalMinutes: Int) {
            val data = workDataOf(KEY_ACCOUNT_NAME to accountName)

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES
            )
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            Log.i(TAG, "Scheduled periodic sync every $intervalMinutes min for $accountName")
        }

        /** Run an immediate one-shot sync. */
        fun runNow(context: Context, accountName: String) {
            val data    = workDataOf(KEY_ACCOUNT_NAME to accountName)
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
            Log.i(TAG, "Enqueued immediate sync for $accountName")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Cancelled periodic sync")
        }
    }
}
