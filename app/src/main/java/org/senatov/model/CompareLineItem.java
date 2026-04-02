/*
 * CompareLineItem — one row in the compare list.
 * Carries text + diff status for cell factory coloring.
 */
package org.senatov.model;

public record CompareLineItem(
    int lineNumber,
    String text,
    DiffStatus status
) {

    public enum DiffStatus {
        IDENTICAL,   // lines match
        MODIFIED,    // lines differ
        ADDED,       // only on this side
        MISSING,     // absent on this side
        HEADER       // section separator / dir name
    }


    public String formatted() {
        String marker = switch (status) {
            case IDENTICAL -> "  ";
            case MODIFIED  -> "≠ ";
            case ADDED     -> "+ ";
            case MISSING   -> "- ";
            case HEADER    -> "# ";
        };
        if (lineNumber > 0) {
            return String.format("%s%4d │ %s", marker, lineNumber, text);
        }
        return marker + text;
    }
}
