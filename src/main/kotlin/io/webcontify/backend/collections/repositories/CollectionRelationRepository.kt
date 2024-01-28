package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.models.apis.WebContifyCollectionRelationApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION
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
  fun update(
      relationFields: Set<WebContifyCollectionRelationApiUpdateRequest>
  ): Set<WebContifyCollectionRelationApiUpdateRequest> {
    val updateStatements =
        relationFields
            .map {
              dslContext
                  .update(WEBCONTIFY_COLLECTION_RELATION)
                  .set(WEBCONTIFY_COLLECTION_RELATION.NAME, it.name)
                  .set(WEBCONTIFY_COLLECTION_RELATION.DISPLAY_NAME, it.displayName)
                  .where(
                      WEBCONTIFY_COLLECTION_RELATION.SOURCE_COLLECTION_ID.eq(it.sourceCollectionId)
                          .and(
                              WEBCONTIFY_COLLECTION_RELATION.SOURCE_COLLECTION_COLUMN_NAME.eq(
                                  it.sourceCollectionColumnName))
                          .and(
                              WEBCONTIFY_COLLECTION_RELATION.REFERENCED_COLLECTION_ID.eq(
                                  it.referencedCollectionId))
                          .and(
                              WEBCONTIFY_COLLECTION_RELATION.REFERENCED_COLLECTION_COLUMN_NAME.eq(
                                  it.referencedCollectionColumnName))
                          .and(WEBCONTIFY_COLLECTION_RELATION.TYPE.eq(it.type))
                          .and(WEBCONTIFY_COLLECTION_RELATION.NAME.eq(it.name)))
            }
            .toList()

    try {
      dslContext.batch(updateStatements).execute()
      return relationFields
    } catch (e: DuplicateKeyException) {
      throw RuntimeException("TODO")
    }
  }

  @Transactional
  fun create(
      relationFields: Set<WebContifyCollectionRelationDto>
  ): Set<WebContifyCollectionRelationDto> {
    val insertableRecords =
        relationFields
            .map {
              dslContext.newRecord(WEBCONTIFY_COLLECTION_RELATION).let { record ->
                record.sourceCollectionId = it.sourceCollection.id
                record.sourceCollectionColumnName = it.sourceCollectionColumnName
                record.referencedCollectionId = it.referencedCollection.id
                record.referencedCollectionColumnName = it.referencedCollectionColumnName
                record.name = it.name
                record.displayName = it.displayName
                record.type = it.type
                return@map record
              }
            }
            .toList()
    try {
      dslContext.batchInsert(insertableRecords).execute()
      return relationFields
    } catch (e: DuplicateKeyException) {
      throw RuntimeException("TODO")
    } catch (e: DataIntegrityViolationException) {
      throw RuntimeException("TODO")
    }
  }
}
