package io.webcontify.backend.relations

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import org.jooq.ConstraintForeignKeyOnStep
import org.jooq.Name
import org.jooq.impl.DSL.constraint
import org.jooq.impl.DSL.name

data class TableRelationDto(
    val relationId: Long,
    val sourceTable: RelationTable,
    val referencedTable: RelationTable
) {

  fun relation(): ConstraintForeignKeyOnStep {
    return constraint(name(relationName()))
        .foreignKey(*sourceTable.columnNames().toTypedArray())
        .references(referencedTable.tableName(), referencedTable.columnNames())
  }

  private fun relationName(): String {
    return "fk_relation_${this.relationId}_${this.sourceTable.id}_${this.referencedTable.id}"
  }
}

data class RelationTable(
    val id: Long,
    val name: String,
    val fields: Set<WebContifyCollectionFieldDto>
) {
  fun columnNames(): Set<Name> {
    return fields.map { name(it.name) }.toSet()
  }

  fun tableName(): Name {
    return name(name)
  }
}
