package io.webcontify.backend.collections.apis.relation

import helpers.setups.api.ApiIntegrationTestSetup
import helpers.suppliers.CollectionApiCreateRequestSupplier
import helpers.suppliers.respones.WebContifyCollectionResponse
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.collections.utils.camelToSnakeCase
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import io.webcontify.backend.configurations.RELATIONS_PATH
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION
import io.webcontify.backend.relations.models.*
import org.hamcrest.Matchers.*
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class DeleteRelationApiIntegrationTest : ApiIntegrationTestSetup() {

  @Autowired lateinit var dslContext: DSLContext

  private fun postgresConstranintNameSQL(collectionName: String): String {
    return "SELECT con.conname\n" +
        "       FROM pg_catalog.pg_constraint con\n" +
        "            INNER JOIN pg_catalog.pg_class rel\n" +
        "                       ON rel.oid = con.conrelid\n" +
        "            INNER JOIN pg_catalog.pg_namespace nsp\n" +
        "                       ON nsp.oid = connamespace\n" +
        "             AND rel.relname = '$collectionName';"
  }

  private fun getRelationSize(relationId: Long): Int {
    return dslContext
        .selectFrom(WEBCONTIFY_COLLECTION_RELATION)
        .where(WEBCONTIFY_COLLECTION_RELATION.ID.eq(relationId))
        .fetch()
        .size
  }

  @Test
  fun `(DeleteRelation) endpoint should delete one to to one relation`() {
    val collection =
        createCollection(CollectionApiCreateRequestSupplier.getCollectionRelationField())
    val relatedCollection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    val relation = createOneToOneRelation(collection, relatedCollection)
    assertEquals(
        dslContext.fetch(postgresConstranintNameSQL(collection.name.camelToSnakeCase())).size, 2)
    assertEquals(getRelationSize(relationId = relation.id), 1)
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
    } When { delete("$RELATIONS_PATH/${relation.id}") } Then { status(HttpStatus.NO_CONTENT) }
    assertEquals(
        dslContext.fetch(postgresConstranintNameSQL(collection.name.camelToSnakeCase())).size, 1)
    assertEquals(getRelationSize(relationId = relation.id), 0)
  }

  @Test
  fun `(DeleteRelation) endpoint should delete one to many relation`() {
    val collection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    val relatedCollection =
        createCollection(CollectionApiCreateRequestSupplier.getCollectionRelationField())
    val relationField = relatedCollection.fields!!.first { !it.isPrimaryKey }.id!!
    val sourceCollectionId = collection.id!!
    val sourceCollectionPrimaryFieldId = collection.fields!!.first { it.isPrimaryKey }.id!!
    val relatedCollectionId = relatedCollection.id!!
    val relationRequest =
        CreateRelationRequest(
            sourceCollectionMapping =
                CollectionRelationMapping(
                    sourceCollectionId,
                    "relation1",
                    setOf(RelationFieldMapping(sourceCollectionPrimaryFieldId, relationField))),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    relatedCollectionId,
                    "relation1",
                    setOf(RelationFieldMapping(relationField, sourceCollectionPrimaryFieldId)),
                ),
            type = WebcontifyCollectionRelationType.ONE_TO_MANY)
    val relation =
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
        dslContext
            .fetch(postgresConstranintNameSQL(relatedCollection.name.camelToSnakeCase()))
            .size,
        2)
    assertEquals(getRelationSize(relationId = relation.id), 1)
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
    } When { delete("$RELATIONS_PATH/${relation.id}") } Then { status(HttpStatus.NO_CONTENT) }
    assertEquals(
        dslContext
            .fetch(postgresConstranintNameSQL(relatedCollection.name.camelToSnakeCase()))
            .size,
        1)
    assertEquals(getRelationSize(relationId = relation.id), 0)
  }

  @Test
  fun `(DeleteRelation) endpoint should delete many to one relation`() {
    val collection =
        createCollection(CollectionApiCreateRequestSupplier.getCollectionRelationField())
    val relatedCollection =
        createCollection(
            CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField())
    val sourceCollectionId = collection.id!!
    val relatedCollectionPrimaryField = relatedCollection.fields!!.first { it.isPrimaryKey }.id!!
    val relationField = collection.fields!!.first { !it.isPrimaryKey }.id!!
    val relatedCollectionId = relatedCollection.id!!
    val relationRequest =
        CreateRelationRequest(
            sourceCollectionMapping =
                CollectionRelationMapping(
                    sourceCollectionId,
                    "relation2",
                    setOf(RelationFieldMapping(relationField, relatedCollectionPrimaryField))),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    relatedCollectionId,
                    "relation2",
                    setOf(RelationFieldMapping(relatedCollectionPrimaryField, relationField)),
                ),
            type = WebcontifyCollectionRelationType.MANY_TO_ONE)
    val relation =
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
        dslContext.fetch(postgresConstranintNameSQL(collection.name.camelToSnakeCase())).size, 2)
    assertEquals(getRelationSize(relationId = relation.id), 1)
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
    } When { delete("$RELATIONS_PATH/${relation.id}") } Then { status(HttpStatus.NO_CONTENT) }
    assertEquals(
        dslContext.fetch(postgresConstranintNameSQL(collection.name.camelToSnakeCase())).size, 1)
    assertEquals(getRelationSize(relationId = relation.id), 0)
  }

  @Test
  fun `(DeleteRelation) endpoint should delete many to many relation`() {
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
                CollectionRelationMapping(sourceCollectionId, "relation3", setOf()),
            mappingCollectionMapping =
                MappingCollectionRelationMapping(
                    id = null,
                    name = "relation3",
                    fieldsMapping =
                        setOf(
                            RelationFieldMapping(
                                sourceCollectionPrimaryFieldId, relatedCollectionPrimaryFieldId))),
            referencedCollectionMapping =
                CollectionRelationMapping(relatedCollectionId, "relation3", setOf()),
            type = WebcontifyCollectionRelationType.MANY_TO_MANY)
    val relation =
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
    val mappingCollectionName =
        dslContext
            .selectFrom(WEBCONTIFY_COLLECTION)
            .where(WEBCONTIFY_COLLECTION.ID.eq(relation.mappingCollectionMapping!!.id))
            .fetchOne()!!
            .getValue(WEBCONTIFY_COLLECTION.NAME)
    assertEquals(3, dslContext.fetch(postgresConstranintNameSQL(mappingCollectionName!!)).size)
    assertEquals(getRelationSize(relationId = relation.id), 1)
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
    } When { delete("$RELATIONS_PATH/${relation.id}") } Then { status(HttpStatus.NO_CONTENT) }
    assertEquals(dslContext.fetch(postgresConstranintNameSQL(mappingCollectionName)).size, 1)
    assertEquals(getRelationSize(relationId = relation.id), 0)
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
                    "relation4",
                    setOf(RelationFieldMapping(relationField, relatedCollectionPrimaryFieldId))),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    relatedCollectionId,
                    "relation4",
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
