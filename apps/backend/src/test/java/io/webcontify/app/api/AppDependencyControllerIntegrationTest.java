package io.webcontify.app.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
class AppDependencyControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean FileStorageService fileStorageService;
    @MockitoBean PermissionService permissionService;

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
            tenant.setName("Dependency Test Tenant");
            tenant.setSlug("dependency-test-tenant");
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
    void shouldAddDependency() throws Exception {
        String appAId = createAndActivateApp("Dep Add App A");
        String appBId = createAndActivateApp("Dep Add App B");

        String depRequest = """
                { "dependsOnAppId": "%s" }
                """.formatted(appBId);

        mockMvc.perform(post("/api/v1/apps/" + appAId + "/dependencies")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depRequest))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.appId").value(appAId))
                .andExpect(jsonPath("$.dependsOnAppId").value(appBId))
                .andExpect(jsonPath("$.direct").value(true));
    }

    @Test
    void shouldRejectCyclicDependency() throws Exception {
        String appAId = createAndActivateApp("Cycle App A");
        String appBId = createAndActivateApp("Cycle App B");

        String depAtoB = """
                { "dependsOnAppId": "%s" }
                """.formatted(appBId);

        mockMvc.perform(post("/api/v1/apps/" + appAId + "/dependencies")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depAtoB))
                .andExpect(status().isCreated());

        String depBtoA = """
                { "dependsOnAppId": "%s" }
                """.formatted(appAId);

        mockMvc.perform(post("/api/v1/apps/" + appBId + "/dependencies")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depBtoA))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRemoveDependency() throws Exception {
        String appAId = createAndActivateApp("Remove Dep App A");
        String appBId = createAndActivateApp("Remove Dep App B");

        String depRequest = """
                { "dependsOnAppId": "%s" }
                """.formatted(appBId);

        mockMvc.perform(post("/api/v1/apps/" + appAId + "/dependencies")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/apps/" + appAId + "/dependencies/" + appBId)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldListDependencies() throws Exception {
        String appAId = createAndActivateApp("List Dep App A");
        String appBId = createAndActivateApp("List Dep App B");
        String appCId = createAndActivateApp("List Dep App C");

        // A depends on B
        mockMvc.perform(post("/api/v1/apps/" + appAId + "/dependencies")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dependsOnAppId": "%s" }
                                """.formatted(appBId)))
                .andExpect(status().isCreated());

        // B depends on C
        mockMvc.perform(post("/api/v1/apps/" + appBId + "/dependencies")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dependsOnAppId": "%s" }
                                """.formatted(appCId)))
                .andExpect(status().isCreated());

        // List from A should include B (direct) and C (transitive)
        mockMvc.perform(get("/api/v1/apps/" + appAId + "/dependencies")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void shouldGetDependencyGraph() throws Exception {
        String appAId = createAndActivateApp("Graph App A");
        String appBId = createAndActivateApp("Graph App B");

        mockMvc.perform(post("/api/v1/apps/" + appAId + "/dependencies")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dependsOnAppId": "%s" }
                                """.formatted(appBId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/apps/" + appAId + "/dependencies/graph")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.edges.length()").value(1))
                .andExpect(jsonPath("$.edges[0].source").value(appAId))
                .andExpect(jsonPath("$.edges[0].target").value(appBId));
    }

    @Test
    void shouldBlockDeleteWhenDependantsExist() throws Exception {
        String appAId = createAndActivateApp("Block Del App A");
        String appBId = createAndActivateApp("Block Del App B");

        mockMvc.perform(post("/api/v1/apps/" + appAId + "/dependencies")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dependsOnAppId": "%s" }
                                """.formatted(appBId)))
                .andExpect(status().isCreated());

        // Try to delete B which has A depending on it
        mockMvc.perform(delete("/api/v1/apps/" + appBId)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("app.delete.has_dependants"));
    }

    @Test
    void shouldListProblemsForInactiveDependency() throws Exception {
        String appAId = createAndActivateApp("Problems App A");
        String appBId = createAndActivateApp("Problems App B");

        // Add dependency A -> B
        mockMvc.perform(post("/api/v1/apps/" + appAId + "/dependencies")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "dependsOnAppId": "%s" }
                                """.formatted(appBId)))
                .andExpect(status().isCreated());

        // Deactivate B
        mockMvc.perform(put("/api/v1/apps/" + appBId + "/status")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "INACTIVE" }
                                """))
                .andExpect(status().isOk());

        // Get problems for A
        mockMvc.perform(get("/api/v1/apps/" + appAId + "/problems")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCount").value(1))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].severity").value("ERROR"));
    }

    private String createAndActivateApp(String name) throws Exception {
        String createRequest = """
                { "name": "%s" }
                """.formatted(name);

        MvcResult createResult = mockMvc.perform(post("/api/v1/apps")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(put("/api/v1/apps/" + id + "/status")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "ACTIVE" }
                                """))
                .andExpect(status().isOk());

        return id;
    }
}
