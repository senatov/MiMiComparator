package org.senatov.compare

import org.senatov.helpers.log.LogTag
import org.slf4j.LoggerFactory

internal data class TreePosition(
    val pathPrefix: String,
    val depth: Int,
) {
    fun child(name: String): TreePosition {
        log.debug(LogTag.COMPARE, "child(name={})", name)
        return TreePosition(
            pathPrefix = if (pathPrefix.isEmpty()) name else "$pathPrefix/$name",
            depth = depth + 1,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(TreePosition::class.java)
        val ROOT = TreePosition(pathPrefix = "", depth = 0)
    }
}
