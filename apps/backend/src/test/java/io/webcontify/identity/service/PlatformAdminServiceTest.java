package io.webcontify.identity.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.webcontify.identity.model.*;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PlatformAdminServiceTest {

    @Mock
    KeycloakAdminService keycloakAdminService;

    PlatformAdminService platformAdminService;

    static final UUID CURRENT_USER_ID = UUID.randomUUID();
    static final UUID TARGET_ADMIN_ID = UUID.randomUUID();
    static final String PLATFORM_REALM = "webcontify-platform";

    @BeforeEach
    void setUp() {
        platformAdminService = new PlatformAdminService(keycloakAdminService, PLATFORM_REALM);

        var tenantInfo = new TenantInformation(null, true);
        var auth = new TenantAwareAuthentication(tenantInfo, CURRENT_USER_ID.toString(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private UserRepresentation createUserRepresentation(UUID id, boolean enabled) {
        var user = new UserRepresentation();
        user.setId(id.toString());
        user.setEmail("admin@test.com");
        user.setFirstName("Platform");
        user.setLastName("Admin");
        user.setEnabled(enabled);
        return user;
    }

    private RoleRepresentation createPlatformAdminRole() {
        var role = new RoleRepresentation();
        role.setName("PLATFORM_ADMIN");
        return role;
    }

    @Nested
    class List_ {
        @Test
        void shouldListAllPlatformAdmins() {
            var admin1 = createUserRepresentation(UUID.randomUUID(), true);
            var admin2 = createUserRepresentation(UUID.randomUUID(), false);
            when(keycloakAdminService.listUsersWithRole(PLATFORM_REALM, "PLATFORM_ADMIN"))
                    .thenReturn(List.of(admin1, admin2));

            var result = platformAdminService.list();

            assertThat(result).hasSize(2);
            verify(keycloakAdminService).listUsersWithRole(PLATFORM_REALM, "PLATFORM_ADMIN");
        }
    }

    @Nested
    class Invite {
        @Test
        void shouldInvitePlatformAdmin() {
            var newAdminId = UUID.randomUUID();
            when(keycloakAdminService.createUser(any()))
                    .thenReturn(new CreateKeycloakUserResponse(newAdminId));

            var result = platformAdminService.invite("new-admin@test.com", "New", "Admin");

            assertThat(result).isEqualTo(newAdminId);
            verify(keycloakAdminService).createUser(argThat(req ->
                    req.realmName().equals(PLATFORM_REALM) &&
                    req.email().equals("new-admin@test.com")));
            verify(keycloakAdminService).assignRealmRole(PLATFORM_REALM, newAdminId, "PLATFORM_ADMIN");
            verify(keycloakAdminService).sendInviteEmail(argThat(req ->
                    req.realmName().equals(PLATFORM_REALM) &&
                    req.userId().equals(newAdminId) &&
                    req.redirectUri().equals("http://platform.localhost")));
        }

        @Test
        void shouldThrowConflictWhenEmailExists() {
            when(keycloakAdminService.createUser(any()))
                    .thenThrow(new RuntimeException("Failed to create Keycloak user (status=409): Conflict"));

            assertThatThrownBy(() -> platformAdminService.invite("existing@test.com", "Existing", "Admin"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("admin.email_already_exists");

            verify(keycloakAdminService, never()).assignRealmRole(any(), any(), any());
            verify(keycloakAdminService, never()).sendInviteEmail(any());
        }
    }

    @Nested
    class GetById {
        @Test
        void shouldGetAdminById() {
            var user = createUserRepresentation(TARGET_ADMIN_ID, true);
            var adminRole = createPlatformAdminRole();

            when(keycloakAdminService.getUserById(PLATFORM_REALM, TARGET_ADMIN_ID)).thenReturn(user);
            when(keycloakAdminService.getUserRealmRoles(PLATFORM_REALM, TARGET_ADMIN_ID))
                    .thenReturn(List.of(adminRole));

            var result = platformAdminService.getById(TARGET_ADMIN_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TARGET_ADMIN_ID.toString());
        }

        @Test
        void shouldThrowNotFoundWhenUserHasNoPlatformAdminRole() {
            var user = createUserRepresentation(TARGET_ADMIN_ID, true);
            var otherRole = new RoleRepresentation();
            otherRole.setName("OTHER_ROLE");

            when(keycloakAdminService.getUserById(PLATFORM_REALM, TARGET_ADMIN_ID)).thenReturn(user);
            when(keycloakAdminService.getUserRealmRoles(PLATFORM_REALM, TARGET_ADMIN_ID))
                    .thenReturn(List.of(otherRole));

            assertThatThrownBy(() -> platformAdminService.getById(TARGET_ADMIN_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("admin.not_found");
        }
    }

    @Nested
    class UpdateStatus {
        @Test
        void shouldDeactivateAdmin() {
            var currentAdmin = createUserRepresentation(CURRENT_USER_ID, true);
            currentAdmin.setEnabled(true);
            var targetAdmin = createUserRepresentation(TARGET_ADMIN_ID, true);
            targetAdmin.setEnabled(true);

            when(keycloakAdminService.listUsersWithRole(PLATFORM_REALM, "PLATFORM_ADMIN"))
                    .thenReturn(List.of(currentAdmin, targetAdmin));

            platformAdminService.updateStatus(TARGET_ADMIN_ID, false);

            verify(keycloakAdminService).disableUser(argThat(req ->
                    req.realmName().equals(PLATFORM_REALM) &&
                    req.userId().equals(TARGET_ADMIN_ID)));
        }

        @Test
        void shouldThrowWhenDeactivatingSelf() {
            assertThatThrownBy(() -> platformAdminService.updateStatus(CURRENT_USER_ID, false))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("admin.cannot_modify_self");
        }

        @Test
        void shouldThrowWhenDeactivatingLastAdmin() {
            var targetAdmin = createUserRepresentation(TARGET_ADMIN_ID, true);

            when(keycloakAdminService.listUsersWithRole(PLATFORM_REALM, "PLATFORM_ADMIN"))
                    .thenReturn(List.of(targetAdmin));

            assertThatThrownBy(() -> platformAdminService.updateStatus(TARGET_ADMIN_ID, false))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("admin.last_admin_cannot_be_deactivated");
        }
    }

    @Nested
    class Delete {
        @Test
        void shouldDeleteAdmin() {
            var currentAdmin = createUserRepresentation(CURRENT_USER_ID, true);
            var targetAdmin = createUserRepresentation(TARGET_ADMIN_ID, true);

            when(keycloakAdminService.listUsersWithRole(PLATFORM_REALM, "PLATFORM_ADMIN"))
                    .thenReturn(List.of(currentAdmin, targetAdmin));

            platformAdminService.delete(TARGET_ADMIN_ID);

            verify(keycloakAdminService).deleteUser(argThat(req ->
                    req.realmName().equals(PLATFORM_REALM) &&
                    req.userId().equals(TARGET_ADMIN_ID)));
        }

        @Test
        void shouldThrowWhenDeletingSelf() {
            assertThatThrownBy(() -> platformAdminService.delete(CURRENT_USER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("admin.cannot_delete_self");
        }

        @Test
        void shouldThrowWhenDeletingLastAdmin() {
            var targetAdmin = createUserRepresentation(TARGET_ADMIN_ID, true);

            when(keycloakAdminService.listUsersWithRole(PLATFORM_REALM, "PLATFORM_ADMIN"))
                    .thenReturn(List.of(targetAdmin));

            assertThatThrownBy(() -> platformAdminService.delete(TARGET_ADMIN_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("admin.last_admin_cannot_be_deleted");
        }
    }

    @Nested
    class ResetPassword {
        @Test
        void shouldResetPassword() {
            platformAdminService.resetPassword(TARGET_ADMIN_ID);

            verify(keycloakAdminService).addRequiredAction(PLATFORM_REALM, TARGET_ADMIN_ID, "UPDATE_PASSWORD");
        }

        @Test
        void shouldThrowWhenResettingOwnPassword() {
            assertThatThrownBy(() -> platformAdminService.resetPassword(CURRENT_USER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("admin.use_account_console_for_own_password");
        }
    }
}
