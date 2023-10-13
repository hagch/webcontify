package io.webcontify.backend

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.jooq.JooqTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.PostgreSQLContainer

@JooqTest
@Sql("/cleanup.sql")
@ExtendWith(SpringExtension::class)
@Import(JooqTestConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName::class)
abstract class JooqTestSetup {

  companion object {
    @ServiceConnection
    val container: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:latest").withReuse(true).apply { this.start() }
  }
}
