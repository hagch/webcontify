package io.webcontify.tenant.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class TenantSelfServiceControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID TEST_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        if (tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_TENANT_ID).isEmpty()) {
            TenantEntity tenant = new TenantEntity();
            tenant.setId(TEST_TENANT_ID);
            tenant.setName("Self Service Test Tenant");
            tenant.setSlug("self-service-test");
            tenant.setStatus("ACTIVE");

            AuditInfo auditInfo = new AuditInfo();
            auditInfo.setCreatedAt(OffsetDateTime.now());
            auditInfo.setCreatedBy(SystemUser.ID);
            auditInfo.setUpdatedAt(OffsetDateTime.now());
            auditInfo.setUpdatedBy(SystemUser.ID);
            tenant.setAuditInfo(auditInfo);

            tenantRepository.save(tenant);
        }
    }

    @Test
    void shouldReturnConfigForAuthenticatedTenant() throws Exception {
        mockMvc.perform(get("/api/v1/tenant/config")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUpdateBrandingForAuthenticatedTenant() throws Exception {
        String request = """
                {
                    "primaryColor": "#1a2b3c",
                    "secondaryColor": "#4d5e6f"
                }
                """;

        mockMvc.perform(put("/api/v1/tenant/config/branding")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branding.primaryColor").value("#1a2b3c"))
                .andExpect(jsonPath("$.branding.secondaryColor").value("#4d5e6f"));
    }

    @Test
    void shouldReturnUpdatedConfigAfterBrandingUpdate() throws Exception {
        String request = """
                {
                    "primaryColor": "#aabbcc"
                }
                """;

        mockMvc.perform(put("/api/v1/tenant/config/branding")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request));

        mockMvc.perform(get("/api/v1/tenant/config")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branding.primaryColor").value("#aabbcc"));
    }

    @Test
    void shouldReturnBadRequestForInvalidColorFormat() throws Exception {
        String request = """
                {
                    "primaryColor": "not-a-valid-color"
                }
                """;

        mockMvc.perform(put("/api/v1/tenant/config/branding")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnForbiddenWhenPlatformAdminCallsSelfServiceEndpoint() throws Exception {
        // Kein X-Test-Tenant-Id Header -> Platform Admin wird injiziert
        mockMvc.perform(get("/api/v1/tenant/config"))
                .andExpect(status().isForbidden());
    }
}
