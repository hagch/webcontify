package helpers.setups.api

import helpers.setups.TestContainerSetup
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Sql("/io/webcontify/backend/collections/apis/cleanup.sql")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class ApiTestSetup : TestContainerSetup() {

  @Autowired lateinit var mockMvc: MockMvc
}
