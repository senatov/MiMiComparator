/*
 * FileContentComparator.java — line-by-line diff of two text files.
 * Generates CompareLineItem lists with DiffStatus coloring.
 * Iakov Senatov, 2026
 */
package org.senatov.compare;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.senatov.model.CompareLineItem;
import org.senatov.model.CompareLineItem.DiffStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


@Slf4j
public final class FileContentComparator {

    private FileContentComparator() {
    }


    public static CompareResult compare(Path leftFile, Path rightFile, boolean showIdentical) throws IOException {
        log.info("file compare: L={} R={}", leftFile, rightFile);

        List<String> leftLines = Files.readAllLines(leftFile, StandardCharsets.UTF_8);
        List<String> rightLines = Files.readAllLines(rightFile, StandardCharsets.UTF_8);

        int maxLen = Math.max(leftLines.size(), rightLines.size());
        List<CompareLineItem> leftItems = new ArrayList<>();
        List<CompareLineItem> rightItems = new ArrayList<>();
        int diffs = 0;

        for (int i = 0; i < maxLen; i++) {
            String l = i < leftLines.size() ? leftLines.get(i) : "";
            String r = i < rightLines.size() ? rightLines.get(i) : "";

            DiffStatus leftStatus;
            DiffStatus rightStatus;

            if (StringUtils.equals(l, r)) {
                leftStatus = DiffStatus.IDENTICAL;
                rightStatus = DiffStatus.IDENTICAL;
            } else if (i >= leftLines.size()) {
                leftStatus = DiffStatus.MISSING;
                rightStatus = DiffStatus.ADDED;
                diffs++;
            } else if (i >= rightLines.size()) {
                leftStatus = DiffStatus.ADDED;
                rightStatus = DiffStatus.MISSING;
                diffs++;
            } else {
                leftStatus = DiffStatus.MODIFIED;
                rightStatus = DiffStatus.MODIFIED;
                diffs++;
            }

            if (!showIdentical && leftStatus == DiffStatus.IDENTICAL) {
                continue;
            }

            leftItems.add(new CompareLineItem(i + 1, l, leftStatus));
            rightItems.add(new CompareLineItem(i + 1, r, rightStatus));
        }

        String summary = diffs == 0
                ? "files identical (" + maxLen + " lines)"
                : diffs + " diffs in " + maxLen + " lines";

        log.info("file compare done: {}", summary);
        return new CompareResult(leftItems, rightItems, diffs, summary);
    }
}
