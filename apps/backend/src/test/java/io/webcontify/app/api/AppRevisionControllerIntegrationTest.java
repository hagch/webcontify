package io.webcontify.app.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AppRevisionControllerIntegrationTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final UUID TEST_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000299");
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
            tenant.setName("Revision Test Tenant");
            tenant.setSlug("revision-test-tenant");
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
    void shouldListRevisionsAfterUpdate() throws Exception {
        String appId = createApp("List Rev App");

        updateApp(appId, "List Rev App Updated", "Updated description");

        mockMvc.perform(get("/api/v1/apps/" + appId + "/revisions")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].changeType").value("updated"))
                .andExpect(jsonPath("$.items[0].patch").isNotEmpty());
    }

    @Test
    void shouldGetRevisionDetail() throws Exception {
        String appId = createApp("Detail Rev App");

        updateApp(appId, "Detail Rev App Updated", "Updated");

        MvcResult listResult = mockMvc.perform(get("/api/v1/apps/" + appId + "/revisions")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andReturn();

        String revisionId = objectMapper
                .readTree(listResult.getResponse().getContentAsString())
                .get("items")
                .get(0)
                .get("id")
                .asText();

        mockMvc.perform(get("/api/v1/apps/" + appId + "/revisions/" + revisionId)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(revisionId))
                .andExpect(jsonPath("$.changeType").value("updated"))
                .andExpect(jsonPath("$.patch").isNotEmpty())
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.source").isNotEmpty());
    }

    @Test
    void shouldRevertRevision() throws Exception {
        String appId = createApp("Revert Rev App");

        updateApp(appId, "New Name", "New description");

        MvcResult listResult = mockMvc.perform(get("/api/v1/apps/" + appId + "/revisions")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andReturn();

        String revisionId = objectMapper
                .readTree(listResult.getResponse().getContentAsString())
                .get("items")
                .get(0)
                .get("id")
                .asText();

        mockMvc.perform(post("/api/v1/apps/" + appId + "/revisions/" + revisionId + "/revert")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Revert Rev App"));

        mockMvc.perform(get("/api/v1/apps/" + appId)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Revert Rev App"));
    }

    @Test
    void shouldReturnEmptyListForNewApp() throws Exception {
        String appId = createApp("No Rev App");

        mockMvc.perform(get("/api/v1/apps/" + appId + "/revisions")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void shouldClearRevisionsAfterPublish() throws Exception {
        String appId = createApp("Publish Clear App");

        updateApp(appId, "Publish Clear App Updated", "Updated");

        // Verify there is at least one revision
        mockMvc.perform(get("/api/v1/apps/" + appId + "/revisions")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        // Publish the app
        String publishRequest = """
                { "version": "0.1.0" }
                """;
        mockMvc.perform(post("/api/v1/apps/" + appId + "/publish")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishRequest))
                .andExpect(status().isCreated());

        // After publish, draft revisions should be empty
        mockMvc.perform(get("/api/v1/apps/" + appId + "/revisions")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
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

    private void updateApp(String appId, String name, String description) throws Exception {
        String updateRequest = """
                { "name": "%s", "description": "%s" }
                """.formatted(name, description);

        mockMvc.perform(put("/api/v1/apps/" + appId)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk());
    }
}
