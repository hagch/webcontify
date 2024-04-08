package helpers.asserts

import io.webcontify.backend.collections.models.errors.Error
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import org.junit.jupiter.api.Assertions

fun ErrorResponse.errorSizeEquals(size: Int) {
  Assertions.assertEquals(size, errors.size)
}

fun Error.equalsTo(errorCode: ErrorCode, message: String) {
  Assertions.assertEquals(code, errorCode)
  Assertions.assertEquals(message, message)
}

fun Error.codeEquals(errorCode: ErrorCode) {
  Assertions.assertEquals(code, errorCode)
}

fun Error.messageContains(message: String) {
  Assertions.assertTrue(message.contains(message))
}

fun ErrorResponse.instanceEquals(instanceComparison: String) {
  Assertions.assertEquals(instance, instanceComparison)
}

fun ErrorResponse.timestampNotNull() {
  Assertions.assertNotNull(timestamp)
}
