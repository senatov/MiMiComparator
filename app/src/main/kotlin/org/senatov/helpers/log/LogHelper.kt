package org.senatov.helpers.log

import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.MarkerFactory

object LogHelper {

    /** Logs a stable method-entry message and its named arguments. */
    fun enter(log: Logger, marker: Marker, method: String, vararg arguments: Pair<String, Any?>) {
        if (!log.isInfoEnabled(marker)) return
        val renderedArguments = arguments.joinToString(", ") { (name, value) -> "$name=$value" }
        log.info(marker, "{}({})", method, renderedArguments)
    }

}

object LogTag {
    val APP: Marker = MarkerFactory.getMarker("APP")
    val CLI: Marker = MarkerFactory.getMarker("CLI")
    val COMPARE: Marker = MarkerFactory.getMarker("COMPARE")
    val IO: Marker = MarkerFactory.getMarker("IO")
    val STATE: Marker = MarkerFactory.getMarker("STATE")
    val UI: Marker = MarkerFactory.getMarker("UI")
}
