package helpers.setups

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer

abstract class TestContainerSetup {

  companion object {
    @ServiceConnection
    val container: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:latest").apply {
          this.withPassword("test").withUsername("test").withExposedPorts(5432).start()
        }
  }
}
