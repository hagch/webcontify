package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PlatformAdminService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminService.class);
    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    private final KeycloakAdminService keycloakAdminService;
    private final String platformRealmName;

    public PlatformAdminService(
            KeycloakAdminService keycloakAdminService,
            @Value("${keycloak.platform-realm:webcontify-platform}") String platformRealmName) {
        this.keycloakAdminService = keycloakAdminService;
        this.platformRealmName = platformRealmName;
    }

    public List<UserRepresentation> list() {
        return keycloakAdminService.listUsersWithRole(platformRealmName, PLATFORM_ADMIN_ROLE);
    }

    public UUID invite(String email, String firstName, String lastName) {
        UUID userId;
        try {
            var response = keycloakAdminService.createUser(
                    new CreateKeycloakUserRequest(platformRealmName, email, firstName, lastName));
            userId = response.keycloakUserId();
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("409")) {
                throw new ConflictException("admin.email_already_exists");
            }
            throw e;
        }

        keycloakAdminService.assignRealmRole(platformRealmName, userId, PLATFORM_ADMIN_ROLE);
        keycloakAdminService.sendInviteEmail(new SendInviteEmailRequest(platformRealmName, userId, "http://platform.localhost"));

        log.info("Platform admin invited: id={}, email={}", userId, email);
        return userId;
    }

    public UserRepresentation getById(UUID adminId) {
        UserRepresentation user;
        try {
            user = keycloakAdminService.getUserById(platformRealmName, adminId);
        } catch (Exception e) {
            throw new NotFoundException("admin.not_found");
        }

        if (user == null) {
            throw new NotFoundException("admin.not_found");
        }

        var roles = keycloakAdminService.getUserRealmRoles(platformRealmName, adminId);
        boolean hasPlatformAdminRole = roles.stream()
                .anyMatch(role -> PLATFORM_ADMIN_ROLE.equals(role.getName()));

        if (!hasPlatformAdminRole) {
            throw new NotFoundException("admin.not_found");
        }

        return user;
    }

    public void updateStatus(UUID adminId, boolean enabled) {
        if (TenantContext.getCurrentUserId().equals(adminId)) {
            throw new BadRequestException("admin.cannot_modify_self");
        }

        if (!enabled) {
            var enabledAdmins = keycloakAdminService.listUsersWithRole(platformRealmName, PLATFORM_ADMIN_ROLE)
                    .stream()
                    .filter(UserRepresentation::isEnabled)
                    .toList();
            if (enabledAdmins.size() <= 1) {
                throw new ConflictException("admin.last_admin_cannot_be_deactivated");
            }
            keycloakAdminService.disableUser(new DisableKeycloakUserRequest(platformRealmName, adminId));
        } else {
            keycloakAdminService.enableUser(new EnableKeycloakUserRequest(platformRealmName, adminId));
        }

        log.info("Platform admin status updated: id={}, enabled={}", adminId, enabled);
    }

    public void delete(UUID adminId) {
        if (TenantContext.getCurrentUserId().equals(adminId)) {
            throw new BadRequestException("admin.cannot_delete_self");
        }

        var allAdmins = keycloakAdminService.listUsersWithRole(platformRealmName, PLATFORM_ADMIN_ROLE);
        var adminToDelete = allAdmins.stream()
                .filter(u -> adminId.toString().equals(u.getId()))
                .findFirst()
                .orElse(null);

        // Only enforce the last-admin guard when deleting an enabled admin
        boolean isDeletingEnabledAdmin = adminToDelete == null || adminToDelete.isEnabled();
        if (isDeletingEnabledAdmin) {
            var enabledAdmins = allAdmins.stream()
                    .filter(UserRepresentation::isEnabled)
                    .toList();
            if (enabledAdmins.size() <= 1) {
                throw new ConflictException("admin.last_admin_cannot_be_deleted");
            }
        }

        keycloakAdminService.deleteUser(new DeleteKeycloakUserRequest(platformRealmName, adminId));
        log.info("Platform admin deleted: id={}", adminId);
    }

    public void resetPassword(UUID adminId) {
        if (TenantContext.getCurrentUserId().equals(adminId)) {
            throw new BadRequestException("admin.use_account_console_for_own_password");
        }

        keycloakAdminService.addRequiredAction(platformRealmName, adminId, "UPDATE_PASSWORD");
        log.info("Password reset requested for platform admin: id={}", adminId);
    }

}
