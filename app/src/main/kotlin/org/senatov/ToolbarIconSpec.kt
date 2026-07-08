package org.senatov

internal data class ToolbarIconSpec(
    val glyph: String,
    val color: String = "#2f343a",
    val size: Int = 32,
    val emoji: Boolean = false,
) {
    val style: String
        get() {
            val family = if (emoji) "'Apple Color Emoji','System'" else "'System'"
            val effect = if (emoji) "" else "-fx-effect:dropshadow(gaussian,rgba(255,255,255,0.85),0,0,0,1);"
            return "-fx-font-family:$family; -fx-font-size:$size; -fx-font-weight:400; " +
                    "-fx-text-fill:$color; -fx-opacity:1; -fx-font-smoothing-type:gray; $effect"
        }
}