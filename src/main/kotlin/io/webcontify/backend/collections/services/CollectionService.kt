package io.webcontify.backend.collections.services

import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
import org.springframework.stereotype.Component

@Component
class CollectionService(val repository: CollectionDao) {

  fun getAll(): Set<WebcontifyCollectionRecord> {
    return repository.getAll()
  }
}
