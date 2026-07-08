package org.senatov.compare

enum class CompareMode(val displayName: String) {
    BINARY_CONTENT("Binary Content"),
    TEXT("Text"),
    SIZE("Size"),
    SIZE_AND_TIMESTAMP("Size and Timestamp");

    companion object {
        fun fromDisplayName(value: String?): CompareMode =
            entries.firstOrNull { it.displayName == value } ?: BINARY_CONTENT
    }
}
