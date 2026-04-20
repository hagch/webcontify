package io.webcontify.shared.persistence;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RlsConfiguration {

    @Bean
    HibernatePropertiesCustomizer hibernateMultiTenancyCustomizer(
            RlsConnectionProvider connectionProvider,
            RlsTenantIdentifierResolver tenantIdentifierResolver) {
        return properties -> {
            properties.put(
                    AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(
                    AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        };
    }
}
