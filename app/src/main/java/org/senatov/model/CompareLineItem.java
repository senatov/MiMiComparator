/*
 * CompareLineItem — one row in the compare list.
 * Carries text + diff status + tree indent for dir mode.
 * Supports expandable directories with disclosure triangle.
 * Iakov Senatov, 2026
 */
package org.senatov.model;

import lombok.Getter;
import lombok.Setter;


@Getter
public final class CompareLineItem {

    private final int lineNumber;
    private final String text;
    private final DiffStatus status;
    private final int indentLevel;
    private final boolean directory;
    private final String relativePath;
    private final long size;
    private final long lastModifiedMs;
    @Setter
    private boolean expanded;

    public enum DiffStatus {
        IDENTICAL,
        MODIFIED,
        ADDED,
        MISSING,
        HEADER
    }


    public CompareLineItem(int lineNumber, String text, DiffStatus status) {
        this(lineNumber, text, status, 0, false, "", 0, 0);
    }


    public CompareLineItem(int lineNumber, String text, DiffStatus status,
                           int indentLevel, boolean directory, String relativePath) {
        this(lineNumber, text, status, indentLevel, directory, relativePath, 0, 0);
    }


    public CompareLineItem(int lineNumber, String text, DiffStatus status,
                           int indentLevel, boolean directory, String relativePath,
                           long size, long lastModifiedMs) {
        this.lineNumber = lineNumber;
        this.text = text;
        this.status = status;
        this.indentLevel = indentLevel;
        this.directory = directory;
        this.relativePath = relativePath;
        this.size = size;
        this.lastModifiedMs = lastModifiedMs;
        this.expanded = false;
    }


    public String formatted() {
        String marker = switch (status) {
            case IDENTICAL -> "  ";
            case MODIFIED  -> "≠ ";
            case ADDED     -> "+ ";
            case MISSING   -> "- ";
            case HEADER    -> "# ";
        };

        String indent = "  ".repeat(indentLevel);
        String disclosure = directory ? (expanded ? "▼ " : "▶ ") : "  ";
        String icon = directory ? "📁 " : "   ";

        if (lineNumber > 0) {
            return String.format("%s%s%s%s%s", marker, indent, disclosure, icon, text);
        }

        return marker + indent + disclosure + icon + text;
    }
}
