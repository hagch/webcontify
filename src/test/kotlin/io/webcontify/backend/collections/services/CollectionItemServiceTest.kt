package io.webcontify.backend.collections.services

import helpers.suppliers.collectionWithColumns
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

  private val collection = collectionWithColumns(listOf(Pair("test", true)))
  private val id = 1
  private val identifierMap = mapOf(Pair("test", id))

  @Test
  fun getByIdWithMapShouldCallGetByIdForWithCollectionAndIdentifierMap() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.getByIdFor(any(), any()) } returns mapOf()

    collectionItemService.getById(id, identifierMap)

    verify(exactly = 1) { collectionItemRepository.getByIdFor(collection, identifierMap) }
  }

  @Test
  fun getByIdShouldCallGetByIdForWithCollectionAndIdentifierMap() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.getByIdFor(any(), any()) } returns mapOf()

    collectionItemService.getById(id, id)

    verify(exactly = 1) { collectionItemRepository.getByIdFor(collection, identifierMap) }
  }

  @Test
  fun deleteByIdWithMapShouldCallDeleteByIdWithCollectionAndIdentifierMap() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.deleteById(any(), any()) } returns Unit

    collectionItemService.deleteById(id, identifierMap)

    verify(exactly = 1) { collectionItemRepository.deleteById(collection, identifierMap) }
  }

  @Test
  fun deleteByIdWithMapShouldThrowExceptionIfMapSizeIsUnequalToPrimaryKeysOfCollection() {
    every { collectionService.getById(any()) } returns collection

    assertThrows<UnprocessableContentException> { collectionItemService.deleteById(id, mapOf()) }
  }

  @Test
  fun deleteByIdWithMapShouldThrowExceptionIfPrimaryKeyIsNotContainedInIdentifierMap() {
    every { collectionService.getById(any()) } returns collection

    assertThrows<UnprocessableContentException> {
      collectionItemService.deleteById(id, mapOf(Pair("not", null)))
    }
  }

  @Test
  fun deleteByIdShouldCallDeleteByIdWithCollectionAndIdentifierMap() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.deleteById(any(), any()) } returns Unit

    collectionItemService.deleteById(id, id)

    verify(exactly = 1) { collectionItemRepository.deleteById(collection, identifierMap) }
  }

  @Test
  fun getAllForShouldCallGetAllForOnRepository() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.getAllFor(any()) } returns listOf()

    collectionItemService.getAllFor(id)

    verify(exactly = 1) { collectionItemRepository.getAllFor(collection) }
  }

  @Test
  fun createShouldCallCreateOnRepository() {
    every { collectionService.getById(any()) } returns collection
    every { collectionItemRepository.create(any(), any()) } returns mapOf()

    collectionItemService.create(id, identifierMap)

    verify(exactly = 1) { collectionItemRepository.create(collection, identifierMap) }
  }
}
