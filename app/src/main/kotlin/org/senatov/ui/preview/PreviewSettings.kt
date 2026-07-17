package org.senatov.ui.preview

internal enum class IgnoreDifferences(val label: String) {
    NONE("None"), TRIM_WHITESPACES("Trim whitespaces"), WHITESPACES("Ignore whitespaces"), WHITESPACES_AND_EMPTY_LINES("Ignore whitespaces and empty lines"), FORMATTING("Ignore formatting"),
}

internal enum class DifferenceHighlighting(val label: String, val supported: Boolean = true) {
    LINES("Lines"), WORDS("Words", supported = false), SPLIT_CHANGES("Split changes", supported = false), CHARACTERS("Characters", supported = false), NONE("None"),
}

internal class PreviewSettings {
    var alignChanges: Boolean = true
    var ignoreDifferences: IgnoreDifferences = IgnoreDifferences.NONE
    var highlighting: DifferenceHighlighting = DifferenceHighlighting.LINES

    fun linesMatch(left: String?, right: String?): Boolean {
        if (left == null || right == null) return left == right
        return normalize(left) == normalize(right)
    }

    private fun normalize(line: String): String = when (ignoreDifferences) {
        IgnoreDifferences.NONE -> line
        IgnoreDifferences.TRIM_WHITESPACES -> line.trim()
        IgnoreDifferences.WHITESPACES, IgnoreDifferences.WHITESPACES_AND_EMPTY_LINES -> line.filterNot(Char::isWhitespace)
        IgnoreDifferences.FORMATTING -> line.trim().replace(WHITESPACE_RUN, " ")
    }

    private companion object {
        val WHITESPACE_RUN = Regex("\\s+")
    }
}