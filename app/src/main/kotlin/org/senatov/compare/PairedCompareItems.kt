package org.senatov.compare

import org.senatov.model.CompareLineItem

data class PairedCompareItems(
    val left: List<CompareLineItem>,
    val right: List<CompareLineItem>,
)
