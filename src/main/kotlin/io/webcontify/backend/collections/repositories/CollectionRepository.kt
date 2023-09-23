package io.webcontify.backend.collections.repositories

import io.webcontify.backend.jooq.tables.daos.WebcontifyCollectionDao
import io.webcontify.backend.jooq.tables.pojos.WebcontifyCollection
import org.springframework.stereotype.Component

@Component
class CollectionRepository(val dao: WebcontifyCollectionDao) {

    fun getAll(): List<WebcontifyCollection> {
        return dao.findAll()
    }

}