package org.senatov.model

class CompareLineItem(
    val content: LineContent,
    val status: DiffStatus,
    val treeState: TreeDisplayState? = null,
) {
    val lineNumber: Int get() = content.number
    val text: String get() = content.text
    val indentLevel: Int get() = treeState?.details?.location?.depth ?: 0
    val isDirectory: Boolean get() = treeState?.details?.isDirectory ?: false
    val relativePath: String get() = treeState?.details?.location?.relativePath.orEmpty()
    val size: Long get() = treeState?.details?.metadata?.size ?: 0
    val lastModifiedMs: Long get() = treeState?.details?.metadata?.lastModifiedMs ?: 0
    val isExpanded: Boolean get() = treeState?.isExpanded ?: false

    fun formatted(): String {
        val marker = when (status) {
            DiffStatus.IDENTICAL -> "  "
            DiffStatus.MODIFIED -> "≠ "
            DiffStatus.ADDED -> "+ "
            DiffStatus.MISSING -> "- "
            DiffStatus.HEADER -> "# "
        }
        val indent = "  ".repeat(indentLevel)
        val disclosure = if (isDirectory) (if (isExpanded) "▼ " else "▶ ") else "  "
        val icon = if (isDirectory) "📁 " else "   "
        return "$marker$indent$disclosure$icon$text"
    }
}
