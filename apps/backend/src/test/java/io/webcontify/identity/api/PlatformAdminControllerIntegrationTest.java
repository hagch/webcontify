package io.webcontify.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.IntegrationTestBase;
import io.webcontify.identity.model.CreateKeycloakUserResponse;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

@AutoConfigureMockMvc
class PlatformAdminControllerIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    static final UUID CURRENT_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID OTHER_ADMIN_ID   = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private UserRepresentation buildUser(UUID id, String email, boolean enabled) {
        var user = new UserRepresentation();
        user.setId(id.toString());
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("Admin");
        user.setEnabled(enabled);
        user.setCreatedTimestamp(1700000000000L);
        return user;
    }

    private RoleRepresentation buildPlatformAdminRole() {
        var role = new RoleRepresentation();
        role.setName("PLATFORM_ADMIN");
        return role;
    }

    @BeforeEach
    void setUp() {
        lenient().when(permissionService.check(any())).thenReturn(true);
    }

    @Test
    void shouldInviteAndListAdmins() throws Exception {
        var newAdminId = UUID.fromString("00000000-0000-0000-0000-000000000030");
        when(keycloakAdminService.createUser(any()))
                .thenReturn(new CreateKeycloakUserResponse(newAdminId));
        doNothing().when(keycloakAdminService).assignRealmRole(any(), any(), any());
        doNothing().when(keycloakAdminService).sendInviteEmail(any());

        var newUser = buildUser(newAdminId, "newadmin@test.com", true);
        when(keycloakAdminService.getUserById(any(), eq(newAdminId))).thenReturn(newUser);
        when(keycloakAdminService.getUserRealmRoles(any(), eq(newAdminId)))
                .thenReturn(List.of(buildPlatformAdminRole()));

        when(keycloakAdminService.listUsersWithRole(any(), any()))
                .thenReturn(List.of(newUser));

        String inviteBody = """
                {
                    "email": "newadmin@test.com",
                    "firstName": "Test",
                    "lastName": "Admin"
                }
                """;

        // POST /api/v1/admin/admins — kein Tenant-Header = Platform Admin Kontext
        mockMvc.perform(post("/api/v1/admin/admins")
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, CURRENT_ADMIN_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newadmin@test.com"))
                .andExpect(jsonPath("$.enabled").value(true));

        // GET /api/v1/admin/admins — kein Tenant-Header = Platform Admin Kontext
        mockMvc.perform(get("/api/v1/admin/admins")
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, CURRENT_ADMIN_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admins").isArray())
                .andExpect(jsonPath("$.admins[0].email").value("newadmin@test.com"));
    }

    @Test
    void shouldGetAdminById() throws Exception {
        var user = buildUser(OTHER_ADMIN_ID, "other@test.com", true);
        when(keycloakAdminService.getUserById(any(), eq(OTHER_ADMIN_ID))).thenReturn(user);
        when(keycloakAdminService.getUserRealmRoles(any(), eq(OTHER_ADMIN_ID)))
                .thenReturn(List.of(buildPlatformAdminRole()));

        mockMvc.perform(get("/api/v1/admin/admins/{adminId}", OTHER_ADMIN_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, CURRENT_ADMIN_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(OTHER_ADMIN_ID.toString()))
                .andExpect(jsonPath("$.email").value("other@test.com"));
    }

    @Test
    void shouldRejectSelfDeactivation() throws Exception {
        String statusBody = """
                {
                    "enabled": false
                }
                """;

        // Eigene ID deaktivieren → 400
        mockMvc.perform(put("/api/v1/admin/admins/{adminId}/status", CURRENT_ADMIN_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, CURRENT_ADMIN_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("admin.cannot_modify_self"));
    }

    @Test
    void shouldRejectDeletingLastAdmin() throws Exception {
        // Nur 1 aktiver Admin vorhanden
        var singleAdmin = buildUser(OTHER_ADMIN_ID, "only@test.com", true);
        when(keycloakAdminService.listUsersWithRole(any(), any()))
                .thenReturn(List.of(singleAdmin));

        mockMvc.perform(delete("/api/v1/admin/admins/{adminId}", OTHER_ADMIN_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, CURRENT_ADMIN_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("admin.last_admin_cannot_be_deleted"));
    }

    @Test
    void shouldResetAdminPassword() throws Exception {
        doNothing().when(keycloakAdminService).addRequiredAction(any(), eq(OTHER_ADMIN_ID), any());

        mockMvc.perform(post("/api/v1/admin/admins/{adminId}/reset-password", OTHER_ADMIN_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, CURRENT_ADMIN_ID.toString()))
                .andExpect(status().isNoContent());

        verify(keycloakAdminService).addRequiredAction(any(), eq(OTHER_ADMIN_ID), eq("UPDATE_PASSWORD"));
    }
}
