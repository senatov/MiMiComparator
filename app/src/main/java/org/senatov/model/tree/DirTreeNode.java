/*
 * DirTreeNode.java — single node in the dir comparison tree.
 * Holds name, path, attributes, children, expand state.
 * Iakov Senatov, 2026
 */
package org.senatov.model.tree;

import lombok.Getter;
import lombok.Setter;
import org.senatov.model.CompareLineItem.DiffStatus;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;


@Getter
public final class DirTreeNode implements Comparable<DirTreeNode> {

    private final String name;
    private final String relativePath;
    private final boolean directory;
    private final long size;
    private final long lastModifiedMs;
    private final DiffStatus status;
    private final int depth;
    @Setter
    private boolean expanded;
    private final List<DirTreeNode> children = new ArrayList<>();

    public DirTreeNode(String name, String relativePath, boolean directory,
                       long size, long lastModifiedMs, DiffStatus status, int depth) {
        this.name = name;
        this.relativePath = relativePath;
        this.directory = directory;
        this.size = size;
        this.lastModifiedMs = lastModifiedMs;
        this.status = status;
        this.depth = depth;
        this.expanded = false;
    }


    public void addChild(DirTreeNode child) {
        children.add(child);
    }


    @Override
    public int compareTo(DirTreeNode other) {
        if (this.directory != other.directory) {
            return this.directory ? -1 : 1;
        }
        return this.name.compareToIgnoreCase(other.name);
    }
}
