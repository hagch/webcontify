package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.daos.CollectionColumnDao
import io.webcontify.backend.collections.models.WebContifyCollectionColumnDto
import org.springframework.stereotype.Component

@Component
class CollectionColumnRepository(val dao: CollectionColumnDao) {

  fun getAll(): Set<WebContifyCollectionColumnDto> {
    return dao.getAll()
  }

  fun getAllForCollection(collectionId: Int): Set<WebContifyCollectionColumnDto> {
    return dao.getAllForCollection(collectionId)
  }

  fun getById(collectionId: Int, name: String): WebContifyCollectionColumnDto {
    return dao.getById(collectionId, name)
  }

  fun deleteById(collectionId: Int, name: String) {
    return dao.deleteById(collectionId, name)
  }

  fun create(column: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    return dao.create(column)
  }

  fun update(column: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    return dao.update(column)
  }

  fun deleteAllForCollection(columnId: Int) {
    return dao.deleteAllForCollection(columnId)
  }
}
