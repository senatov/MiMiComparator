/*
 * DirTreeModel — manages tree state 4 dir compare panel.
 * flat list from tree for ListView display.
 * expand/collapse by relativePath.
 * Iakov Senatov, 2026
 */
package org.senatov.model.tree

import org.senatov.model.CompareLineItem
import org.slf4j.LoggerFactory


class DirTreeModel(val roots: List<DirTreeNode>) {

    private val log = LoggerFactory.getLogger(DirTreeModel::class.java)
    private val expandedPaths = mutableSetOf<String>()


    fun toggleExpand(relativePath: String) {
        if (relativePath in expandedPaths) {
            expandedPaths.remove(relativePath)
            log.debug("collapsed: {}", relativePath)
        } else {
            expandedPaths.add(relativePath)
            log.debug("expanded: {}", relativePath)
        }
    }


    fun expandAll() {
        expandAllRecursive(roots)
        log.info("expanded all dirs, count={}", expandedPaths.size)
    }


    fun collapseAll() {
        expandedPaths.clear()
        log.info("collapsed all dirs")
    }


    fun isExpanded(relativePath: String): Boolean = relativePath in expandedPaths


    fun toFlatList(): List<CompareLineItem> {
        val result = mutableListOf<CompareLineItem>()
        for (root in roots) {
            flattenNode(root, result)
        }
        return result
    }


    private fun flattenNode(node: DirTreeNode, result: MutableList<CompareLineItem>) {
        val expanded = node.isDirectory && node.relativePath in expandedPaths
        val item = CompareLineItem(
            lineNumber = 0,
            text = node.name,
            status = node.status,
            indentLevel = node.depth,
            isDirectory = node.isDirectory,
            relativePath = node.relativePath,
            size = node.size,
            lastModifiedMs = node.lastModifiedMs,
            isExpanded = expanded
        )
        result.add(item)
        if (!expanded || !node.isDirectory) return
        for (child in node.children.sorted()) {
            flattenNode(child, result)
        }
    }


    private fun expandAllRecursive(nodes: List<DirTreeNode>) {
        for (node in nodes) {
            if (node.isDirectory) {
                expandedPaths.add(node.relativePath)
                expandAllRecursive(node.children)
            }
        }
    }
}
