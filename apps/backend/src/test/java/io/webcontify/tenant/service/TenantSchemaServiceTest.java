package io.webcontify.tenant.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TenantSchemaServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private TenantSchemaService tenantSchemaService;

    @BeforeEach
    void setUp() {
        tenantSchemaService = new TenantSchemaService(jdbcTemplate);
    }

    @Test
    void shouldCreateSchemaWithSlug() {
        tenantSchemaService.createSchema("acme");

        var captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(captor.capture());
        assertThat(captor.getValue()).isEqualTo("CREATE SCHEMA IF NOT EXISTS tenant_acme");
    }

    @Test
    void shouldReplaceHyphensWithUnderscoresInSchemaName() {
        tenantSchemaService.createSchema("my-test-tenant");

        var captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(captor.capture());
        assertThat(captor.getValue()).isEqualTo("CREATE SCHEMA IF NOT EXISTS tenant_my_test_tenant");
    }

    @Test
    void shouldDropSchemaWithCascade() {
        tenantSchemaService.dropSchema("acme");

        var captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(captor.capture());
        assertThat(captor.getValue()).isEqualTo("DROP SCHEMA IF EXISTS tenant_acme CASCADE");
    }

    @Test
    void shouldDropSchemaWithHyphenConversion() {
        tenantSchemaService.dropSchema("my-test-tenant");

        var captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(captor.capture());
        assertThat(captor.getValue()).isEqualTo("DROP SCHEMA IF EXISTS tenant_my_test_tenant CASCADE");
    }

    @Test
    void shouldRejectSlugWithInvalidCharactersAfterConversion() {
        assertThatThrownBy(() -> tenantSchemaService.createSchema("acme; DROP TABLE users"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid slug");
    }

    @Test
    void shouldRejectEmptySlug() {
        assertThatThrownBy(() -> tenantSchemaService.createSchema(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullSlug() {
        assertThatThrownBy(() -> tenantSchemaService.createSchema(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptSlugWithNumbersAndUnderscores() {
        tenantSchemaService.createSchema("tenant_123");

        var captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(captor.capture());
        assertThat(captor.getValue()).isEqualTo("CREATE SCHEMA IF NOT EXISTS tenant_tenant_123");
    }
}
