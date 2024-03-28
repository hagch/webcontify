package helpers.suppliers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionRelationApiCreateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationFieldDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

class CollectionRelationApiCreateRequestSupplier {

  companion object {
    fun getOneToOneRelation(
        fields: Set<WebContifyCollectionRelationFieldDto>,
        referencedCollectionId: Int
    ) =
        WebContifyCollectionRelationApiCreateRequest(
            "one_to_one",
            "One To One",
            WebcontifyCollectionRelationType.ONE_TO_ONE,
            referencedCollectionId,
            fields)

    fun getOneToManyRelation(
        fields: Set<WebContifyCollectionRelationFieldDto>,
        referencedCollectionId: Int
    ) =
        WebContifyCollectionRelationApiCreateRequest(
            "one_to_many",
            "One To Many",
            WebcontifyCollectionRelationType.ONE_TO_MANY,
            referencedCollectionId,
            fields)

    fun getManyToOneRelation(
        fields: Set<WebContifyCollectionRelationFieldDto>,
        referencedCollectionId: Int
    ) =
        WebContifyCollectionRelationApiCreateRequest(
            "many_to_one",
            "Many to One",
            WebcontifyCollectionRelationType.MANY_TO_ONE,
            referencedCollectionId,
            fields)

    fun getManyToManyRelation(
        fields: Set<WebContifyCollectionRelationFieldDto>,
        referencedCollectionId: Int
    ) =
        WebContifyCollectionRelationApiCreateRequest(
            "many_to_many",
            "Many to Many",
            WebcontifyCollectionRelationType.MANY_TO_MANY,
            referencedCollectionId,
            fields)
  }
}
