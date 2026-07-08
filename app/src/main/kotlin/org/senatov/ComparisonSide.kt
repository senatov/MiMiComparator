package org.senatov

internal enum class ComparisonSide(
    val logName: String,
    val openDialogTitle: String,
) {
    LEFT(logName = "left", openDialogTitle = "Open Left"),
    RIGHT(logName = "right", openDialogTitle = "Open Right"),
}
