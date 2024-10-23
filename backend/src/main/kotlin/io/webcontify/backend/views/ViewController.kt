package io.webcontify.backend.views

import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ViewController(val viewService: ViewService, val viewMapper: ViewMapper) {

  @PostMapping("$COLLECTIONS_PATH/{collectionId}/views")
  fun createView(
      @PathVariable("collectionId") collectionId: Long,
      @RequestBody viewCreateDto: ViewCreateRequestDto
  ): Long {
    val viewDto = viewMapper.mapApiToDto(viewCreateDto, collectionId)
    val dto = viewService.createView(viewDto)
    return dto.id!!
  }

  @GetMapping("$COLLECTIONS_PATH/views/{viewId}")
  fun getViewItems(@PathVariable("viewId") viewId: Long): List<Item> {
    return viewService.getItems(viewId)
  }
}
