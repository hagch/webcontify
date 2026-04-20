package io.webcontify;

import io.webcontify.identity.service.KeycloakAdminService;
import io.webcontify.identity.service.OpenFgaService;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.identity.service.TenantInfrastructureLookupService;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.security.TestSecurityConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfiguration.class)
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean protected PermissionService permissionService;
    @MockitoBean protected KeycloakAdminService keycloakAdminService;
    @MockitoBean protected OpenFgaService openFgaService;
    @MockitoBean protected FileStorageService fileStorageService;
    @MockitoBean protected TenantInfrastructureLookupService lookupService;
}
