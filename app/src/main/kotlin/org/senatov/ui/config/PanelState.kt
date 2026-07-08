package org.senatov.ui.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PanelState(
    var path: String = "",
    var selectedIndex: Int = -1,
    var scrollPosition: Double = 0.0,
    var visibleItems: MutableList<String> = mutableListOf(),
    var rawLines: MutableList<String> = mutableListOf(),
)
