package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.daos.CollectionCreateTableDao
import io.webcontify.backend.collections.daos.CollectionDao
import io.webcontify.backend.collections.models.WebContifyCollectionDto
import org.springframework.stereotype.Component

@Component
class CollectionRepository(val dao: CollectionDao, val tableDao: CollectionCreateTableDao) {

  fun getAll(): Set<WebContifyCollectionDto> {
    return dao.getAll()
  }

  fun getById(id: Int): WebContifyCollectionDto {
    return dao.getById(id)
  }

  fun deleteById(id: Int) {
    return dao.deleteById(id)
  }

  fun create(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    return dao.create(collection).also { tableDao.createTable(it) }
  }

  fun update(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    return dao.update(collection)
  }
}