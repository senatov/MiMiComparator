package org.senatov.helpers.log

import org.slf4j.Marker
import org.slf4j.MarkerFactory

object LogTag {
    val APP: Marker = MarkerFactory.getMarker("APP")
    val CLI: Marker = MarkerFactory.getMarker("CLI")
    val COMPARE: Marker = MarkerFactory.getMarker("COMPARE")
    val IO: Marker = MarkerFactory.getMarker("IO")
    val STATE: Marker = MarkerFactory.getMarker("STATE")
    val UI: Marker = MarkerFactory.getMarker("UI")
}
