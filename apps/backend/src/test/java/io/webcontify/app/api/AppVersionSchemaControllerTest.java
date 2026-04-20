package io.webcontify.app.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.webcontify.IntegrationTestBase;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AppVersionSchemaControllerTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;

    private static final UUID TEST_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000299");
    private static final UUID TEST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        lenient().when(permissionService.check(any())).thenReturn(true);

        if (tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_TENANT_ID).isEmpty()) {
            TenantEntity tenant = new TenantEntity();
            tenant.setId(TEST_TENANT_ID);
            tenant.setName("Schema Test Tenant");
            tenant.setSlug("schema-test-tenant");
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
    void shouldReturn404ForNonExistentVersion() throws Exception {
        UUID nonExistentAppId = UUID.randomUUID();
        UUID nonExistentVersionId = UUID.randomUUID();

        mockMvc.perform(
                        get("/api/v1/apps/"
                                        + nonExistentAppId
                                        + "/versions/"
                                        + nonExistentVersionId
                                        + "/schema")
                                .header(
                                        TestSecurityConfiguration.TEST_TENANT_ID_HEADER,
                                        TEST_TENANT_ID)
                                .header(
                                        TestSecurityConfiguration.TEST_USER_ID_HEADER,
                                        TEST_USER_ID.toString()))
                .andExpect(status().isNotFound());
    }
}
