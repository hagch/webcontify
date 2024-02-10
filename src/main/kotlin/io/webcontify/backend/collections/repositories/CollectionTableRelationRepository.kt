package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository

@Repository
class CollectionTableRelationRepository(val dslContext: DSLContext) {

  fun create(relation: WebContifyCollectionRelationDto) {
    val sourceFields = relation.fields.map { field(name(it.sourceCollectionColumnName)) }
    val referencedFields = relation.fields.map { name(it.referencedCollectionColumnName) }
    dslContext
        .alterTableIfExists(name(relation.sourceCollection.name))
        .add(
            constraint("fk_relation_${relation.referencedCollection.name}_${relation.name}")
                .foreignKey(sourceFields)
                .references(name(relation.referencedCollection.name), referencedFields))
        .execute()
  }
}
