package io.webcontify.backend.collections.apis.relation

import helpers.setups.api.ApiTestSetup
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
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_FIELD
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION
import io.webcontify.backend.relations.*
import org.hamcrest.Matchers.*
import org.jooq.DSLContext
import org.jooq.impl.DSL.jsonbGetAttribute
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class DeleteRelationApiTest : ApiTestSetup() {

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

  private fun getMirrorFieldSizeForRelation(relationId: Long): Int {
    return dslContext
        .selectFrom(WEBCONTIFY_COLLECTION_FIELD)
        .where(
            WEBCONTIFY_COLLECTION_FIELD.TYPE.eq(WebcontifyCollectionFieldType.RELATION_MIRROR)
                .and(
                    jsonbGetAttribute(WEBCONTIFY_COLLECTION_FIELD.CONFIGURATION, "relationId")
                        .cast(Long::class.java)
                        .eq(relationId)))
        .fetch()
        .size
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
    assertEquals(getMirrorFieldSizeForRelation(relationId = relation.id), 1)
    assertEquals(getRelationSize(relationId = relation.id), 1)
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
    } When { delete("$RELATIONS_PATH/${relation.id}") } Then { status(HttpStatus.NO_CONTENT) }
    assertEquals(
        dslContext.fetch(postgresConstranintNameSQL(collection.name.camelToSnakeCase())).size, 1)
    assertEquals(getMirrorFieldSizeForRelation(relationId = relation.id), 0)
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
                    setOf(RelationFieldMapping(sourceCollectionPrimaryFieldId, relationField)),
                    setOf(
                        MirrorRelationFieldMapping("mirrorFieldFor$relationField", relationField))),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    relatedCollectionId,
                    setOf(RelationFieldMapping(relationField, sourceCollectionPrimaryFieldId)),
                    setOf()),
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
    assertEquals(getMirrorFieldSizeForRelation(relationId = relation.id), 1)
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
    assertEquals(getMirrorFieldSizeForRelation(relationId = relation.id), 0)
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
                    setOf(RelationFieldMapping(relationField, relatedCollectionPrimaryField)),
                    setOf()),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    relatedCollectionId,
                    setOf(RelationFieldMapping(relatedCollectionPrimaryField, relationField)),
                    setOf(
                        MirrorRelationFieldMapping("mirrorFieldFor$relationField", relationField))),
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
    assertEquals(getMirrorFieldSizeForRelation(relationId = relation.id), 1)
    assertEquals(getRelationSize(relationId = relation.id), 1)
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
    } When { delete("$RELATIONS_PATH/${relation.id}") } Then { status(HttpStatus.NO_CONTENT) }
    assertEquals(
        dslContext.fetch(postgresConstranintNameSQL(collection.name.camelToSnakeCase())).size, 1)
    assertEquals(getMirrorFieldSizeForRelation(relationId = relation.id), 0)
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
                CollectionRelationMapping(
                    sourceCollectionId,
                    setOf(),
                    setOf(
                        MirrorRelationFieldMapping(
                            "mirrorFieldFor$relatedCollectionPrimaryFieldId",
                            relatedCollectionPrimaryFieldId))),
            mappingCollectionMapping =
                MappingCollectionRelationMapping(
                    id = null,
                    fieldsMapping =
                        setOf(
                            RelationFieldMapping(
                                sourceCollectionPrimaryFieldId, relatedCollectionPrimaryFieldId))),
            referencedCollectionMapping =
                CollectionRelationMapping(
                    relatedCollectionId,
                    setOf(),
                    setOf(
                        MirrorRelationFieldMapping(
                            "mirrorFieldFor$sourceCollectionPrimaryFieldId",
                            sourceCollectionPrimaryFieldId))),
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
    assertEquals(5, dslContext.fetch(postgresConstranintNameSQL(mappingCollectionName!!)).size)
    assertEquals(getMirrorFieldSizeForRelation(relationId = relation.id), 2)
    assertEquals(getRelationSize(relationId = relation.id), 1)
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
    } When { delete("$RELATIONS_PATH/${relation.id}") } Then { status(HttpStatus.NO_CONTENT) }
    assertEquals(dslContext.fetch(postgresConstranintNameSQL(mappingCollectionName)).size, 3)
    assertEquals(getMirrorFieldSizeForRelation(relationId = relation.id), 0)
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
                    setOf(RelationFieldMapping(relationField, relatedCollectionPrimaryFieldId)),
                    setOf()),
            mappingCollectionMapping = null,
            referencedCollectionMapping =
                CollectionRelationMapping(
                    relatedCollectionId,
                    setOf(RelationFieldMapping(relatedCollectionPrimaryFieldId, relationField)),
                    setOf(
                        MirrorRelationFieldMapping("mirrorFieldFor$relationField", relationField))),
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
