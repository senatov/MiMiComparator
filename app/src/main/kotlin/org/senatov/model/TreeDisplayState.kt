package org.senatov.model

import org.senatov.model.tree.TreeEntryDetails

data class TreeDisplayState(
    val details: TreeEntryDetails,
    val isExpanded: Boolean,
)
