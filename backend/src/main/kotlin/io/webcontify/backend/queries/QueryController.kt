package io.webcontify.backend.queries

import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import io.webcontify.backend.queries.mappers.QueryRequestMapper
import io.webcontify.backend.queries.models.QueryCreateRequestDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class QueryController(val queryService: QueryService, val queryRequestMapper: QueryRequestMapper) {

  @PostMapping("$COLLECTIONS_PATH/{collectionId}/views")
  fun createView(
      @PathVariable("collectionId") collectionId: Long,
      @RequestBody viewCreateDto: QueryCreateRequestDto
  ): Long {
    val viewDto = queryRequestMapper.mapApiToDto(viewCreateDto, collectionId)
    val dto = queryService.createView(viewDto)
    return dto.id!!
  }

  @GetMapping("$COLLECTIONS_PATH/views/{viewId}")
  fun getViewItems(@PathVariable("viewId") viewId: Long): List<Item> {
    return queryService.getItems(viewId)
  }
}
