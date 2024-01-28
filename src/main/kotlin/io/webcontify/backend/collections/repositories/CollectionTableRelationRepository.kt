package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository

@Repository
class CollectionTableRelationRepository(val dslContext: DSLContext) {

  fun create(relation: Set<WebContifyCollectionRelationDto>) {
    val firstRelationEntry = relation.first()
    val sourceFields = relation.map { field(name(it.sourceCollectionColumnName)) }
    val referencedFields = relation.map { name(it.referencedCollectionColumnName) }
    dslContext
        .alterTableIfExists(name(firstRelationEntry.sourceCollection.name))
        .add(
            constraint("fk_relation_${firstRelationEntry.referencedCollection.name}_${firstRelationEntry.name}")
                .foreignKey(sourceFields)
                .references(name(firstRelationEntry.referencedCollection.name), referencedFields))
        .execute()
  }
}
