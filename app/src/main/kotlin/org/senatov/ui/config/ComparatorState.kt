/*
 * ComparatorState — persistent UI state 4 MiMiComparator.
 * stored as JSON under ~/.mimi/comparator/.
 * Iakov Senatov, 2026
 */
package org.senatov.mimicomparator.ui.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class ComparatorState(
    var window: WindowState = WindowState(),
    var leftPanel: PanelState = PanelState(),
    var rightPanel: PanelState = PanelState(),
    var leftInputPath: String = "",
    var rightInputPath: String = "",
    var isDirMode: Boolean = true,
    var isSyncScroll: Boolean = true,
    var splitRatio: Double = 0.5,
    var isShowDirs: Boolean = true,
    var isShowEqual: Boolean = true,
    var isShowDifferent: Boolean = true,
    var isShowOnlyLeft: Boolean = true,
    var isShowOnlyRight: Boolean = true,
    var lastStatusLeft: String = "",
    var lastStatusCenter: String = "",
    var lastStatusRight: String = ""
) {

    companion object {
        fun defaults(): ComparatorState = ComparatorState()
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    data class WindowState(
        var x: Double = 120.0,
        var y: Double = 120.0,
        var width: Double = 1400.0,
        var height: Double = 900.0,
        var isMaximized: Boolean = false
    )


    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PanelState(
        var path: String = "",
        var selectedIndex: Int = -1,
        var scrollPosition: Double = 0.0,
        var visibleItems: MutableList<String> = mutableListOf(),
        var rawLines: MutableList<String> = mutableListOf()
    )
}