/*
 * CompareResult.java — result of a compare operation.
 * Carries left/right item lists, diff count, summary text.
 * Iakov Senatov, 2026
 */
package org.senatov.compare;

import org.senatov.model.CompareLineItem;

import java.util.List;


public record CompareResult(
        List<CompareLineItem> leftItems,
        List<CompareLineItem> rightItems,
        int diffCount,
        String summary
) {

    public boolean isIdentical() {
        return diffCount == 0;
    }


    public String statusText() {
        if (isIdentical()) {
            return "✅ identical";
        }
        return "≠ " + diffCount + " differences";
    }
}
