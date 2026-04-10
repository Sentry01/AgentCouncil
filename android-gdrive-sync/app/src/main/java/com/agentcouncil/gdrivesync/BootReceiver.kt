package com.agentcouncil.gdrivesync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reschedules the periodic sync job after the device reboots.
 * WorkManager already handles this automatically, but this receiver
 * acts as a belt-and-suspenders safety net.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i("BootReceiver", "Boot completed – checking sync schedule")
        val prefs = SyncPreferences(context)

        // goAsync so we can launch a coroutine from a BroadcastReceiver
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config      = prefs.configFlow.first()
                val accountName = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                    .getString("account_name", null)
                if (config.syncEnabled && accountName != null) {
                    SyncWorker.schedule(context, accountName, config.syncIntervalMinutes)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
