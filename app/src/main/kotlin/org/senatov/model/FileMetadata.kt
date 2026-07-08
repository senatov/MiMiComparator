package org.senatov.model

data class FileMetadata(
    val size: Long,
    val lastModifiedMs: Long,
) {
    companion object {
        val EMPTY = FileMetadata(size = 0, lastModifiedMs = 0)
    }
}
