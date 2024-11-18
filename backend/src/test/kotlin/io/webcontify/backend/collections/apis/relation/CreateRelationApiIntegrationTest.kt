package io.webcontify.backend.collections.apis.relation

import helpers.setups.api.ApiIntegrationTestSetup
import helpers.suppliers.CollectionApiCreateRequestSupplier
import helpers.suppliers.respones.WebContifyCollectionResponse
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import io.webcontify.backend.configurations.RELATIONS_PATH
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import io.webcontify.backend.relations.models.*
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class CreateRelationApiIntegrationTest : ApiIntegrationTestSetup() {

  @Test
  fun `(CreateRelation) endpoint should create one to one relation`() {
    val collection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    val relatedCollection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
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

    assertEquals(
        MappingCollectionRelationMapping(
            id = relationRequest.sourceCollectionMapping.id,
            "relation1",
            fieldsMapping = relationRequest.sourceCollectionMapping.fieldsMapping),
        relationDto.sourceCollectionMapping)
    assertEquals(
        MappingCollectionRelationMapping(
            id = relationRequest.referencedCollectionMapping.id,
            "relation1",
            fieldsMapping = relationRequest.referencedCollectionMapping.fieldsMapping),
        relationDto.referencedCollectionMapping)
    assertNotNull(relationDto.id)
    assertEquals(WebcontifyCollectionRelationType.ONE_TO_ONE, relationDto.type)
  }

  @Test
  fun `(CreateRelation) endpoint should create one to many relation`() {
    val collection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    val relatedCollection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    val sourceCollectionId = collection.id!!
    val sourceCollectionPrimaryFieldId = collection.fields!!.first { it.isPrimaryKey }.id!!
    val relatedCollectionId = relatedCollection.id!!
    val relatedCollectionPrimaryFieldId = relatedCollection.fields!!.first { it.isPrimaryKey }.id!!
    val relationRequest =
        CreateRelationRequest(
            sourceCollectionMapping =
                CollectionRelationMapping(
                    sourceCollectionId,
                    "relation2",
                    setOf(
                        RelationFieldMapping(
                            sourceCollectionPrimaryFieldId, relatedCollectionPrimaryFieldId))),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    relatedCollectionId,
                    "relation2",
                    setOf(
                        RelationFieldMapping(
                            relatedCollectionPrimaryFieldId, sourceCollectionPrimaryFieldId))),
            type = WebcontifyCollectionRelationType.ONE_TO_MANY)
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

    assertEquals(
        MappingCollectionRelationMapping(
            id = relationRequest.sourceCollectionMapping.id,
            "relation2",
            fieldsMapping = relationRequest.sourceCollectionMapping.fieldsMapping),
        relationDto.sourceCollectionMapping)
    assertEquals(
        MappingCollectionRelationMapping(
            id = relationRequest.referencedCollectionMapping.id,
            "relation2",
            fieldsMapping = relationRequest.referencedCollectionMapping.fieldsMapping),
        relationDto.referencedCollectionMapping)
    assertNotNull(relationDto.id)
    assertEquals(WebcontifyCollectionRelationType.ONE_TO_MANY, relationDto.type)
  }

  @Test
  fun `(CreateRelation) endpoint should create many to one relation`() {
    val collection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    val relatedCollection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    val sourceCollectionId = collection.id!!
    val sourceCollectionPrimaryFieldId = collection.fields!!.first { it.isPrimaryKey }.id!!
    val relatedCollectionId = relatedCollection.id!!
    val relatedCollectionPrimaryFieldId = relatedCollection.fields!!.first { it.isPrimaryKey }.id!!
    val relationRequest =
        CreateRelationRequest(
            sourceCollectionMapping =
                CollectionRelationMapping(
                    sourceCollectionId,
                    "relation3",
                    setOf(
                        RelationFieldMapping(
                            sourceCollectionPrimaryFieldId, relatedCollectionPrimaryFieldId))),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    relatedCollectionId,
                    "relation3",
                    setOf(
                        RelationFieldMapping(
                            relatedCollectionPrimaryFieldId, sourceCollectionPrimaryFieldId))),
            type = WebcontifyCollectionRelationType.MANY_TO_ONE)
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

    assertEquals(
        MappingCollectionRelationMapping(
            id = relationRequest.sourceCollectionMapping.id,
            "relation3",
            fieldsMapping = relationRequest.sourceCollectionMapping.fieldsMapping),
        relationDto.sourceCollectionMapping)
    assertEquals(
        MappingCollectionRelationMapping(
            id = relationRequest.referencedCollectionMapping.id,
            "relation3",
            fieldsMapping = relationRequest.referencedCollectionMapping.fieldsMapping),
        relationDto.referencedCollectionMapping)
    assertNotNull(relationDto.id)
    assertEquals(WebcontifyCollectionRelationType.MANY_TO_ONE, relationDto.type)
  }

  @Test
  fun `(CreateRelation) endpoint should create many to many relation`() {
    val collection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    val relatedCollection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    val sourceCollectionId = collection.id!!
    val sourceCollectionPrimaryFieldId = collection.fields!!.first { it.isPrimaryKey }.id!!
    val relatedCollectionId = relatedCollection.id!!
    val relatedCollectionPrimaryFieldId = relatedCollection.fields!!.first { it.isPrimaryKey }.id!!
    val relationRequest =
        CreateRelationRequest(
            sourceCollectionMapping =
                CollectionRelationMapping(sourceCollectionId, "relation4", setOf()),
            mappingCollectionMapping =
                MappingCollectionRelationMapping(
                    id = null,
                    "relation4",
                    fieldsMapping =
                        setOf(
                            RelationFieldMapping(
                                sourceCollectionPrimaryFieldId, relatedCollectionPrimaryFieldId))),
            referencedCollectionMapping =
                CollectionRelationMapping(relatedCollectionId, "relation4", setOf()),
            type = WebcontifyCollectionRelationType.MANY_TO_MANY)
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

    assertEquals(1, relationDto.sourceCollectionMapping.fieldsMapping.size)
    assertEquals(1, relationDto.referencedCollectionMapping.fieldsMapping.size)
    assertNotNull(relationDto.mappingCollectionMapping)
    assertNotNull(relationDto.id)
    assertEquals(WebcontifyCollectionRelationType.MANY_TO_MANY, relationDto.type)
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
