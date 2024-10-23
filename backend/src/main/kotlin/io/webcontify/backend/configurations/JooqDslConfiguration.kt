package io.webcontify.backend.configurations

import com.fasterxml.jackson.annotation.JsonInclude
import javax.sql.DataSource
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultDSLContext
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration @EnableTransactionManagement class JooqDslConfiguration

@Bean
fun connectionProvider(dataSource: DataSource): DataSourceConnectionProvider {
  return DataSourceConnectionProvider(TransactionAwareDataSourceProxy(dataSource))
}

@Bean
fun dsl(provider: DataSourceConnectionProvider): DefaultDSLContext {
  return DefaultDSLContext(configuration(provider))
}

@Bean
fun jsonCustomizer(): Jackson2ObjectMapperBuilderCustomizer {
  return Jackson2ObjectMapperBuilderCustomizer { builder ->
    builder
        .serializationInclusion(JsonInclude.Include.USE_DEFAULTS)
        .serializers(CustomJSONSerializer())
  }
}

private fun configuration(provider: DataSourceConnectionProvider): DefaultConfiguration {
  val configuration = DefaultConfiguration()
  configuration.set(provider)
  return configuration
}
