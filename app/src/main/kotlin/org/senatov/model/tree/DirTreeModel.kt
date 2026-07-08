/*
 * DirTreeModel — manages tree state 4 dir compare panel.
 * flat list from tree for ListView display.
 * expand/collapse by relativePath.
 * Iakov Senatov, 2026
 */
package org.senatov.model.tree

import org.senatov.helpers.log.LogTag
import org.senatov.model.CompareLineItem
import org.senatov.model.LineContent
import org.senatov.model.TreeDisplayState
import org.slf4j.LoggerFactory


class DirTreeModel(val roots: List<DirTreeNode>) {

    private val log = LoggerFactory.getLogger(DirTreeModel::class.java)
    private val expandedPaths = mutableSetOf<String>()


    fun toggleExpand(relativePath: String) {
        log.debug(LogTag.UI, "toggleExpand(relativePath={})", relativePath)
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
        log.debug(LogTag.UI, "isExpanded(relativePath={})", relativePath)
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
        log.debug(
            LogTag.UI,
            "flattenNode(relativePath={}, resultSize={})",
            node.relativePath,
            result.size
        )
        val expanded = node.isDirectory && node.relativePath in expandedPaths
        val item = CompareLineItem(
            content = LineContent(number = 0, text = node.name),
            status = node.status,
            treeState = TreeDisplayState(details = node.details, isExpanded = expanded),
        )
        result.add(item)
        if (!expanded || !node.isDirectory) return
        for (child in node.children.sorted()) {
            flattenNode(child, result)
        }
    }


    private fun expandAllRecursive(nodes: List<DirTreeNode>) {
        log.debug(LogTag.UI, "expandAllRecursive(nodes={})", nodes.size)
        for (node in nodes) {
            if (node.isDirectory) {
                expandedPaths.add(node.relativePath)
                expandAllRecursive(node.children)
            }
        }
    }
}
