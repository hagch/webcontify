package io.webcontify.backend.collections.apis.view

import helpers.setups.api.ApiIntegrationTestSetup
import helpers.suppliers.CollectionApiCreateRequestSupplier
import helpers.suppliers.respones.WebContifyCollectionResponse
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.controllers.CollectionController
import io.webcontify.backend.collections.controllers.CollectionItemController
import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import io.webcontify.backend.configurations.RELATIONS_PATH
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import io.webcontify.backend.queries.models.QueryCreateRequestDto
import io.webcontify.backend.queries.models.QueryRelationTreeRequestDto
import io.webcontify.backend.relations.*
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class CreateViewApiIntegrationTest : ApiIntegrationTestSetup() {

  @Autowired lateinit var collectionItemController: CollectionItemController
  @Autowired lateinit var collectionController: CollectionController

  @Test
  fun `(CreateView) endpoint should create view with all join types`() {
    val userCollection = createCollection(CollectionApiCreateRequestSupplier.getUserCollection())
    val userPrimaryField = userCollection.fields!!.first { it.isPrimaryKey }
    val userOrganizationField = userCollection.fields!!.first { it.name == "organizationId" }
    val organizationCollection =
        createCollection(CollectionApiCreateRequestSupplier.getOrganizationCollection())
    val organizationCollectionPrimaryField =
        organizationCollection.fields!!.first { it.isPrimaryKey }
    val userChildCollection =
        createCollection(CollectionApiCreateRequestSupplier.getUserChildrenCollection())
    val userChildUserField = userChildCollection.fields!!.first { it.name == "userId" }
    val networkProviderCollection =
        createCollection(CollectionApiCreateRequestSupplier.getNetworkProviderCollection())
    val networkProviderPrimaryField = networkProviderCollection.fields!!.first { it.isPrimaryKey }

    val userOrganizationRelationRequest =
        CreateRelationRequest(
            sourceCollectionMapping =
                CollectionRelationMapping(
                    userCollection.id!!,
                    "users",
                    setOf(
                        RelationFieldMapping(
                            userOrganizationField.id!!, organizationCollectionPrimaryField.id!!))),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    organizationCollection.id!!,
                    "organization",
                    setOf(
                        RelationFieldMapping(
                            organizationCollectionPrimaryField.id, userOrganizationField.id))),
            type = WebcontifyCollectionRelationType.ONE_TO_ONE)

    val userUserChildRelationRequest =
        CreateRelationRequest(
            sourceCollectionMapping =
                CollectionRelationMapping(
                    userCollection.id!!,
                    "users",
                    setOf(RelationFieldMapping(userPrimaryField.id!!, userChildUserField.id!!))),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    userChildCollection.id!!,
                    "children",
                    setOf(RelationFieldMapping(userChildUserField.id, userPrimaryField.id))),
            type = WebcontifyCollectionRelationType.ONE_TO_MANY)

    val networkProviderRelationRequest =
        CreateRelationRequest(
            sourceCollectionMapping =
                CollectionRelationMapping(userCollection.id!!, "user", setOf()),
            mappingCollectionMapping =
                MappingCollectionRelationMapping(
                    null,
                    "userNetworkProviders",
                    setOf(
                        RelationFieldMapping(
                            userPrimaryField.id!!, networkProviderPrimaryField.id!!))),
            referencedCollectionMapping =
                CollectionRelationMapping(
                    networkProviderCollection.id!!, "networkProvider", setOf()),
            type = WebcontifyCollectionRelationType.MANY_TO_MANY)

    val userOrganizationRelationDto =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(userOrganizationRelationRequest)
        } When
            {
              post(RELATIONS_PATH)
            } Then
            {
              status(HttpStatus.CREATED)
            } Extract
            {
              body().`as`(RelationDto::class.java)
            }

    val userUserChildRelationDto =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(userUserChildRelationRequest)
        } When
            {
              post(RELATIONS_PATH)
            } Then
            {
              status(HttpStatus.CREATED)
            } Extract
            {
              body().`as`(RelationDto::class.java)
            }

    val networkProviderRelationDto =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(networkProviderRelationRequest)
        } When
            {
              post(RELATIONS_PATH)
            } Then
            {
              status(HttpStatus.CREATED)
            } Extract
            {
              body().`as`(RelationDto::class.java)
            }
    collectionItemController.create(
        organizationCollection.id, mapOf("organizationName" to "Organization 1"))
    collectionItemController.create(
        organizationCollection.id, mapOf("organizationName" to "Organization 2"))
    collectionItemController.create(
        networkProviderCollection.id, mapOf("networkProviderName" to "NetworkProvider 1"))
    collectionItemController.create(
        networkProviderCollection.id, mapOf("networkProviderName" to "NetworkProvider 2"))
    collectionItemController.create(
        userCollection.id, mapOf("userName" to "User 1", "organizationId" to 1))
    collectionItemController.create(
        userCollection.id, mapOf("userName" to "User 2", "organizationId" to 2))
    collectionItemController.create(
        userCollection.id, mapOf("userName" to "User 3", "organizationId" to null))
    collectionItemController.create(
        userChildCollection.id, mapOf("userId" to 1, "childName" to "Child 1"))
    collectionItemController.create(
        userChildCollection.id, mapOf("userId" to 1, "childName" to "Child 2"))
    collectionItemController.create(
        userChildCollection.id, mapOf("userId" to 2, "childName" to "Child 3"))
    var mappingCollection =
        collectionController.getById(networkProviderRelationDto.mappingCollectionMapping?.id!!)
    var userReferencedField =
        mappingCollection.body!!
            .fields
            .filter { it.name.contains(userCollection.id.toString()) }
            .map { it.name }
            .first()
    var networkProviderReferencedField =
        mappingCollection.body!!
            .fields
            .filter { it.name.contains(networkProviderCollection.id.toString()) }
            .map { it.name }
            .first()
    // TODO cannot be created on mapping tables currently
    collectionItemController.create(
        networkProviderRelationDto.mappingCollectionMapping?.id!!,
        mapOf(userReferencedField to 1, networkProviderReferencedField to 1))
    collectionItemController.create(
        networkProviderRelationDto.mappingCollectionMapping?.id!!,
        mapOf(userReferencedField to 1, networkProviderReferencedField to 2))
    collectionItemController.create(
        networkProviderRelationDto.mappingCollectionMapping?.id!!,
        mapOf(userReferencedField to 3, networkProviderReferencedField to 1))
    var viewId =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(
              QueryCreateRequestDto(
                  "user_view",
                  listOf(
                      QueryRelationTreeRequestDto(
                          userOrganizationRelationDto.id, organizationCollection.id, emptyList()),
                      QueryRelationTreeRequestDto(
                          userUserChildRelationDto.id, userChildCollection.id, emptyList()),
                      QueryRelationTreeRequestDto(
                          networkProviderRelationDto.id,
                          networkProviderRelationDto.mappingCollectionMapping!!.id!!,
                          listOf(
                              QueryRelationTreeRequestDto(
                                  networkProviderRelationDto.id,
                                  networkProviderCollection.id,
                                  emptyList())),
                      ))))
        } When
            {
              post("$COLLECTIONS_PATH/${userCollection.id}/views")
            } Then
            {
              status(HttpStatus.OK)
            } Extract
            {
              body().`as`(Long::class.java)
            }
    var body =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
        } When
            {
              get("$COLLECTIONS_PATH/views/${viewId}")
            } Then
            {
              status(HttpStatus.OK)
            } Extract
            {
              body().`as`(typeReference<List<Item>>())
            }
    assertThat(body).hasSize(3)
  }

  @Test
  fun `(CreateView) endpoint should create view`() {
    val collection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    createTestItemWithRelation(collection.id!!)
    val relatedCollection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    createTestItemWithRelation(relatedCollection.id!!)
    val sourceCollectionId = collection.id!!
    val sourceCollectionPrimaryFieldId = collection.fields!!.first { it.isPrimaryKey }.id!!
    val relatedCollectionId = relatedCollection.id!!
    val relatedCollectionPrimaryFieldId = relatedCollection.fields!!.first { it.isPrimaryKey }.id!!
    val relationRequest =
        CreateRelationRequest(
            sourceCollectionMapping =
                CollectionRelationMapping(
                    sourceCollectionId,
                    "relation1",
                    setOf(
                        RelationFieldMapping(
                            sourceCollectionPrimaryFieldId, relatedCollectionPrimaryFieldId))),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    relatedCollectionId,
                    "relation1",
                    setOf(
                        RelationFieldMapping(
                            relatedCollectionPrimaryFieldId, sourceCollectionPrimaryFieldId))),
            type = WebcontifyCollectionRelationType.ONE_TO_ONE)
    val relationDto =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(relationRequest)
        } When
            {
              post(RELATIONS_PATH)
            } Then
            {
              status(HttpStatus.CREATED)
            } Extract
            {
              body().`as`(RelationDto::class.java)
            }

    var viewId =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(
              QueryCreateRequestDto(
                  "test_view",
                  listOf(
                      QueryRelationTreeRequestDto(
                          relationDto.id, relatedCollectionId, emptyList()))))
        } When
            {
              post("$COLLECTIONS_PATH/${sourceCollectionId}/views")
            } Then
            {
              status(HttpStatus.OK)
            } Extract
            {
              body().`as`(Long::class.java)
            }
    var body =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
        } When
            {
              get("$COLLECTIONS_PATH/views/${viewId}")
            } Then
            {
              status(HttpStatus.OK)
            } Extract
            {
              body().`as`(typeReference<List<Item>>())
            }
    assertThat(body).hasSize(1)
  }

  private fun createTestItemWithRelation(collectionId: Long) {
    val uuid = UUID.randomUUID().toString()
    val item =
        mapOf(
            "decimalField" to 123.01,
            "textField" to "Thats an text",
            "timestampField" to "2000-10-31T01:30:00",
            "booleanField" to true,
            "uuidField" to uuid,
        )
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          post("$COLLECTIONS_PATH/${collectionId}/items")
        } Then
        {
          status(HttpStatus.CREATED)
          body("decimalField", CoreMatchers.equalTo(123.01f))
          body("textField", CoreMatchers.equalTo("Thats an text"))
          body("timestampField", CoreMatchers.equalTo("2000-10-31T01:30:00"))
          body("booleanField", CoreMatchers.equalTo(true))
          body("uuidField", CoreMatchers.equalTo(uuid))
          body("numberField", CoreMatchers.notNullValue())
        }
  }

  private fun createCollection(
      collection: WebContifyCollectionApiCreateRequest
  ): WebContifyCollectionResponse {
    return Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(collection)
    } When
        {
          post(COLLECTIONS_PATH)
        } Then
        {
          status(HttpStatus.CREATED)
          body("id", notNullValue())
          body(
              "fields",
              hasSize<MutableCollection<Map<String, Any>>>(equalTo(collection.fields.size)))
          body("name", equalTo(collection.name))
          body("displayName", equalTo(collection.displayName))
        } Extract
        {
          body().`as`(WebContifyCollectionResponse::class.java)
        }
  }
}
