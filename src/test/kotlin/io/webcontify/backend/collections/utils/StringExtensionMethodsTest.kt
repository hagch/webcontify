package io.webcontify.backend.collections.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

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

  @ParameterizedTest
  @ValueSource(strings = ["quote", ""])
  fun doubleQuoteShouldDoubleQuote(testString: String) {
    assertEquals("\"$testString\"", testString.doubleQuote())
  }

  companion object {
    @JvmStatic
    private fun provideMapForConversions(): List<Arguments> {
      return listOf(
          Arguments.of("thatIsWorking", "THAT_IS_WORKING"),
          Arguments.of("that-IsWorking", "THAT-_IS_WORKING"),
          Arguments.of("that\$IsWorking", "THAT\$_IS_WORKING"),
          Arguments.of("", ""))
    }
  }
}
