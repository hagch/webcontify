package io.webcontify.backend.collections.apis.item

import helpers.asserts.equalsTo
import helpers.asserts.errorSizeEquals
import helpers.asserts.instanceEquals
import helpers.asserts.timestampNotNull
import helpers.setups.api.ApiIntegrationTestSetup
import helpers.suppliers.CollectionApiCreateRequestSupplier
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.DECIMAL_FIELD
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.NUMBER_RELATION_FIELD
import helpers.suppliers.respones.WebContifyCollectionResponse
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import io.webcontify.backend.collections.utils.toKeyValueString
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import io.webcontify.backend.configurations.RELATIONS_PATH
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import io.webcontify.backend.relations.models.CollectionRelationMapping
import io.webcontify.backend.relations.models.CreateRelationRequest
import io.webcontify.backend.relations.models.RelationDto
import io.webcontify.backend.relations.models.RelationFieldMapping
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class CreateItemRelationApiIntegrationTest : ApiIntegrationTestSetup() {

  @Test
  fun `(CreateItemRelation) should throw error on relation with value which does not exist`() {
    val collection =
        createCollection(CollectionApiCreateRequestSupplier.getCollectionRelationField())
    val relatedCollection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    createOneToOneRelation(collection, relatedCollection)
    val item =
        mapOf(
            NUMBER_RELATION_FIELD.name to 1,
        )
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(item)
        } When
            {
              post("$COLLECTIONS_PATH/${collection.id}/items")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.timestampNotNull()
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/${collection.id}/items")
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.CONSTRAINT_EXCEPTION,
        String.format(
            ErrorCode.CONSTRAINT_EXCEPTION.message, item.toKeyValueString(), collection.id))
  }

  @Test
  fun `(CreateItemRelation) should create item with referenced field`() {
    val collection =
        createCollection(CollectionApiCreateRequestSupplier.getCollectionRelationField())
    val relatedCollection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    createOneToOneRelation(collection, relatedCollection)
    val createdItemIdToRelate =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(mapOf(DECIMAL_FIELD.name to 1.0))
        } When
            {
              post("$COLLECTIONS_PATH/${relatedCollection.id}/items")
            } Then
            {
              status(HttpStatus.CREATED)
            } Extract
            {
              body().jsonPath().getInt("numberField")
            }
    val item =
        mapOf(
            NUMBER_RELATION_FIELD.name to createdItemIdToRelate,
        )
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          post("$COLLECTIONS_PATH/${collection.id}/items")
        } Then
        {
          status(HttpStatus.CREATED)
          body("numberField", notNullValue())
          body("relationField", equalTo(createdItemIdToRelate))
        }
  }

  private fun createOneToOneRelation(
      collection: WebContifyCollectionResponse,
      relatedCollection: WebContifyCollectionResponse
  ): RelationDto {
    val sourceCollectionId = collection.id!!
    val relationField = collection.fields!!.first { !it.isPrimaryKey }.id!!
    val relatedCollectionId = relatedCollection.id!!
    val relatedCollectionPrimaryFieldId = relatedCollection.fields!!.first { it.isPrimaryKey }.id!!
    val relationRequest =
        CreateRelationRequest(
            sourceCollectionMapping =
                CollectionRelationMapping(
                    sourceCollectionId,
                    "oneToOneRelation",
                    setOf(RelationFieldMapping(relationField, relatedCollectionPrimaryFieldId))),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    relatedCollectionId,
                    "oneToOneRelation",
                    setOf(RelationFieldMapping(relatedCollectionPrimaryFieldId, relationField))),
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
    return relationDto
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
