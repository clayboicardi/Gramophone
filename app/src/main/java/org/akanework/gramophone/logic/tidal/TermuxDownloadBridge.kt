package org.akanework.gramophone.logic.tidal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.widget.Toast
import androidx.media3.common.util.Log

object TermuxDownloadBridge {

    private const val TAG = "TermuxDownloadBridge"
    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val TIDDL_PATH = "/data/data/com.termux/files/usr/bin/tiddl"
    private const val DOWNLOAD_DIR = "/storage/emulated/0/Music/tiddl"

    fun downloadAlbum(context: Context, albumId: String, albumTitle: String) {
        executeDownload(context, "album/$albumId", albumTitle)
    }

    fun downloadTrack(context: Context, trackId: String, trackTitle: String) {
        executeDownload(context, "track/$trackId", trackTitle)
    }

    private fun executeDownload(context: Context, resource: String, displayName: String) {
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", TIDDL_PATH)
            putExtra(
                "com.termux.RUN_COMMAND_ARGUMENTS",
                arrayOf("download", "url", resource, "-p", DOWNLOAD_DIR)
            )
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Toast.makeText(
                context,
                "Downloading \"$displayName\" via tiddl...",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Termux download", e)
            Toast.makeText(
                context,
                "Termux not available. Install Termux and enable external apps.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun triggerMediaScan(context: Context) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(DOWNLOAD_DIR),
            null
        ) { path, uri ->
            Log.d(TAG, "Scanned: $path -> $uri")
        }
    }

    fun isTermuxAvailable(context: Context): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
