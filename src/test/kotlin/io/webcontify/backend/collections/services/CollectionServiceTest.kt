package io.webcontify.backend.collections.services

import helpers.suppliers.collectionWithFields
import helpers.suppliers.collectionWithNameCollection
import helpers.suppliers.collectionWithNameTest
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.repositories.CollectionFieldRepository
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class CollectionServiceTest {

  @RelaxedMockK lateinit var collectionFieldRepository: CollectionFieldRepository

  @RelaxedMockK lateinit var collectionTableRepository: CollectionTableRepository

  @RelaxedMockK lateinit var collectionRepository: CollectionRepository

  @RelaxedMockK lateinit var collectionMapper: CollectionMapper

  @InjectMockKs lateinit var service: CollectionService

  private val collection = collectionWithFields(listOf(Pair("id", true)))

  @Test
  fun `(getAll) should call collection repository`() {
    service.getAll()

    verify(exactly = 1) { collectionRepository.getAll() }
  }

  @Test
  fun `(getById) should call collection repository`() {
    service.getById(1)

    verify(exactly = 1) { collectionRepository.getById(1) }
  }

  @Test
  fun `(getById) should delete collection and table`() {
    every { collectionRepository.getById(0) } returns collection

    service.deleteById(0)

    verify(exactly = 1) {
      collectionRepository.deleteById(0)
      collectionTableRepository.delete(collection.name)
    }
  }

  @Test
  fun `(create) should create collection`() {
    every { collectionRepository.create(collection) } returns collection

    service.create(collection)

    verify(exactly = 1) {
      collectionRepository.create(collection)
      collectionTableRepository.create(any())
      collectionFieldRepository.create(any())
    }
  }

  @Test
  fun `(create) should create collection and fields and table`() {
    every { collectionRepository.create(collection) } returns collection

    service.create(collection)

    verify(exactly = 1) {
      collectionRepository.create(collection)
      collectionTableRepository.create(any())
      collectionFieldRepository.create(collection.fields!![0])
    }
  }

  @Test
  fun `(update) should update collection and table name`() {
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
