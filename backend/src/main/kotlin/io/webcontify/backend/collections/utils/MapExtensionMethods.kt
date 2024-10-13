package io.webcontify.backend.collections.utils

fun Map<String, Any?>.toKeyValueString(): String {
  return this.entries.joinToString { "${it.key}= ${it.value}" }
}
