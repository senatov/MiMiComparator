/*
 * DirCompareResult.java — result of tree-based directory comparison.
 * Holds paired DirTreeModels and diff count.
 * Iakov Senatov, 2026
 */
package org.senatov.compare;

import org.senatov.model.tree.DirTreeModel;


public record DirCompareResult(
        DirTreeModel leftModel,
        DirTreeModel rightModel,
        int diffCount
) {

    public boolean isIdentical() {
        return diffCount == 0;
    }


    public String statusText() {
        if (isIdentical()) {
            return "✅ dirs identical";
        }
        return "≠ " + diffCount + " differences";
    }
}
