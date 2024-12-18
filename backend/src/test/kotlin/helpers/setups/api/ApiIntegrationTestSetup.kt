package helpers.setups.api

import helpers.setups.TestContainerSetup
import io.restassured.common.mapper.TypeRef
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles(profiles = ["test"])
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class ApiIntegrationTestSetup : TestContainerSetup() {

  @Autowired lateinit var mockMvc: MockMvc

  protected final inline fun <reified T> typeReference() = object : TypeRef<T>() {}
}
