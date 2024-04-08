package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository

@Repository
class CollectionTableRelationRepository(val dslContext: DSLContext) {
  companion object {
    val CONSTRAINT_PREFIX = "fk_relation_"
  }

  fun create(relation: WebContifyCollectionRelationDto) {
    /**
     * TODO NOT WORKING val sourceFields = relation.fields.map {
     * field(name(it.sourceCollectionColumnName)) } val referencedFields = relation.fields.map {
     * name(it.referencedCollectionColumnName) } dslContext
     * .alterTableIfExists(name(relation.sourceCollection.name)) .add(
     * constraint("$CONSTRAINT_PREFIX${relation.referencedCollection.name}_${relation.name}")
     * .foreignKey(sourceFields) .references(name(relation.referencedCollection.name),
     * referencedFields)) .execute()
     */
  }

  fun delete(
      sourceCollection: WebContifyCollectionDto,
      referencedCollection: WebContifyCollectionDto,
      relationName: String
  ) {
    dslContext
        .alterTableIfExists(name(sourceCollection.name))
        .drop(constraint("$CONSTRAINT_PREFIX${referencedCollection.name}_${relationName}"))
        .execute()
  }
}
