/*
 * DirectoryComparator.java — recursive directory compare.
 * Builds paired DirTreeModels for left/right panels.
 * Compares by name, size, last-modified date.
 * TC-style: left-only, right-only, same, different.
 * Iakov Senatov, 2026
 */
package org.senatov.compare;

import lombok.extern.slf4j.Slf4j;
import org.senatov.model.CompareLineItem.DiffStatus;
import org.senatov.model.tree.DirTreeModel;
import org.senatov.model.tree.DirTreeNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
public final class DirectoryComparator {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());


    private DirectoryComparator() {
    }


    public static DirCompareResult compareTree(Path leftDir, Path rightDir) throws IOException {
        log.info("tree compare: L={} R={}", leftDir, rightDir);

        List<DirTreeNode> leftRoots = new ArrayList<>();
        List<DirTreeNode> rightRoots = new ArrayList<>();
        int[] diffCounter = {0};

        buildPairedTree(leftDir, rightDir, "", 0, leftRoots, rightRoots, diffCounter);

        DirTreeModel leftModel = new DirTreeModel(leftRoots);
        DirTreeModel rightModel = new DirTreeModel(rightRoots);

        log.info("tree compare done: {} diffs", diffCounter[0]);
        return new DirCompareResult(leftModel, rightModel, diffCounter[0]);
    }


    private static void buildPairedTree(
            Path leftDir, Path rightDir, String pathPrefix, int depth,
            List<DirTreeNode> leftNodes, List<DirTreeNode> rightNodes,
            int[] diffCounter
    ) throws IOException {

        Set<String> leftNames = safeListNames(leftDir);
        Set<String> rightNames = safeListNames(rightDir);

        TreeSet<String> allNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        allNames.addAll(leftNames);
        allNames.addAll(rightNames);

        for (String name : allNames) {
            boolean inLeft = leftNames.contains(name);
            boolean inRight = rightNames.contains(name);
            String relPath = pathPrefix.isEmpty() ? name : pathPrefix + "/" + name;

            if (inLeft && inRight) {
                Path lp = leftDir.resolve(name);
                Path rp = rightDir.resolve(name);
                boolean lIsDir = Files.isDirectory(lp);
                boolean rIsDir = Files.isDirectory(rp);

                if (lIsDir && rIsDir) {
                    DirTreeNode ln = makeDirNode(name, relPath, depth, DiffStatus.IDENTICAL);
                    DirTreeNode rn = makeDirNode(name, relPath, depth, DiffStatus.IDENTICAL);

                    buildPairedTree(lp, rp, relPath, depth + 1,
                            ln.getChildren(), rn.getChildren(), diffCounter);

                    leftNodes.add(ln);
                    rightNodes.add(rn);
                } else if (!lIsDir && !rIsDir) {
                    DiffStatus st = compareFileAttrs(lp, rp);
                    if (st != DiffStatus.IDENTICAL) {
                        diffCounter[0]++;
                    }
                    leftNodes.add(makeFileNode(name, relPath, lp, depth, st));
                    rightNodes.add(makeFileNode(name, relPath, rp, depth, st));
                } else {
                    diffCounter[0]++;
                    leftNodes.add(lIsDir
                            ? makeDirNode(name, relPath, depth, DiffStatus.MODIFIED)
                            : makeFileNode(name, relPath, lp, depth, DiffStatus.MODIFIED));
                    rightNodes.add(rIsDir
                            ? makeDirNode(name, relPath, depth, DiffStatus.MODIFIED)
                            : makeFileNode(name, relPath, rp, depth, DiffStatus.MODIFIED));
                }
            } else if (inLeft) {
                diffCounter[0]++;
                Path lp = leftDir.resolve(name);
                boolean isDir = Files.isDirectory(lp);
                leftNodes.add(isDir
                        ? makeDirNode(name, relPath, depth, DiffStatus.ADDED)
                        : makeFileNode(name, relPath, lp, depth, DiffStatus.ADDED));
                rightNodes.add(makePlaceholder(name, relPath, depth, isDir));
            } else {
                diffCounter[0]++;
                Path rp = rightDir.resolve(name);
                boolean isDir = Files.isDirectory(rp);
                leftNodes.add(makePlaceholder(name, relPath, depth, isDir));
                rightNodes.add(isDir
                        ? makeDirNode(name, relPath, depth, DiffStatus.ADDED)
                        : makeFileNode(name, relPath, rp, depth, DiffStatus.ADDED));
            }
        }
    }


    private static Set<String> safeListNames(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return Set.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        } catch (IOException ex) {
            log.warn("cant list dir {}: {}", dir, ex.getMessage());
            return Set.of();
        }
    }


    private static DiffStatus compareFileAttrs(Path left, Path right) {
        try {
            BasicFileAttributes la = Files.readAttributes(left, BasicFileAttributes.class);
            BasicFileAttributes ra = Files.readAttributes(right, BasicFileAttributes.class);
            boolean sameSize = la.size() == ra.size();
            boolean sameDate = la.lastModifiedTime().equals(ra.lastModifiedTime());
            return (sameSize && sameDate) ? DiffStatus.IDENTICAL : DiffStatus.MODIFIED;
        } catch (IOException ex) {
            return DiffStatus.MODIFIED;
        }
    }


    private static DirTreeNode makeDirNode(String name, String relPath, int depth, DiffStatus status) {
        return new DirTreeNode(name, relPath, true, 0, 0, status, depth);
    }


    private static DirTreeNode makeFileNode(String name, String relPath, Path filePath,
                                            int depth, DiffStatus status) {
        try {
            BasicFileAttributes attr = Files.readAttributes(filePath, BasicFileAttributes.class);
            return new DirTreeNode(name, relPath, false,
                    attr.size(), attr.lastModifiedTime().toMillis(), status, depth);
        } catch (IOException ex) {
            return new DirTreeNode(name, relPath, false, 0, 0, status, depth);
        }
    }


    private static DirTreeNode makePlaceholder(String name, String relPath, int depth, boolean isDir) {
        return new DirTreeNode("‹missing›", relPath, isDir, 0, 0, DiffStatus.MISSING, depth);
    }


    public static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }


    public static String formatDate(long millis) {
        if (millis <= 0) {
            return "";
        }
        return DATE_FMT.format(java.time.Instant.ofEpochMilli(millis));
    }
}
