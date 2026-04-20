package io.webcontify.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.identity.model.CreateKeycloakUserResponse;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.service.KeycloakAdminService;
import io.webcontify.identity.service.OpenFgaService;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.identity.service.RoleService;
import io.webcontify.identity.service.TenantInfrastructureLookupService;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.ProvisioningStatus;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class UserControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean KeycloakAdminService keycloakAdminService;
    @MockitoBean OpenFgaService openFgaService;
    @MockitoBean PermissionService permissionService;
    @MockitoBean RoleService roleService;
    @MockitoBean FileStorageService fileStorageService;
    @MockitoBean TenantInfrastructureLookupService lookupService;

    @Autowired MockMvc mockMvc;
    @Autowired TenantUserRepository userRepository;
    @Autowired TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Create tenant if not exists (FK constraint on tenant_user.tenant_id)
        if (tenantRepository.findById(TENANT_ID).isEmpty()) {
            var tenant = new TenantEntity();
            tenant.setId(TENANT_ID);
            tenant.setName("Test Tenant");
            tenant.setSlug("test-tenant");
            tenant.setStatus("ACTIVE");
            tenant.setKeycloakRealmName("test-realm");
            tenant.setOpenfgaStoreId("test-store");
            var provStatus = ProvisioningStatus.initial("admin@test.com", "Admin", "User");
            tenant.setProvisioningStatus(provStatus.toJson());
            var auditInfo = new AuditInfo();
            auditInfo.setCreatedAt(OffsetDateTime.now());
            auditInfo.setCreatedBy(SYSTEM_USER_ID);
            auditInfo.setUpdatedAt(OffsetDateTime.now());
            auditInfo.setUpdatedBy(SYSTEM_USER_ID);
            tenant.setAuditInfo(auditInfo);
            tenantRepository.save(tenant);
        }

        // PermissionCheckAspect calls permissionService.check() for non-platform-admin users
        lenient().when(permissionService.check(any())).thenReturn(true);

        var infra = new TenantInfrastructureInfo("test-store", "test-realm");
        lenient().when(lookupService.getInfrastructure(TENANT_ID)).thenReturn(infra);

        when(keycloakAdminService.createUser(any()))
                .thenReturn(new CreateKeycloakUserResponse(UUID.randomUUID()));
    }

    @Test
    void shouldInviteAndListUsers() throws Exception {
        String inviteBody = """
                {
                    "email": "invited@test.com",
                    "firstName": "Max",
                    "lastName": "Muster",
                    "roleIds": ["00000000-0000-0000-0000-000000000010"]
                }
                """;

        mockMvc.perform(post("/api/v1/tenant/users")
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("invited@test.com"))
                .andExpect(jsonPath("$.status").value("INVITED"));

        mockMvc.perform(get("/api/v1/tenant/users")
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
