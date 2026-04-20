package io.webcontify.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.identity.model.OpenFgaBatchCheckResponse;
import io.webcontify.identity.model.RelationshipTuple;
import io.webcontify.identity.repository.TenantRoleRepository;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantRoleEntity;
import io.webcontify.identity.service.KeycloakAdminService;
import io.webcontify.identity.service.OpenFgaService;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.identity.service.TenantInfrastructureLookupService;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.ProvisioningStatus;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class RoleControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean KeycloakAdminService keycloakAdminService;
    @MockitoBean OpenFgaService openFgaService;
    @MockitoBean PermissionService permissionService;
    @MockitoBean FileStorageService fileStorageService;
    @MockitoBean TenantInfrastructureLookupService lookupService;

    @Autowired MockMvc mockMvc;
    @Autowired TenantRoleRepository roleRepository;
    @Autowired TenantUserRepository userRepository;
    @Autowired TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000011");
    static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @BeforeEach
    void setUp() {
        roleRepository.deleteAll();

        if (tenantRepository.findById(TENANT_ID).isEmpty()) {
            var tenant = new TenantEntity();
            tenant.setId(TENANT_ID);
            tenant.setName("Role Test Tenant");
            tenant.setSlug("role-test-tenant");
            tenant.setStatus("ACTIVE");
            tenant.setKeycloakRealmName("role-test-realm");
            tenant.setOpenfgaStoreId("role-test-store");
            var provStatus = ProvisioningStatus.initial("admin@roletest.com", "Admin", "User");
            tenant.setProvisioningStatus(provStatus.toJson());
            var auditInfo = new AuditInfo();
            auditInfo.setCreatedAt(OffsetDateTime.now());
            auditInfo.setCreatedBy(SYSTEM_USER_ID);
            auditInfo.setUpdatedAt(OffsetDateTime.now());
            auditInfo.setUpdatedBy(SYSTEM_USER_ID);
            tenant.setAuditInfo(auditInfo);
            tenantRepository.save(tenant);
        }

        lenient().when(permissionService.check(any())).thenReturn(true);

        var infra = new TenantInfrastructureInfo("role-test-store", "role-test-realm");
        lenient().when(lookupService.getInfrastructure(TENANT_ID)).thenReturn(infra);

        lenient().when(openFgaService.readTuples(any())).thenReturn(List.of());
        lenient().when(openFgaService.check(any())).thenReturn(false);
        lenient().when(openFgaService.batchCheck(any()))
                .thenReturn(new OpenFgaBatchCheckResponse(Map.of()));
        doNothing().when(openFgaService).write(any());
        doNothing().when(openFgaService).delete(any());
    }

    @Test
    void shouldCreateAndListRoles() throws Exception {
        String createBody = """
                {
                    "name": "Editor",
                    "description": "Can edit content"
                }
                """;

        mockMvc.perform(post("/api/v1/tenant/roles")
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Editor"))
                .andExpect(jsonPath("$.systemRole").value(false));

        mockMvc.perform(get("/api/v1/tenant/roles")
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles[0].name").value("Editor"));
    }

    @Test
    void shouldRejectSystemRoleUpdate() throws Exception {
        var systemRole = createSystemRole("Admin");

        String updateBody = """
                {
                    "name": "AdminModified",
                    "description": "Hacked"
                }
                """;

        mockMvc.perform(put("/api/v1/tenant/roles/{roleId}", systemRole.getId())
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("role.system_role_cannot_be_modified"));
    }

    @Test
    void shouldRejectSystemRoleDelete() throws Exception {
        var systemRole = createSystemRole("Admin");

        mockMvc.perform(delete("/api/v1/tenant/roles/{roleId}", systemRole.getId())
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("role.system_role_cannot_be_deleted"));
    }

    @Test
    void shouldRejectDeleteWhenRoleHasAssignees() throws Exception {
        String createBody = """
                {
                    "name": "Viewer",
                    "description": "Read only"
                }
                """;

        var createResult = mockMvc.perform(post("/api/v1/tenant/roles")
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        UUID roleId = UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());

        // Simulate role has assignees in OpenFGA
        var assigneeTuple = new RelationshipTuple("user:" + USER_ID, "assignee", "role:" + roleId);
        when(openFgaService.readTuples(any())).thenReturn(List.of(assigneeTuple));

        mockMvc.perform(delete("/api/v1/tenant/roles/{roleId}", roleId)
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("role.has_assignees"));
    }

    @Test
    void shouldGetRoleDetail() throws Exception {
        String createBody = """
                {
                    "name": "ContentManager",
                    "description": "Manages content"
                }
                """;

        var createResult = mockMvc.perform(post("/api/v1/tenant/roles")
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        UUID roleId = UUID.fromString(
                objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(get("/api/v1/tenant/roles/{roleId}", roleId)
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roleId.toString()))
                .andExpect(jsonPath("$.name").value("ContentManager"))
                .andExpect(jsonPath("$.assignees").isArray())
                .andExpect(jsonPath("$.permissions").isArray());
    }

    // --- Helpers ---

    private TenantRoleEntity createSystemRole(String name) {
        var entity = new TenantRoleEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(TENANT_ID);
        entity.setName(name);
        entity.setDescription("System role");
        entity.setSystemRole(true);
        var auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(SYSTEM_USER_ID);
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(SYSTEM_USER_ID);
        entity.setAuditInfo(auditInfo);
        return roleRepository.save(entity);
    }
}
