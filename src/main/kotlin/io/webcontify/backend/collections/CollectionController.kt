package io.webcontify.backend.collections

import io.webcontify.backend.collections.services.CollectionService
import io.webcontify.backend.jooq.tables.pojos.WebcontifyCollection
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class CollectionController(val service: CollectionService) {

    @GetMapping("collections")
    fun get(): List<WebcontifyCollection> {
        return service.getAll()
    }
}