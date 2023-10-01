package io.webcontify.backend.collections.services

import io.webcontify.backend.converters.CollectionConverter
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionColumnRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_COLUMN
import io.webcontify.backend.models.WebContifyCollectionDto
import java.util.stream.Collectors.*
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class CollectionDao(val dslContext: DSLContext, val converter: CollectionConverter) {

  fun getById(id: Int?): WebContifyCollectionDto {
    val map =
        dslContext
            .select()
            .from(WEBCONTIFY_COLLECTION)
            .leftJoin(WEBCONTIFY_COLLECTION_COLUMN)
            .onKey()
            .where(WEBCONTIFY_COLLECTION.ID.eq(id))
            .collect(
                groupingBy(
                    { r -> r.into(WebcontifyCollectionRecord::class.java) },
                    filtering(
                        { r -> r.get(WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID) != null },
                        mapping(
                            { r -> r.into(WebcontifyCollectionColumnRecord::class.java) },
                            toSet()))))
    return map.firstNotNullOfOrNull { (collection, columns) ->
      converter.convertToDto(collection, columns)
    }
        ?: throw RuntimeException()
  }

  fun getAll(): Set<WebContifyCollectionDto> {
    val map =
        dslContext
            .select()
            .from(WEBCONTIFY_COLLECTION)
            .leftJoin(WEBCONTIFY_COLLECTION_COLUMN)
            .onKey()
            .collect(
                groupingBy(
                    { r -> r.into(WebcontifyCollectionRecord::class.java) },
                    filtering(
                        { r -> r.get(WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID) != null },
                        mapping(
                            { r -> r.into(WebcontifyCollectionColumnRecord::class.java) },
                            toSet()))))
    return map.map { (collection, columns) -> converter.convertToDto(collection, columns) }
        .toHashSet()
  }

  fun deleteById(id: Int?) {
    dslContext
        .deleteFrom(WEBCONTIFY_COLLECTION)
        .where(WEBCONTIFY_COLLECTION.ID.eq(id))
        .execute()
        .let {
          if (it != 1) {
            throw RuntimeException()
          }
        }
  }

  fun update(record: WebcontifyCollectionRecord): WebcontifyCollectionRecord {
    return dslContext.newRecord(WEBCONTIFY_COLLECTION).apply {
      this.displayName = record.displayName
      this.name = record.displayName
      this.update()
    }
  }

  fun create(record: WebcontifyCollectionRecord): WebcontifyCollectionRecord {
    return dslContext.newRecord(WEBCONTIFY_COLLECTION).apply {
      this.displayName = record.displayName
      this.name = record.displayName
      this.insert()
    }
  }
}
