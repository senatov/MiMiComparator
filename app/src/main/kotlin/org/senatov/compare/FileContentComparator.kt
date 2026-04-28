/*
 * FileContentComparator — line-by-line diff of two text files.
 * generates CompareLineItem lists w/ DiffStatus coloring.
 * Iakov Senatov, 2026
 */
package org.senatov.mimicomparator.compare

import org.senatov.mimicomparator.model.CompareLineItem
import org.senatov.mimicomparator.model.CompareLineItem.DiffStatus
import org.senatov.mimicomparator.helpers.log.LogTag
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max


object FileContentComparator {

    private val log = LoggerFactory.getLogger(FileContentComparator::class.java)


    fun compare(leftFile: Path, rightFile: Path, showIdentical: Boolean): CompareResult {
        log.info(LogTag.COMPARE, "file start L={} R={} identical={}", leftFile, rightFile, showIdentical)
        val leftLines = Files.readAllLines(leftFile, StandardCharsets.UTF_8)
        val rightLines = Files.readAllLines(rightFile, StandardCharsets.UTF_8)
        log.debug(LogTag.IO, "file lines L={} R={}", leftLines.size, rightLines.size)
        val maxLen = max(leftLines.size, rightLines.size)
        val leftItems = mutableListOf<CompareLineItem>()
        val rightItems = mutableListOf<CompareLineItem>()
        var diffs = 0
        for (i in 0 until maxLen) {
            val l = leftLines.getOrElse(i) { "" }
            val r = rightLines.getOrElse(i) { "" }
            val (leftStatus, rightStatus) = when {
                l == r -> DiffStatus.IDENTICAL to DiffStatus.IDENTICAL
                i >= leftLines.size -> { diffs++; DiffStatus.MISSING to DiffStatus.ADDED }
                i >= rightLines.size -> { diffs++; DiffStatus.ADDED to DiffStatus.MISSING }
                else -> { diffs++; DiffStatus.MODIFIED to DiffStatus.MODIFIED }
            }
            if (!showIdentical && leftStatus == DiffStatus.IDENTICAL) continue
            leftItems.add(CompareLineItem(i + 1, l, leftStatus))
            rightItems.add(CompareLineItem(i + 1, r, rightStatus))
        }
        val summary = if (diffs == 0) "files identical ($maxLen lines)"
            else "$diffs diffs in $maxLen lines"
        log.info(LogTag.COMPARE, "file done {}", summary)
        return CompareResult(leftItems, rightItems, diffs, summary)
    }
}