/*
 * DirectoryComparator.java — recursive directory compare.
 * Compares by name, size, last-modified date.
 * TC-style: left-only, right-only, same, different.
 * Iakov Senatov, 2026
 */
package org.senatov.compare;

import lombok.extern.slf4j.Slf4j;
import org.senatov.model.CompareLineItem;
import org.senatov.model.CompareLineItem.DiffStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
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


    public static CompareResult compare(Path leftDir, Path rightDir, boolean showIdentical) throws IOException {
        log.info("dir compare: L={} R={}", leftDir, rightDir);

        Set<String> leftNames = listEntryNames(leftDir);
        Set<String> rightNames = listEntryNames(rightDir);

        TreeSet<String> allNames = new TreeSet<>(leftNames);
        allNames.addAll(rightNames);

        List<CompareLineItem> leftItems = new ArrayList<>();
        List<CompareLineItem> rightItems = new ArrayList<>();
        int diffs = 0;
        int lineNum = 0;

        for (String name : allNames) {
            lineNum++;
            boolean inLeft = leftNames.contains(name);
            boolean inRight = rightNames.contains(name);

            if (inLeft && inRight) {
                DirEntryComparison cmp = compareEntries(leftDir.resolve(name), rightDir.resolve(name));

                if (cmp.identical && !showIdentical) {
                    continue;
                }

                DiffStatus status = cmp.identical ? DiffStatus.IDENTICAL : DiffStatus.MODIFIED;
                if (!cmp.identical) {
                    diffs++;
                }

                leftItems.add(new CompareLineItem(lineNum, cmp.leftText, status));
                rightItems.add(new CompareLineItem(lineNum, cmp.rightText, status));
            } else if (inLeft) {
                diffs++;
                String info = formatEntry(leftDir.resolve(name));
                leftItems.add(new CompareLineItem(lineNum, info, DiffStatus.ADDED));
                rightItems.add(new CompareLineItem(lineNum, "‹missing›", DiffStatus.MISSING));
            } else {
                diffs++;
                String info = formatEntry(rightDir.resolve(name));
                leftItems.add(new CompareLineItem(lineNum, "‹missing›", DiffStatus.MISSING));
                rightItems.add(new CompareLineItem(lineNum, info, DiffStatus.ADDED));
            }
        }

        String summary = diffs == 0
                ? "dirs identical (" + allNames.size() + " entries)"
                : diffs + " diffs in " + allNames.size() + " entries";

        log.info("dir compare done: {}", summary);
        return new CompareResult(leftItems, rightItems, diffs, summary);
    }


    private static Set<String> listEntryNames(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
        }
    }


    private static DirEntryComparison compareEntries(Path left, Path right) {
        try {
            boolean leftIsDir = Files.isDirectory(left);
            boolean rightIsDir = Files.isDirectory(right);

            if (leftIsDir != rightIsDir) {
                String lt = leftIsDir ? "📁 " + left.getFileName() : formatEntry(left);
                String rt = rightIsDir ? "📁 " + right.getFileName() : formatEntry(right);
                return new DirEntryComparison(false, lt, rt);
            }

            if (leftIsDir) {
                String name = "📁 " + left.getFileName();
                return new DirEntryComparison(true, name, name);
            }

            BasicFileAttributes leftAttr = Files.readAttributes(left, BasicFileAttributes.class);
            BasicFileAttributes rightAttr = Files.readAttributes(right, BasicFileAttributes.class);

            boolean sameSize = leftAttr.size() == rightAttr.size();
            boolean sameDate = leftAttr.lastModifiedTime().equals(rightAttr.lastModifiedTime());
            boolean identical = sameSize && sameDate;

            String lt = formatEntryWithAttr(left, leftAttr);
            String rt = formatEntryWithAttr(right, rightAttr);

            return new DirEntryComparison(identical, lt, rt);
        } catch (IOException ex) {
            log.warn("cant compare entries: {} vs {} — {}", left, right, ex.getMessage());
            String name = left.getFileName().toString();
            return new DirEntryComparison(false, "⚠ " + name, "⚠ " + name);
        }
    }


    private static String formatEntry(Path path) {
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return formatEntryWithAttr(path, attr);
        } catch (IOException ex) {
            return (Files.isDirectory(path) ? "📁 " : "   ") + path.getFileName();
        }
    }


    private static String formatEntryWithAttr(Path path, BasicFileAttributes attr) {
        String prefix = Files.isDirectory(path) ? "📁 " : "   ";
        String name = path.getFileName().toString();
        String size = Files.isDirectory(path) ? "" : formatSize(attr.size());
        String date = DATE_FMT.format(attr.lastModifiedTime().toInstant());
        return String.format("%s%-40s %10s  %s", prefix, name, size, date);
    }


    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }


    private record DirEntryComparison(boolean identical, String leftText, String rightText) {
    }
}
