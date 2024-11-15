package helpers.setups

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class PostgresApplicationConfiguration {

  @Bean
  @ServiceConnection
  fun postgresContainer(): PostgreSQLContainer<*> {
    var container = PostgreSQLContainer("postgres:latest").withReuse(false)
    container.withUsername("test")
    container.withPassword("test")
    return container
  }
}
