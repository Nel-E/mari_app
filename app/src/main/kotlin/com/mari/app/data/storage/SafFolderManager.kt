package com.mari.app.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.safPrefs by preferencesDataStore(name = "saf_prefs")
private val KEY_TREE_URI = stringPreferencesKey("tree_uri")

sealed interface SafGrant {
    data class Granted(val treeUri: Uri) : SafGrant
    data object Missing : SafGrant
}

interface SafSource {
    val grant: StateFlow<SafGrant>
    suspend fun init()
}

@Singleton
class SafFolderManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafSource {
    private val _grant = MutableStateFlow<SafGrant>(SafGrant.Missing)
    override val grant: StateFlow<SafGrant> = _grant.asStateFlow()

    override suspend fun init() {
        val stored = context.safPrefs.data.map { it[KEY_TREE_URI] }.first()
        if (stored != null) {
            val uri = Uri.parse(stored)
            _grant.value = if (isGrantValid(uri)) SafGrant.Granted(uri) else SafGrant.Missing
        }
    }

    suspend fun onFolderPicked(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        context.safPrefs.edit { it[KEY_TREE_URI] = treeUri.toString() }
        _grant.value = SafGrant.Granted(treeUri)
    }

    suspend fun releaseAndClear() {
        val current = _grant.value
        if (current is SafGrant.Granted) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    current.treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        context.safPrefs.edit { it.remove(KEY_TREE_URI) }
        _grant.value = SafGrant.Missing
    }

    fun checkGrant() {
        val current = _grant.value
        if (current is SafGrant.Granted && !isGrantValid(current.treeUri)) {
            _grant.value = SafGrant.Missing
        }
    }

    private fun isGrantValid(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri &&
                it.isReadPermission &&
                it.isWritePermission
        }
}
