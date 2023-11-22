package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.services.CollectionItemService
import io.webcontify.backend.collections.utils.isSlug
import io.webcontify.backend.collections.utils.toIdentifierMap
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.springframework.web.bind.annotation.*

@RestController
class CollectionItemController(val collectionItemService: CollectionItemService) {

  @DeleteMapping("$COLLECTIONS_PATH/{collectionId}/items/{*slugOrId}")
  fun deleteById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("slugOrId") slugOrId: String
  ) {
    slugOrId.substring(1).let {
      if (it.isSlug()) {
        return collectionItemService.deleteById(collectionId, it.toIdentifierMap())
      }
      return collectionItemService.deleteById(collectionId, it)
    }
  }

  @GetMapping("$COLLECTIONS_PATH/{collectionId}/items/{*slugOrId}")
  fun getById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("slugOrId") slugOrId: String
  ): Item {
    slugOrId.substring(1).let {
      if (it.isSlug()) {
        return collectionItemService.getById(collectionId, it.toIdentifierMap())
      }
      return collectionItemService.getById(collectionId, it)
    }
  }

  @GetMapping("$COLLECTIONS_PATH/{collectionId}/items")
  fun getAllForCollection(@PathVariable("collectionId") collectionId: Int): List<Item> {
    return collectionItemService.getAllFor(collectionId)
  }

  @PostMapping("$COLLECTIONS_PATH/{collectionId}/items")
  fun create(
      @PathVariable("collectionId") collectionId: Int,
      @RequestBody item: Item
  ): Item { // TODO primary key generation and always insert primary keys
    return collectionItemService.create(collectionId, item)
  }

  @PutMapping("$COLLECTIONS_PATH/{collectionId}/items/{*slugOrId}")
  fun updateById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable slugOrId: String,
      @RequestBody item: Item
  ): Item {
    slugOrId.substring(1).let {
      if (it.isSlug()) {
        return collectionItemService.updateById(collectionId, it.toIdentifierMap(), item)
      }
      return collectionItemService.updateById(collectionId, it, item)
    }
  }
}
