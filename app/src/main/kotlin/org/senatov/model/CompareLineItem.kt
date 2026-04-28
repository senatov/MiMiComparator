/*
 * CompareLineItem — one row in compare list.
 * txt + diff status + tree indent 4 dir mode.
 * expandable dirs w/ disclosure triangle.
 * Iakov Senatov, 2026
 */
package org.senatov.mimicomparator.model


class CompareLineItem(
    val lineNumber: Int,
    val text: String,
    val status: DiffStatus,
    val indentLevel: Int = 0,
    val isDirectory: Boolean = false,
    val relativePath: String = "",
    val size: Long = 0L,
    val lastModifiedMs: Long = 0L,
    var isExpanded: Boolean = false
) {

    enum class DiffStatus {
        IDENTICAL, MODIFIED, ADDED, MISSING, HEADER
    }


    fun formatted(): String {
        val marker = when (status) {
            DiffStatus.IDENTICAL -> "  "
            DiffStatus.MODIFIED  -> "≠ "
            DiffStatus.ADDED     -> "+ "
            DiffStatus.MISSING   -> "- "
            DiffStatus.HEADER    -> "# "
        }
        val indent = "  ".repeat(indentLevel)
        val disclosure = if (isDirectory) (if (isExpanded) "▼ " else "▶ ") else "  "
        val icon = if (isDirectory) "📁 " else "   "
        return "$marker$indent$disclosure$icon$text"
    }
}