package io.webcontify.backend.collections.utils

import org.jooq.ConstraintEnforcementStep
import org.jooq.impl.DSL

fun MutableList<ConstraintEnforcementStep>.addLessThanIfPresent(
    tableName: String,
    fieldName: String,
    lessThan: Any?,
    value: Any?
) {
  lessThan?.let {
    this.add(
        DSL.constraint("lt_${tableName}_${fieldName}").check(DSL.field(fieldName).lessThan(value)))
  }
}

fun MutableList<ConstraintEnforcementStep>.addGreaterThanIfPresent(
    tableName: String,
    fieldName: String,
    greaterThan: Any?,
    value: Any?
) {
  greaterThan?.let {
    this.add(
        DSL.constraint("gt_${tableName}_${fieldName}")
            .check(DSL.field(fieldName).greaterThan(value)))
  }
}
