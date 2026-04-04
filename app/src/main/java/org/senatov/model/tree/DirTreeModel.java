/*
 * DirTreeModel.java — manages tree state for dir compare panel.
 * Builds flat list from tree for ListView display.
 * Handles expand/collapse by relativePath.
 * Iakov Senatov, 2026
 */
package org.senatov.model.tree;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.senatov.model.CompareLineItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Slf4j
public final class DirTreeModel {

    @Getter
    private final List<DirTreeNode> roots;
    private final Set<String> expandedPaths = new HashSet<>();

    public DirTreeModel(List<DirTreeNode> roots) {
        this.roots = roots;
    }


    public void toggleExpand(String relativePath) {
        if (expandedPaths.contains(relativePath)) {
            expandedPaths.remove(relativePath);
            log.debug("collapsed: {}", relativePath);
        } else {
            expandedPaths.add(relativePath);
            log.debug("expanded: {}", relativePath);
        }
    }


    public void expandAll() {
        expandAllRecursive(roots);
        log.info("expanded all dirs, count={}", expandedPaths.size());
    }


    public void collapseAll() {
        expandedPaths.clear();
        log.info("collapsed all dirs");
    }


    public boolean isExpanded(String relativePath) {
        return expandedPaths.contains(relativePath);
    }


    public List<CompareLineItem> toFlatList() {
        List<CompareLineItem> result = new ArrayList<>();
        for (DirTreeNode root : roots) {
            flattenNode(root, result);
        }
        return result;
    }


    private void flattenNode(DirTreeNode node, List<CompareLineItem> result) {
        boolean expanded = node.isDirectory() && expandedPaths.contains(node.getRelativePath());

        CompareLineItem item = new CompareLineItem(
                0, node.getName(), node.getStatus(),
                node.getDepth(), node.isDirectory(), node.getRelativePath(),
                node.getSize(), node.getLastModifiedMs()
        );
        item.setExpanded(expanded);
        result.add(item);

        if (!expanded || !node.isDirectory()) {
            return;
        }

        List<DirTreeNode> sorted = new ArrayList<>(node.getChildren());
        Collections.sort(sorted);

        for (DirTreeNode child : sorted) {
            flattenNode(child, result);
        }
    }


    private void expandAllRecursive(List<DirTreeNode> nodes) {
        for (DirTreeNode node : nodes) {
            if (node.isDirectory()) {
                expandedPaths.add(node.getRelativePath());
                expandAllRecursive(node.getChildren());
            }
        }
    }
}
