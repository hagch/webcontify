package io.webcontify.identity.service;

import io.webcontify.identity.mapper.UserMapper;
import io.webcontify.identity.model.*;
import io.webcontify.identity.repository.TenantRoleRepository;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final TenantUserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final OpenFgaService openFgaService;
    private final PermissionService permissionService;
    private final TenantInfrastructureLookupService lookupService;
    private final UserMapper userMapper;
    private final RoleService roleService;
    private final TenantRoleRepository roleRepository;

    public UserService(
            TenantUserRepository userRepository,
            KeycloakAdminService keycloakAdminService,
            OpenFgaService openFgaService,
            PermissionService permissionService,
            TenantInfrastructureLookupService lookupService,
            UserMapper userMapper,
            RoleService roleService,
            TenantRoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.keycloakAdminService = keycloakAdminService;
        this.openFgaService = openFgaService;
        this.permissionService = permissionService;
        this.lookupService = lookupService;
        this.userMapper = userMapper;
        this.roleService = roleService;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public TenantUser invite(String email, String firstName, String lastName, List<UUID> roleIds, UUID copyRolesFromUserId) {
        // Privilege escalation check: non-admin cannot assign admin role
        var adminRole = roleRepository.findActiveByName("Admin");
        if (adminRole.isPresent() && roleIds.contains(adminRole.get().getId())) {
            var tenantInfoForCheck = TenantContext.getTenantInformation();
            boolean isAdmin = permissionService.check(new CheckPermissionRequest(
                    TenantContext.getCurrentUserId(), "admin", "tenant", tenantInfoForCheck.tenantId()));
            if (!isAdmin) {
                throw new AccessDeniedException("invite.cannot_assign_admin_role");
            }
        }

        // Duplikat-Check
        userRepository.findActiveByEmail(email).ifPresent(existing -> {
            throw new ConflictException("user.email_already_exists");
        });

        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

        // Keycloak User erstellen (gibt Keycloak-UUID zurueck, wird als PK verwendet)
        UUID keycloakUserId;
        try {
            var keycloakResponse = keycloakAdminService.createUser(
                    new CreateKeycloakUserRequest(infrastructure.keycloakRealmName(), email, firstName, lastName));
            keycloakUserId = keycloakResponse.keycloakUserId();
        } catch (Exception e) {
            log.error("Failed to create Keycloak user for email={}", email, e);
            throw e;
        }

        // DB-Insert (bei Fehler: Keycloak-User cleanup)
        TenantUserEntity entity;
        try {
            entity = new TenantUserEntity();
            entity.setId(keycloakUserId);
            entity.setTenantId(tenantInfo.tenantId());
            entity.setEmail(email);
            entity.setFirstName(firstName);
            entity.setLastName(lastName);
            entity.setStatus(UserStatus.INVITED);
            entity.setInvitedBy(TenantContext.getCurrentUserId());
            entity = userRepository.save(entity);
        } catch (Exception e) {
            log.error("DB insert failed for user={}, cleaning up Keycloak user", keycloakUserId, e);
            try {
                keycloakAdminService.deleteUser(
                        new DeleteKeycloakUserRequest(infrastructure.keycloakRealmName(), keycloakUserId));
            } catch (Exception cleanupEx) {
                log.error("Keycloak cleanup failed for user={}", keycloakUserId, cleanupEx);
            }
            throw e;
        }

        // Invite-Email senden
        String inviteSlug = infrastructure.keycloakRealmName().replace("tenant-", "");
        String inviteRedirectUri = "http://" + inviteSlug + ".localhost";
        keycloakAdminService.sendInviteEmail(
                new SendInviteEmailRequest(infrastructure.keycloakRealmName(), keycloakUserId, inviteRedirectUri));

        // Rollen zuweisen (verpflichtend — Rollback bei Fehler)
        try {
            roleService.setRolesForUser(keycloakUserId, roleIds);
        } catch (Exception e) {
            log.error("Role assignment failed for user={}, rolling back", keycloakUserId, e);
            var auditInfo = entity.getAuditInfo();
            if (auditInfo == null) {
                auditInfo = new AuditInfo();
                entity.setAuditInfo(auditInfo);
            }
            auditInfo.setDeletedAt(java.time.OffsetDateTime.now());
            auditInfo.setDeletedBy(TenantContext.getCurrentUserId());
            userRepository.save(entity);
            try {
                keycloakAdminService.deleteUser(
                        new DeleteKeycloakUserRequest(infrastructure.keycloakRealmName(), keycloakUserId));
            } catch (Exception cleanupEx) {
                log.error("Keycloak cleanup failed during role rollback for user={}", keycloakUserId, cleanupEx);
            }
            throw e;
        }

        log.info("User invited: id={}, email={}, tenant={}", keycloakUserId, email, tenantInfo.tenantId());
        return userMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public Page<TenantUser> list(Pageable pageable) {
        return userRepository.findAllActive(pageable).map(userMapper::toDomain);
    }

    @Transactional(readOnly = true)
    public TenantUser getById(UUID userId) {
        return userRepository.findActiveById(userId)
                .map(userMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("user.not_found"));
    }

    @Transactional
    public TenantUser updateStatus(UUID userId, UserStatus newStatus) {
        var entity = userRepository.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException("user.not_found"));

        UserStatus currentStatus = entity.getStatus();

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new BadRequestException("user.invalid_status_transition");
        }

        UUID currentUserId = TenantContext.getCurrentUserId();
        if (newStatus == UserStatus.DEACTIVATED && userId.equals(currentUserId)) {
            throw new BadRequestException("user.cannot_deactivate_self");
        }

        // Last-Admin-Guard bei Deaktivierung
        if (newStatus == UserStatus.DEACTIVATED) {
            guardLastAdmin(userId);
        }

        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

        // Keycloak enable/disable
        if (newStatus == UserStatus.DEACTIVATED) {
            keycloakAdminService.disableUser(
                    new DisableKeycloakUserRequest(infrastructure.keycloakRealmName(), userId));
        } else if (newStatus == UserStatus.ACTIVE) {
            keycloakAdminService.enableUser(
                    new EnableKeycloakUserRequest(infrastructure.keycloakRealmName(), userId));
        }

        entity.setStatus(newStatus);
        entity = userRepository.save(entity);

        log.info("User status updated: id={}, {} -> {}", userId, currentStatus, newStatus);
        return userMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public void resendInvite(UUID userId) {
        var entity = userRepository.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException("user.not_found"));

        if (entity.getStatus() != UserStatus.INVITED) {
            throw new BadRequestException("user.not_invited");
        }

        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

        String resendSlug = infrastructure.keycloakRealmName().replace("tenant-", "");
        String resendRedirectUri = "http://" + resendSlug + ".localhost";
        keycloakAdminService.sendInviteEmail(
                new SendInviteEmailRequest(infrastructure.keycloakRealmName(), userId, resendRedirectUri));

        log.info("Invite resent for user={}", userId);
    }

    @Transactional
    public void delete(UUID userId) {
        UUID currentUserId = TenantContext.getCurrentUserId();
        if (userId.equals(currentUserId)) {
            throw new BadRequestException("user.cannot_delete_self");
        }

        var entity = userRepository.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException("user.not_found"));

        guardLastAdmin(userId);

        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

        // Soft Delete in lokaler DB
        entity.getAuditInfo().setDeletedAt(java.time.OffsetDateTime.now());
        entity.getAuditInfo().setDeletedBy(currentUserId);
        userRepository.save(entity);

        // Hard Delete in Keycloak
        keycloakAdminService.deleteUser(
                new DeleteKeycloakUserRequest(infrastructure.keycloakRealmName(), userId));

        // OpenFGA Cleanup
        permissionService.revoke(new RevokePermissionRequest(
                userId, "admin", "tenant", tenantInfo.tenantId()));

        // Alle Rollen-Zuweisungen entfernen (best-effort)
        try {
            var userRoles = roleService.getRolesForUser(userId);
            if (!userRoles.isEmpty()) {
                roleService.setRolesForUser(userId, List.of());
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup role assignments for user={}", userId, e);
        }

        log.info("User deleted: id={}", userId);
    }

    private void guardLastAdmin(UUID excludeUserId) {
        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

        // Finde Admin-System-Rolle
        var adminRole = roleRepository.findActiveByName("Admin");
        if (adminRole.isEmpty()) return;

        // Pruefe welche User die Admin-Rolle haben
        var adminTuples = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                infrastructure.openfgaStoreId(), "role", adminRole.get().getId().toString(), "assignee"));

        var adminUserIds = adminTuples.stream()
                .map(t -> UUID.fromString(t.user().replace("user:", "")))
                .collect(Collectors.toSet());

        if (adminUserIds.contains(excludeUserId) && adminUserIds.size() <= 1) {
            throw new BadRequestException("user.last_admin_cannot_be_removed");
        }
    }

}
