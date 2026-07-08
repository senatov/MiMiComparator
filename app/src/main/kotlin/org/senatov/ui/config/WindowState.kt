package org.senatov.ui.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class WindowState(
    var x: Double = 120.0,
    var y: Double = 120.0,
    var width: Double = 1400.0,
    var height: Double = 900.0,
    var isMaximized: Boolean = false,
)
