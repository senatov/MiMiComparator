/*
 * CompareResult — result of compare op.
 * left/right item lists, diff count, summary.
 * Iakov Senatov, 2026
 */
package org.senatov.compare

import org.senatov.model.CompareLineItem


data class CompareResult(
    val leftItems: List<CompareLineItem>,
    val rightItems: List<CompareLineItem>,
    val diffCount: Int,
    val summary: String
) {

    val isIdentical: Boolean get() = diffCount == 0


    fun statusText(): String =
        if (isIdentical) "✅ identical"
        else "≠ $diffCount differences"
}
