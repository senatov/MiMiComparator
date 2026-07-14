/*
 * ComparatorState — persistent UI state 4 MiMiComparator.
 * stored as JSON under ~/.mimi/comparator/.
 * Iakov Senatov, 2026
 */
package org.senatov.ui.config

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
    var previewSplitRatio: Double = 0.70,
    var compareMode: String = "Binary Content",
    var isShowDirs: Boolean = true,
    var isShowEqual: Boolean = true,
    var isShowDifferent: Boolean = true,
    var isShowOnlyLeft: Boolean = true,
    var isShowOnlyRight: Boolean = true,
    var lastStatusLeft: String = "",
    var lastStatusCenter: String = "",
    var lastStatusRight: String = ""
)
