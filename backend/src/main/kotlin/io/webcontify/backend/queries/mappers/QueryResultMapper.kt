package io.webcontify.backend.queries.mappers

import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.services.field.handler.FieldHandlerStrategy
import io.webcontify.backend.jooq.enums.WebcontifyQueryAggregationType
import io.webcontify.backend.queries.models.QueryRelationInfoTree
import java.util.stream.Collectors
import org.springframework.stereotype.Component

@Component
class QueryResultMapper(private val fieldHandlerStrategy: FieldHandlerStrategy) {

  fun mapToItems(
      entries: List<Map<String, Any?>>,
      root: QueryRelationInfoTree,
  ): List<Item> {
    return aggregateToItems(entries, root, null)
  }

  private fun aggregateToItems(
      entries: List<Item>,
      root: QueryRelationInfoTree,
      prefix: String?
  ): List<Item> {
    val fields = root.collection.fields ?: emptyList()
    val grouped =
        entries
            .stream()
            .collect(
                Collectors.groupingBy(
                    { entry ->
                      fields.associate { field ->
                        field.name to entry[root.getName(prefix) + field.name]
                      }
                    },
                    Collectors.toList()))
            .filter { group ->
              !group.key
                  .filter { entry ->
                    root.collection.getPrimaryFields().any { entry.key == it.name }
                  }
                  .values
                  .contains(null)
            }
    return grouped.map { getGroupMappedToItem(root, it, prefix) }.toList()
  }

  private fun getGroupMappedToItem(
      root: QueryRelationInfoTree,
      entry: Map.Entry<Map<String, Any?>, MutableList<Item>>,
      prefix: String?
  ): MutableMap<String, Any?> {
    val map =
        fieldHandlerStrategy.castItemToJavaTypes(root.collection.fields, entry.key).toMutableMap()
    val children =
        root.childRelationTrees.associate {
          it.let { child ->
            val key = child.getRelationCollectionName(child)
            val aggregatedItems = aggregateToItems(entry.value, child, root.getName(prefix))
            val value: Any? =
                if (child.type == WebcontifyQueryAggregationType.OBJECT) {
                  if (aggregatedItems.isEmpty()) {
                    null
                  } else {
                    aggregatedItems.first()
                  }
                } else {
                  aggregatedItems
                }
            return@let key to value
          }
        }
    map.putAll(children)
    return map
  }
}
