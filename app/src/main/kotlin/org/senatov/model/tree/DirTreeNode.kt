/*
 * DirTreeNode — single node in dir comparison tree.
 * name, path, attrs, children, expand state.
 * Iakov Senatov, 2026
 */
package org.senatov.mimicomparator.model.tree

import org.senatov.mimicomparator.model.CompareLineItem.DiffStatus


class DirTreeNode(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModifiedMs: Long,
    val status: DiffStatus,
    val depth: Int,
    var isExpanded: Boolean = false,
    val children: MutableList<DirTreeNode> = mutableListOf()
) : Comparable<DirTreeNode> {


    fun addChild(child: DirTreeNode) {
        children.add(child)
    }


    override fun compareTo(other: DirTreeNode): Int {
        if (this.isDirectory != other.isDirectory) {
            return if (this.isDirectory) -1 else 1
        }
        return this.name.compareTo(other.name, ignoreCase = true)
    }
}