package io.webcontify.tenant.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.IntegrationTestBase;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class TenantConfigControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID TEST_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        if (tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_TENANT_ID).isEmpty()) {
            TenantEntity tenant = new TenantEntity();
            tenant.setId(TEST_TENANT_ID);
            tenant.setName("Config Test Tenant");
            tenant.setSlug("config-test");
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
    void shouldReturnEmptyConfigForNewTenant() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants/{tenantId}/config", TEST_TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branding").isEmpty())
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.domain").isEmpty());
    }

    @Test
    void shouldUpdateBrandingConfig() throws Exception {
        String request = """
                {
                    "primaryColor": "#3b82f6",
                    "secondaryColor": "#64748b"
                }
                """;

        mockMvc.perform(
                        put("/api/v1/admin/tenants/{tenantId}/config/branding", TEST_TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branding.primaryColor").value("#3b82f6"))
                .andExpect(jsonPath("$.branding.secondaryColor").value("#64748b"));
    }

    @Test
    void shouldUpdateBrandingConfigWithNewFields() throws Exception {
        String request = """
                {
                    "primaryColor": "#3b82f6",
                    "secondaryColor": "#64748b",
                    "accentColor": "#10b981",
                    "fontFamily": "inter",
                    "borderRadius": "0.75rem"
                }
                """;

        mockMvc.perform(
                        put("/api/v1/admin/tenants/{tenantId}/config/branding", TEST_TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branding.primaryColor").value("#3b82f6"))
                .andExpect(jsonPath("$.branding.accentColor").value("#10b981"))
                .andExpect(jsonPath("$.branding.fontFamily").value("inter"))
                .andExpect(jsonPath("$.branding.borderRadius").value("0.75rem"));
    }

    @Test
    void shouldUpdateEmailConfigAndNotReturnPassword() throws Exception {
        String request = """
                {
                    "smtpHost": "smtp.example.com",
                    "smtpPort": 587,
                    "smtpUser": "user@example.com",
                    "smtpPassword": "secret123",
                    "fromAddress": "noreply@example.com",
                    "fromName": "Test Tenant"
                }
                """;

        mockMvc.perform(
                        put("/api/v1/admin/tenants/{tenantId}/config/email", TEST_TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email.smtpHost").value("smtp.example.com"))
                .andExpect(jsonPath("$.email.smtpPort").value(587))
                .andExpect(jsonPath("$.email.smtpUser").value("user@example.com"))
                .andExpect(jsonPath("$.email.fromAddress").value("noreply@example.com"))
                .andExpect(jsonPath("$.email.fromName").value("Test Tenant"))
                .andExpect(jsonPath("$.email.smtpPassword").doesNotExist());
    }

    @Test
    void shouldUpdateDomainConfig() throws Exception {
        String request = """
                {
                    "customDomain": "app.example.com"
                }
                """;

        mockMvc.perform(
                        put("/api/v1/admin/tenants/{tenantId}/config/domain", TEST_TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domain.customDomain").value("app.example.com"));
    }

    @Test
    void shouldPreserveBrandingWhenUpdatingEmail() throws Exception {
        // First: set branding
        String brandingRequest = """
                {
                    "primaryColor": "#ff0000",
                    "secondaryColor": "#00ff00"
                }
                """;
        mockMvc.perform(
                put("/api/v1/admin/tenants/{tenantId}/config/branding", TEST_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brandingRequest));

        // Then: set email
        String emailRequest = """
                {
                    "smtpHost": "smtp.test.com",
                    "smtpPort": 25,
                    "fromAddress": "test@test.com",
                    "fromName": "Test"
                }
                """;
        mockMvc.perform(
                        put("/api/v1/admin/tenants/{tenantId}/config/email", TEST_TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(emailRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branding.primaryColor").value("#ff0000"))
                .andExpect(jsonPath("$.email.smtpHost").value("smtp.test.com"));
    }

    @Test
    void shouldGetConfigAfterUpdates() throws Exception {
        // Set branding
        mockMvc.perform(
                put("/api/v1/admin/tenants/{tenantId}/config/branding", TEST_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryColor\": \"#abcdef\"}"));

        // Verify via GET
        mockMvc.perform(get("/api/v1/admin/tenants/{tenantId}/config", TEST_TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branding.primaryColor").value("#abcdef"));
    }
}
