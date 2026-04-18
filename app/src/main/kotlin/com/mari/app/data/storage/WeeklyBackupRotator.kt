package com.mari.app.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mari.shared.domain.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import javax.inject.Inject

private const val BACKUP_DIR = "backups"
private const val MAX_WEEKLY_BACKUPS = 8
private const val MIME_JSON = "application/json"
private val BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-'W'ww")

class WeeklyBackupRotator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    /**
     * On the first save of each ISO-week Sunday UTC, copies the canonical content
     * to backups/mari_tasks.YYYY-WW.json and prunes to [MAX_WEEKLY_BACKUPS].
     */
    fun runIfNeeded(treeUri: Uri, content: String) {
        val now = clock.nowUtc().atOffset(ZoneOffset.UTC)
        if (now.dayOfWeek != DayOfWeek.SUNDAY) return

        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return
        val backupDir = dir.findFile(BACKUP_DIR)
            ?: dir.createDirectory(BACKUP_DIR)
            ?: return

        val weekLabel = BACKUP_DATE_FORMAT.format(now)
        val backupName = "mari_tasks.$weekLabel.json"

        // Idempotent — skip if this week's backup already exists
        if (backupDir.findFile(backupName) != null) return

        val backupFile = backupDir.createFile(MIME_JSON, backupName) ?: return
        context.contentResolver.openOutputStream(backupFile.uri, "wt")?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }

        pruneOldBackups(backupDir)
    }

    private fun pruneOldBackups(backupDir: DocumentFile) {
        val files = backupDir.listFiles()
            .filter { it.name?.startsWith("mari_tasks.") == true && it.name?.endsWith(".json") == true }
            .sortedByDescending { it.name }

        if (files.size > MAX_WEEKLY_BACKUPS) {
            files.drop(MAX_WEEKLY_BACKUPS).forEach { it.delete() }
        }
    }
}
