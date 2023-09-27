package io.webcontify.backend.collections.services


import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class CollectionDao(val dslContext: DSLContext) {

    fun getById(id: Int?): WebcontifyCollectionRecord {
        return dslContext.selectFrom(WEBCONTIFY_COLLECTION).where(WEBCONTIFY_COLLECTION.ID.eq(id)).orderBy(
            WEBCONTIFY_COLLECTION.ID).fetchAny() ?: throw RuntimeException();
    }

    fun getAll(): Set<WebcontifyCollectionRecord> {
        return dslContext.selectFrom(WEBCONTIFY_COLLECTION).orderBy(WEBCONTIFY_COLLECTION.ID).fetchArray().toHashSet()
    }

    fun deleteById(id: Int?) {
        dslContext.deleteFrom(WEBCONTIFY_COLLECTION).where(WEBCONTIFY_COLLECTION.ID.eq(id)).execute().let {
            if(it != 1) {
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