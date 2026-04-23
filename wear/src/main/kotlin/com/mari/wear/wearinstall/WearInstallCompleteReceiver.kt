package com.mari.wear.wearinstall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

private const val TAG = "WearInstallCompleteReceiver"

class WearInstallCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_SUCCESS ->
                Log.i(TAG, "Wear APK installed successfully")

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // First-time install on a fresh watch (or API < 31) requires user confirmation.
                @Suppress("DEPRECATION")
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmIntent != null) {
                    context.startActivity(confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    Log.w(TAG, "STATUS_PENDING_USER_ACTION but no confirmation intent")
                }
            }

            else -> Log.w(TAG, "Wear APK install failed: status=$status, message=$message")
        }
    }
}
