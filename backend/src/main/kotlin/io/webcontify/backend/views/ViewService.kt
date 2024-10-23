package io.webcontify.backend.views

import io.webcontify.backend.collections.models.Item
import org.springframework.stereotype.Service

@Service
class ViewService(private val viewRepository: ViewRepository) {

  fun createView(viewDto: ViewDto): ViewDto {
    val id = viewRepository.createView(viewDto)
    return viewDto.copy(id = id)
  }

  fun getItems(id: Long): List<Item> {
    val view = viewRepository.getById(id)
    return viewRepository.getAllViewItems(view)
  }
}
