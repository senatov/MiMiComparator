package org.senatov.compare

import org.senatov.helpers.log.LogTag
import org.slf4j.LoggerFactory

internal class TreeStats {
    private val log = LoggerFactory.getLogger(TreeStats::class.java)
    var directories: Int = 0
    var files: Int = 0
    var differences: Int = 0

    fun count(isDirectory: Boolean) {
        log.debug(LogTag.COMPARE, "count(isDirectory={})", isDirectory)
        if (isDirectory) directories++ else files++
    }
}
