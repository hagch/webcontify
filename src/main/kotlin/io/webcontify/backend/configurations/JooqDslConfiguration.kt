package io.webcontify.backend.configurations

import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultDSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

@Configuration
@EnableTransactionManagement
class JooqDslConfiguration

@Bean
fun connectionProvider(dataSource: DataSource): DataSourceConnectionProvider {
    return DataSourceConnectionProvider(TransactionAwareDataSourceProxy(dataSource))
}

@Bean
fun dsl(provider: DataSourceConnectionProvider): DefaultDSLContext {
    return DefaultDSLContext(configuration(provider))
}

private fun configuration(provider: DataSourceConnectionProvider): DefaultConfiguration {
    val configuration = DefaultConfiguration()
    configuration.set(provider)
    return configuration
}

