package org.senatov.compare

import org.senatov.helpers.log.LogTag
import org.senatov.model.tree.DirTreeNode
import org.slf4j.LoggerFactory

internal data class PairedTreeNodes(
    val left: MutableList<DirTreeNode> = mutableListOf(),
    val right: MutableList<DirTreeNode> = mutableListOf(),
) {
    fun add(nodes: PairedTreeNodes) {
        log.debug(LogTag.COMPARE, "add(leftNodes={}, rightNodes={})", nodes.left.size, nodes.right.size)
        left.addAll(nodes.left)
        right.addAll(nodes.right)
    }

    fun add(leftNode: DirTreeNode, rightNode: DirTreeNode) {
        log.debug(LogTag.COMPARE, "add(leftNode={}, rightNode={})", leftNode.name, rightNode.name)
        left.add(leftNode)
        right.add(rightNode)
    }

    companion object {
        private val log = LoggerFactory.getLogger(PairedTreeNodes::class.java)

        fun of(left: DirTreeNode, right: DirTreeNode): PairedTreeNodes {
            log.debug(LogTag.COMPARE, "of(left={}, right={})", left.name, right.name)
            return PairedTreeNodes(
                left = mutableListOf(left),
                right = mutableListOf(right),
            )
        }
    }
}
