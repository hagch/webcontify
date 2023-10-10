package io.webcontify.backend.collections.utils

fun String.snakeToCamelCase(): String {
  val pattern = "_[a-z]".toRegex()
  return replace(pattern) { it.value.last().uppercase() }
}

fun String.camelToSnakeCase(): String {
  val pattern = "(?<=.)[A-Z]".toRegex()
  return this.replace(pattern, "_$0").uppercase()
}

fun String.doubleQuote(): String {
  return "\"${this}\""
}
