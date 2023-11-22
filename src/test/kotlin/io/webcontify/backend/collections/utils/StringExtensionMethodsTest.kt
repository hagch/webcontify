package io.webcontify.backend.collections.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class StringExtensionMethodsTest {

  @ParameterizedTest
  @MethodSource("provideMapForConversions")
  fun camelToSnakeCaseShouldConvert(toConvert: String, expected: String) {
    assertEquals(expected, toConvert.camelToSnakeCase())
  }

  @ParameterizedTest
  @MethodSource("provideMapForConversions")
  fun snakeToCamelCaseShouldConvert(expected: String, toConvert: String) {
    assertEquals(expected, toConvert.snakeToCamelCase())
  }

  companion object {
    @JvmStatic
    private fun provideMapForConversions(): List<Arguments> {
      return listOf(
          Arguments.of("thatIsWorking", "that_is_working"),
          Arguments.of("that-IsWorking", "that-_is_working"),
          Arguments.of("that\$IsWorking", "that\$_is_working"),
          Arguments.of("", ""))
    }
  }
}
