package io.webcontify.backend.configurations

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.jooq.JSON

class CustomJSONSerializer : StdSerializer<JSON>(JSON::class.java) {
  override fun serialize(value: JSON?, gen: JsonGenerator?, provider: SerializerProvider?) {
    gen?.writeRawValue(value?.data())
  }
}
