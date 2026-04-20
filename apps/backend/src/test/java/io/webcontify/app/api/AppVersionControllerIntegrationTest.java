package io.webcontify.app.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.filestorage.FileStorageService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class AppVersionControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean FileStorageService fileStorageService;
    @MockitoBean PermissionService permissionService;

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final UUID TEST_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000199");
    private static final UUID TEST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        lenient().when(permissionService.check(any())).thenReturn(true);

        when(fileStorageService.uploadPrivateAsset(anyString(), anyString(), any(), anyString()))
                .thenReturn("files/snapshots/test-snapshot.json");

        if (tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_TENANT_ID).isEmpty()) {
            TenantEntity tenant = new TenantEntity();
            tenant.setId(TEST_TENANT_ID);
            tenant.setName("Version Test Tenant");
            tenant.setSlug("version-test-tenant");
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
    void shouldPublishAppAndCreateVersion() throws Exception {
        String appId = createApp("Publish Test App");

        String publishRequest = """
                { "version": "0.1.0", "releaseNotes": "Initial release" }
                """;

        mockMvc.perform(post("/api/v1/apps/" + appId + "/publish")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishRequest))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.version").value("0.1.0"))
                .andExpect(jsonPath("$.appId").value(appId))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.s3Key").isNotEmpty());
    }

    @Test
    void shouldListVersions() throws Exception {
        String appId = createApp("List Versions App");

        publishApp(appId, "0.1.0");
        publishApp(appId, "0.2.0");

        mockMvc.perform(get("/api/v1/apps/" + appId + "/versions")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].version").value("0.2.0"))
                .andExpect(jsonPath("$.items[1].version").value("0.1.0"));
    }

    @Test
    void shouldGetVersionDetail() throws Exception {
        String appId = createApp("Version Detail App");

        String versionId = publishApp(appId, "0.1.0");

        mockMvc.perform(get("/api/v1/apps/" + appId + "/versions/" + versionId)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(versionId))
                .andExpect(jsonPath("$.appId").value(appId))
                .andExpect(jsonPath("$.version").value("0.1.0"))
                .andExpect(jsonPath("$.s3Key").isNotEmpty());
    }

    @Test
    void shouldRejectDuplicateVersion() throws Exception {
        String appId = createApp("Duplicate Version App");

        publishApp(appId, "0.1.0");

        String publishRequest = """
                { "version": "0.1.0" }
                """;

        mockMvc.perform(post("/api/v1/apps/" + appId + "/publish")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("app.version.already_exists"));
    }

    @Test
    void shouldRollbackToVersion() throws Exception {
        String appId = createApp("Rollback App");

        String versionId = publishApp(appId, "0.1.0");

        mockMvc.perform(post("/api/v1/apps/" + appId + "/versions/" + versionId + "/rollback")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appId))
                .andExpect(jsonPath("$.hasDraft").value(true));
    }

    @Test
    void shouldSuggestNextVersion() throws Exception {
        String appId = createApp("Suggest Version App");

        publishApp(appId, "0.1.0");

        mockMvc.perform(get("/api/v1/apps/" + appId + "/versions/suggest")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedVersion").value("0.1.1"));
    }

    @Test
    void shouldSetHasDraftOnAppUpdate() throws Exception {
        String appId = createApp("Has Draft App");

        // Activate the app via status update
        String statusRequest = """
                { "status": "ACTIVE" }
                """;
        mockMvc.perform(put("/api/v1/apps/" + appId + "/status")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Update the app name
        String updateRequest = """
                { "name": "Has Draft App Updated", "description": "Updated" }
                """;
        mockMvc.perform(put("/api/v1/apps/" + appId)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk());

        // Verify hasDraft is true
        mockMvc.perform(get("/api/v1/apps/" + appId)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDraft").value(true));
    }

    private String createApp(String name) throws Exception {
        String request = """
                { "name": "%s", "description": "Test app" }
                """.formatted(name);

        MvcResult result = mockMvc.perform(post("/api/v1/apps")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private String publishApp(String appId, String version) throws Exception {
        String publishRequest = """
                { "version": "%s" }
                """.formatted(version);

        MvcResult result = mockMvc.perform(post("/api/v1/apps/" + appId + "/publish")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishRequest))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }
}
