package org.senatov.compare

import org.senatov.helpers.log.LogTag
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

object DirectoryComparator {
    private val log = LoggerFactory.getLogger(DirectoryComparator::class.java)
    private val dateFormatter = DateTimeFormatter
        .ofPattern("d. MMM yyyy 'at' HH:mm:ss", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())

    fun compareTree(
        leftDirectory: Path,
        rightDirectory: Path,
        mode: CompareMode = CompareMode.SIZE_AND_TIMESTAMP,
    ): DirCompareResult {
        log.debug(
            LogTag.COMPARE,
            "compareTree(leftDirectory={}, rightDirectory={})",
            leftDirectory,
            rightDirectory,
        )
        log.info(LogTag.COMPARE, "tree start left={} right={}", leftDirectory, rightDirectory)
        return DirectoryTreeBuilder(mode).build(DirectoryPair(leftDirectory, rightDirectory))
    }

    fun formatSize(bytes: Long): String {
        log.debug(LogTag.COMPARE, "formatSize(bytes={})", bytes)
        return if (bytes <= 0) "0" else "%,d".format(bytes).replace(",", " ")
    }

    fun formatDate(millis: Long): String {
        log.debug(LogTag.COMPARE, "formatDate(millis={})", millis)
        return if (millis <= 0) "" else dateFormatter.format(Instant.ofEpochMilli(millis))
    }
}
