package org.senatov.compare

import java.nio.file.Path

internal data class DirectoryPair(
    val left: Path,
    val right: Path,
)
