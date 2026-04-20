package io.webcontify.shared.persistence;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource tenantAwareDataSource(DataSource dataSource) {
        return new TenantAwareDataSource(dataSource);
    }
}
