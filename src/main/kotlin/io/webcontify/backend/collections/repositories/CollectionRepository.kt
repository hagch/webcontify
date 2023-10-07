package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.daos.CollectionTableDao
import io.webcontify.backend.collections.daos.CollectionDao
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import org.springframework.stereotype.Component

@Component
class CollectionRepository(val dao: CollectionDao, val tableDao: CollectionTableDao) {

  fun getAll(): Set<WebContifyCollectionDto> {
    return dao.getAll()
  }

  fun getById(id: Int): WebContifyCollectionDto {
    return dao.getById(id)
  }

  fun deleteById(id: Int) {
    val collection = dao.getById(id)
    return dao.deleteById(id).also { tableDao.deleteTable(collection.name) }
  }

  fun create(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    return dao.create(collection).also { tableDao.createTable(it) }
  }

  fun update(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    val oldCollection = dao.getById(collection.id)
    return dao.update(collection).also { tableDao.updateTableName(it.name, oldCollection.name) }
  }
}
