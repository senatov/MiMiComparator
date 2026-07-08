package org.senatov.model.tree

import org.senatov.model.DiffStatus

class DirTreeNode(
    val identity: TreeNodeIdentity,
    val details: TreeEntryDetails,
    val children: MutableList<DirTreeNode> = mutableListOf(),
) : Comparable<DirTreeNode> {
    val name: String get() = identity.name
    val status: DiffStatus get() = identity.status
    val relativePath: String get() = details.location.relativePath
    val depth: Int get() = details.location.depth
    val isDirectory: Boolean get() = details.isDirectory
    val size: Long get() = details.metadata.size
    val lastModifiedMs: Long get() = details.metadata.lastModifiedMs

    override fun compareTo(other: DirTreeNode): Int {
        if (isDirectory != other.isDirectory) {
            return if (isDirectory) -1 else 1
        }
        return name.compareTo(other.name, ignoreCase = true)
    }
}
