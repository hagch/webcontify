package io.webcontify.backend.collections.services

import helpers.suppliers.collectionWithFields
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.repositories.CollectionItemRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class CollectionItemServiceTest {
  @MockK lateinit var collectionService: CollectionService
  @MockK lateinit var collectionItemRepository: CollectionItemRepository
  @InjectMockKs lateinit var collectionItemService: CollectionItemService

  private val collection = collectionWithFields(listOf(Pair("test", true)))
  private val id = 1L
  private val identifierMap = mapOf(Pair("test", id))

  @Test
  fun `(getById) with map should call get by id for with collection and identifier map`() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.getByIdFor(any(), any()) } returns mapOf()

    collectionItemService.getById(id, identifierMap)

    verify(exactly = 1) { collectionItemRepository.getByIdFor(collection, identifierMap) }
  }

  @Test
  fun `(getById) should call get by id for with collection and identifier map`() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.getByIdFor(any(), any()) } returns mapOf()

    collectionItemService.getById(id, id)

    verify(exactly = 1) { collectionItemRepository.getByIdFor(collection, identifierMap) }
  }

  @Test
  fun `(deleteById) with map should call delete by id with collection and identifier map`() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.deleteById(any(), any()) } returns Unit

    collectionItemService.deleteById(id, identifierMap)

    verify(exactly = 1) { collectionItemRepository.deleteById(collection, identifierMap) }
  }

  @Test
  fun `(deleteById) with map should throw exception if map size is unequal to primary keys of collection`() {
    every { collectionService.getById(any()) } returns collection

    assertThrows<UnprocessableContentException> { collectionItemService.deleteById(id, mapOf()) }
  }

  @Test
  fun `(deleteById) with map should throw exception if primary key is not contained in identifier map`() {
    every { collectionService.getById(any()) } returns collection

    assertThrows<UnprocessableContentException> {
      collectionItemService.deleteById(id, mapOf(Pair("not", null)))
    }
  }

  @Test
  fun `(deleteById) should call delete by id with collection and identifier map`() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.deleteById(any(), any()) } returns Unit

    collectionItemService.deleteById(id, id)

    verify(exactly = 1) { collectionItemRepository.deleteById(collection, identifierMap) }
  }

  @Test
  fun `(getAll) for should call get all for on repository`() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.getAllFor(any()) } returns listOf()

    collectionItemService.getAllFor(id)

    verify(exactly = 1) { collectionItemRepository.getAllFor(collection) }
  }

  @Test
  fun `(create) should call create on repository`() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.create(any(), any()) } returns mapOf()

    collectionItemService.create(id, identifierMap)

    verify(exactly = 1) { collectionItemRepository.create(collection, identifierMap) }
  }
}
