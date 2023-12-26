package io.webcontify.backend.collections.utils

import org.jooq.ConstraintEnforcementStep
import org.jooq.impl.DSL

fun MutableList<ConstraintEnforcementStep>.addLessThanIfPresent(
    tableName: String,
    columnName: String,
    lessThan: Any?,
    value: Any?
) {
  lessThan?.let {
    this.add(
        DSL.constraint("lt_${tableName}_${columnName}")
            .check(DSL.field(columnName).lessThan(value)))
  }
}

fun MutableList<ConstraintEnforcementStep>.addGreaterThanIfPresent(
    tableName: String,
    columnName: String,
    greaterThan: Any?,
    value: Any?
) {
  greaterThan?.let {
    this.add(
        DSL.constraint("gt_${tableName}_${columnName}")
            .check(DSL.field(columnName).greaterThan(value)))
  }
}
