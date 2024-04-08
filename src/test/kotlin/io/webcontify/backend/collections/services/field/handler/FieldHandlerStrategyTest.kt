package io.webcontify.backend.collections.services.field.handler

import helpers.suppliers.firstSqlInsertedField
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.services.field.handler.strategies.BooleanFieldHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FieldHandlerStrategyTest {

  private val handler = BooleanFieldHandler()

  private val strategy = FieldHandlerStrategy(mapOf(Pair("BOOLEAN", handler)))

  @Test
  fun `(generateHandlerMap) should register handlers`() {
    val selectedHandler =
        strategy.getHandlerFor(
            WebContifyCollectionFieldDto(
                null, null, "", "", WebcontifyCollectionFieldType.BOOLEAN, true, null))

    assertEquals(handler, selectedHandler)
  }

  @Test
  fun `(getHandlerFor) should throw unprocessable content exception on no handler found`() {
    assertThrows<UnprocessableContentException> { strategy.getHandlerFor(firstSqlInsertedField()) }
  }
}
