package helpers.setups

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer

abstract class TestContainerSetup {

  companion object {
    @ServiceConnection
    val container: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:latest").withReuse(true).apply { this.start() }
  }
}
