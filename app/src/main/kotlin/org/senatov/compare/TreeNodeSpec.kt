package org.senatov.compare

import org.senatov.model.DiffStatus

internal data class TreeNodeSpec(
    val name: String,
    val position: TreePosition,
    val status: DiffStatus,
)
