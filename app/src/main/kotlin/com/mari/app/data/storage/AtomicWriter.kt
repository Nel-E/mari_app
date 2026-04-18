package com.mari.app.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val FILE_NAME = "mari_tasks.json"
private const val FILE_TMP = "mari_tasks.json.tmp"
private const val FILE_BAK = "mari_tasks.json.bak"
private const val MIME_JSON = "application/json"

class AtomicWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Writes [content] atomically to [FILE_NAME] inside [treeUri].
     * Strategy: write to .tmp → fsync → copy to canonical → delete .tmp → rotate .bak.
     * Always keeps previous .bak intact until the new canonical write succeeds,
     * so there is always at least one readable file on disk after a partial failure.
     */
    fun write(treeUri: Uri, content: String): Result<Unit> = runCatching {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw StorageError.Io(IllegalStateException("Cannot open tree URI"))

        val bytes = content.toByteArray(Charsets.UTF_8)

        // 1. Write to .tmp
        val tmp = dir.findOrCreate(FILE_TMP, MIME_JSON)
        context.contentResolver.openOutputStream(tmp.uri, "wt")?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw StorageError.Io(IllegalStateException("Cannot open output stream for $FILE_TMP"))

        // 2. Rotate existing canonical → .bak (only after .tmp is fully written)
        val existing = dir.findFile(FILE_NAME)
        if (existing != null) {
            val bak = dir.findOrCreate(FILE_BAK, MIME_JSON)
            val existingContent = context.contentResolver.openInputStream(existing.uri)?.use {
                it.readBytes()
            } ?: ByteArray(0)
            context.contentResolver.openOutputStream(bak.uri, "wt")?.use { it.write(existingContent) }
        }

        // 3. Write .tmp content to canonical file
        val canonical = dir.findOrCreate(FILE_NAME, MIME_JSON)
        context.contentResolver.openOutputStream(canonical.uri, "wt")?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw StorageError.Io(IllegalStateException("Cannot open output stream for $FILE_NAME"))

        // 4. Delete .tmp
        dir.findFile(FILE_TMP)?.delete()
    }

    fun read(treeUri: Uri): Result<String> = runCatching {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw StorageError.Io(IllegalStateException("Cannot open tree URI"))

        val canonical = dir.findFile(FILE_NAME)
        if (canonical != null) {
            val content = context.contentResolver.openInputStream(canonical.uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            if (!content.isNullOrBlank()) return@runCatching content
        }

        // Fall back to .bak — return its content so callers receive recovered data
        val bak = dir.findFile(FILE_BAK)
        if (bak != null) {
            val content = context.contentResolver.openInputStream(bak.uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            if (!content.isNullOrBlank()) {
                return@runCatching content
            }
        }

        throw StorageError.Corrupt(recovered = false)
    }

    fun exists(treeUri: Uri): Boolean {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        return dir.findFile(FILE_NAME) != null || dir.findFile(FILE_BAK) != null
    }

    private fun DocumentFile.findOrCreate(name: String, mime: String): DocumentFile =
        findFile(name) ?: createFile(mime, name)
            ?: throw StorageError.Io(IllegalStateException("Cannot create $name"))
}
