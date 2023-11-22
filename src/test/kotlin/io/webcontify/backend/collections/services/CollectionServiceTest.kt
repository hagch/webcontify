package io.webcontify.backend.collections.services

import helpers.suppliers.collectionWithColumns
import helpers.suppliers.collectionWithNameCollection
import helpers.suppliers.collectionWithNameTest
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.repositories.CollectionColumnRepository
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class CollectionServiceTest {

  @RelaxedMockK lateinit var collectionColumnRepository: CollectionColumnRepository

  @RelaxedMockK lateinit var collectionTableRepository: CollectionTableRepository

  @RelaxedMockK lateinit var collectionRepository: CollectionRepository

  @RelaxedMockK lateinit var collectionMapper: CollectionMapper

  @InjectMockKs lateinit var service: CollectionService

  private val collection = collectionWithColumns(listOf(Pair("id", true)))

  @Test
  fun getAllShouldCallCollectionRepository() {
    service.getAll()

    verify(exactly = 1) { collectionRepository.getAll() }
  }

  @Test
  fun getByIdShouldCallCollectionRepository() {
    service.getById(1)

    verify(exactly = 1) { collectionRepository.getById(1) }
  }

  @Test
  fun getByIdShouldDeleteCollectionAndTable() {
    every { collectionRepository.getById(0) } returns collection

    service.deleteById(0)

    verify(exactly = 1) {
      collectionRepository.deleteById(0)
      collectionTableRepository.delete(collection.name)
    }
  }

  @Test
  fun createShouldCreateCollection() {
    every { collectionRepository.create(collection) } returns collection

    service.create(collection)

    verify(exactly = 1) {
      collectionRepository.create(collection)
      collectionTableRepository.create(any())
      collectionColumnRepository.create(any())
    }
  }

  @Test
  fun createShouldCreateCollectionAndColumnsAndTable() {
    every { collectionRepository.create(collection) } returns collection

    service.create(collection)

    verify(exactly = 1) {
      collectionRepository.create(collection)
      collectionTableRepository.create(any())
      collectionColumnRepository.create(collection.columns!![0])
    }
  }

  @Test
  fun updateShouldUpdateCollectionAndTableName() {
    val oldCollection = collectionWithNameTest()
    val collection = collectionWithNameCollection()
    every { collectionRepository.getById(any()) } returns oldCollection

    service.update(collection)

    verify(exactly = 1) {
      collectionRepository.update(collection)
      collectionTableRepository.updateName(collection.name, oldCollection.name)
    }
  }
}
