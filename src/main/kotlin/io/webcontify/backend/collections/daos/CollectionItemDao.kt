package io.webcontify.backend.collections.daos

import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.springframework.stereotype.Component

@Component
class CollectionItemDao(val dslContext: DSLContext) {

  fun getItemByIdFor(tableName: String, primaryKeyValueMap: Map<String, Any>): Map<String, Any> {
    val fields =
        primaryKeyValueMap.map { field("${tableName}.${it.key}").eq(it.value) }.toMutableList()
    val map =
        dslContext.selectFrom(tableName).where(fields).fetchOne { record ->
          primaryKeyValueMap.map { it.key to record.getValue(it.key) }.toMap()
        }
            ?: mutableMapOf()
    return map
  }
}
