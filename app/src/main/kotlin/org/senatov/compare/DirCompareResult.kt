/*
 * DirCompareResult — result of tree-based dir comparison.
 * paired DirTreeModels + diff count.
 * Iakov Senatov, 2026
 */
package org.senatov.mimicomparator.compare

import org.senatov.mimicomparator.model.tree.DirTreeModel


data class DirCompareResult(
    val leftModel: DirTreeModel,
    val rightModel: DirTreeModel,
    val diffCount: Int
) {

    val isIdentical: Boolean get() = diffCount == 0


    fun statusText(): String =
        if (isIdentical) "✅ dirs identical"
        else "≠ $diffCount differences"
}