package io.webcontify.backend.queries

import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.queries.models.QueryDto
import org.springframework.stereotype.Service

@Service
class QueryService(private val queryRepository: QueryRepository) {

  fun createView(queryDto: QueryDto): QueryDto {
    val id = queryRepository.createView(queryDto)
    return queryDto.copy(id = id)
  }

  fun getItems(id: Long): List<Item> {
    val view = queryRepository.getById(id)
    return queryRepository.getAllViewItems(view)
  }
}
