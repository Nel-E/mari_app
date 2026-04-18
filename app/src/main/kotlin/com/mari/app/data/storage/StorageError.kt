package com.mari.app.data.storage

sealed class StorageError : Exception() {
    data object NoGrant : StorageError()
    data class Corrupt(val recovered: Boolean, override val cause: Throwable? = null) : StorageError()
    data class Io(override val cause: Throwable) : StorageError()
    data class Migration(override val cause: Throwable) : StorageError()
}
