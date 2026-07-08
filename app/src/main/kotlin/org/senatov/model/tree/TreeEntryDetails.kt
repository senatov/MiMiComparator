package org.senatov.model.tree

import org.senatov.model.FileMetadata

data class TreeEntryDetails(
    val location: TreeLocation,
    val isDirectory: Boolean,
    val metadata: FileMetadata,
)
