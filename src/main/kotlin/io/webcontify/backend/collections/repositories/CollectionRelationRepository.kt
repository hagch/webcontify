package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION_FIELD
import org.jooq.DSLContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CollectionRelationRepository(val dslContext: DSLContext) {
  @Transactional
  fun delete(sourceCollectionId: Int, name: String) {
    dslContext
        .deleteFrom(WEBCONTIFY_COLLECTION_RELATION)
        .where(
            WEBCONTIFY_COLLECTION_RELATION.SOURCE_COLLECTION_ID.eq(sourceCollectionId)
                .and(WEBCONTIFY_COLLECTION_RELATION.NAME.eq(name)))
        .execute()
  }

  @Transactional
  fun create(relation: WebContifyCollectionRelationDto): WebContifyCollectionRelationDto {
    val relationInsert =
        dslContext.newRecord(WEBCONTIFY_COLLECTION_RELATION).apply {
          this.sourceCollectionId = relation.sourceCollection.id
          this.referencedCollectionId = relation.referencedCollection.id
          this.name = relation.name
          this.displayName = relation.displayName
          this.type = relation.type
        }
    val fields =
        relation.fields
            .map {
              dslContext.newRecord(WEBCONTIFY_COLLECTION_RELATION_FIELD).apply {
                this.sourceCollectionId = relation.sourceCollection.id
                this.sourceCollectionColumnName = it.sourceCollectionColumnName
                this.referencedCollectionId = relation.referencedCollection.id
                this.referencedCollectionColumnName = it.referencedCollectionColumnName
                this.name = relation.name
                this.type = relation.type
              }
            }
            .toTypedArray()
    try {
      dslContext.batchInsert(relationInsert, *fields).execute()
      return relation
    } catch (e: DuplicateKeyException) {
      throw RuntimeException("TODO")
    } catch (e: DataIntegrityViolationException) {
      throw RuntimeException("TODO")
    }
  }
}
