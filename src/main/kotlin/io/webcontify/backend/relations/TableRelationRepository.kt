package io.webcontify.backend.relations

import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class TableRelationRepository(val dslContext: DSLContext) {

  fun create(tableRelationDto: TableRelationDto) {
    dslContext
        .alterTableIfExists(tableRelationDto.sourceTable.tableName())
        .add(tableRelationDto.relation())
        .execute()
  }

  fun delete(relationDto: RelationCollectionDto) {
    dslContext
        .alterTableIfExists(relationDto.sourceCollection.name)
        .dropConstraintIfExists(relationDto.relationName())
        .execute()
  }
}
