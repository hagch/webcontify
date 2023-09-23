package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.jooq.tables.pojos.WebcontifyCollection
import org.springframework.stereotype.Component

@Component
class CollectionService(val repository: CollectionRepository) {

    fun getAll(): List<WebcontifyCollection> {
        return repository.getAll()
    }
}