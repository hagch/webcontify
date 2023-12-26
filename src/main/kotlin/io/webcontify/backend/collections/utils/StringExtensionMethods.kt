package io.webcontify.backend.collections.utils

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.IdentifierMap
import io.webcontify.backend.collections.models.errors.ErrorCode

fun String.snakeToCamelCase(): String {
  val pattern = "_[a-z]".toRegex()
  return this.lowercase().replace(pattern) { it.value.last().uppercase() }
}

fun String.camelToSnakeCase(): String {
  val pattern = "(?<=.)[A-Z]".toRegex()
  return this.replace(pattern, "_$0").lowercase()
}

fun String?.mapNullStringToNull(): String? {
  return if (this == "null") {
    null
  } else {
    this
  }
}

fun String.isSlug(): Boolean {
  return this.contains("/")
}

fun String.toIdentifierMap(): IdentifierMap {
  return this.split("/")
      .apply {
        if (this.isEmpty()) {
          throw UnprocessableContentException(ErrorCode.INVALID_PATH_PARAMETERS)
        }
      }
      .chunked(2) {
        Pair(it.elementAtOrElse(0) { "" }.lowercase(), it.elementAtOrNull(1).mapNullStringToNull())
      }
      .associateBy({ it.first }, { it.second })
}
