package org.senatov.model.tree

import org.senatov.model.DiffStatus

data class TreeNodeIdentity(
    val name: String,
    val status: DiffStatus,
)
