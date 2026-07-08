/*
 * DirTreeModel — manages tree state 4 dir compare panel.
 * flat list from tree for ListView display.
 * expand/collapse by relativePath.
 * Iakov Senatov, 2026
 */
package org.senatov.model.tree

import org.senatov.helpers.log.LogHelper
import org.senatov.helpers.log.LogTag
import org.senatov.model.CompareLineItem
import org.slf4j.LoggerFactory


class DirTreeModel(val roots: List<DirTreeNode>) {

    private val log = LoggerFactory.getLogger(DirTreeModel::class.java)
    private val expandedPaths = mutableSetOf<String>()


    fun toggleExpand(relativePath: String) {
        LogHelper.enter(log, LogTag.UI, "toggleExpand", "relativePath" to relativePath)
        if (relativePath in expandedPaths) {
            expandedPaths.remove(relativePath)
            log.debug(LogTag.UI, "collapsed {}", relativePath)
        } else {
            expandedPaths.add(relativePath)
            log.debug(LogTag.UI, "expanded {}", relativePath)
        }
    }


    fun expandAll() {
        log.debug(LogTag.UI, "expandAll()")
        expandAllRecursive(roots)
        log.info(LogTag.UI, "expanded all count={}", expandedPaths.size)
    }


    fun collapseAll() {
        log.debug(LogTag.UI, "collapseAll()")
        expandedPaths.clear()
        log.info(LogTag.UI, "collapsed all")
    }


    fun isExpanded(relativePath: String): Boolean {
        LogHelper.enter(log, LogTag.UI, "isExpanded", "relativePath" to relativePath)
        return relativePath in expandedPaths
    }


    fun toFlatList(): List<CompareLineItem> {
        log.debug(LogTag.UI, "toFlatList()")
        val result = mutableListOf<CompareLineItem>()
        for (root in roots) {
            flattenNode(root, result)
        }
        return result
    }


    private fun flattenNode(node: DirTreeNode, result: MutableList<CompareLineItem>) {
        LogHelper.enter(log, LogTag.UI, "flattenNode", "node" to node, "resultSize" to result.size)
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
        LogHelper.enter(log, LogTag.UI, "expandAllRecursive", "nodes" to nodes)
        for (node in nodes) {
            if (node.isDirectory) {
                expandedPaths.add(node.relativePath)
                expandAllRecursive(node.children)
            }
        }
    }
}
