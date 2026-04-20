package io.webcontify.identity.service;

import com.fasterxml.uuid.Generators;
import io.webcontify.identity.mapper.RoleMapper;
import io.webcontify.identity.model.*;
import io.webcontify.identity.repository.TenantRoleRepository;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantRoleEntity;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    static final String ADMIN_ROLE_NAME = "Admin";

    private static final Map<String, List<String>> RESOURCE_ACTIONS = Map.of(
            "user_management", List.of("inviter", "editor", "deleter", "reader"),
            "role_management", List.of("creator", "editor", "deleter", "reader"),
            "tenant_config", List.of("editor", "reader")
    );

    private final TenantRoleRepository roleRepository;
    private final TenantUserRepository userRepository;
    private final OpenFgaService openFgaService;
    private final TenantInfrastructureLookupService lookupService;
    private final RoleMapper roleMapper;

    public RoleService(
            TenantRoleRepository roleRepository,
            TenantUserRepository userRepository,
            OpenFgaService openFgaService,
            TenantInfrastructureLookupService lookupService,
            RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.openFgaService = openFgaService;
        this.lookupService = lookupService;
        this.roleMapper = roleMapper;
    }

    @Transactional
    public TenantRole create(String name, String description) {
        roleRepository.findActiveByName(name).ifPresent(existing -> {
            throw new ConflictException("role.name_already_exists");
        });

        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());
        UUID currentUserId = resolveCurrentUserId();

        var entity = new TenantRoleEntity();
        entity.setId(Generators.timeBasedEpochGenerator().generate());
        entity.setTenantId(tenantInfo.tenantId());
        entity.setName(name);
        entity.setDescription(description);
        entity.setSystemRole(false);
        var auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(currentUserId);
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(currentUserId);
        entity.setAuditInfo(auditInfo);

        entity = roleRepository.save(entity);

        var tenantTuple = new RelationshipTuple(
                "tenant:" + tenantInfo.tenantId(),
                "tenant",
                "role:" + entity.getId()
        );
        openFgaService.write(new OpenFgaWriteRequest(infrastructure.openfgaStoreId(), List.of(tenantTuple)));

        log.info("Role created: id={}, name={}, tenant={}", entity.getId(), name, tenantInfo.tenantId());
        return roleMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public List<TenantRole> list() {
        return roleRepository.findAllActive().stream()
                .map(roleMapper::toDomain)
                .toList();
    }

    public int getAssigneeCount(UUID roleId) {
        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());
        var tuples = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                infrastructure.openfgaStoreId(), "role", roleId.toString(), "assignee"));
        return tuples.size();
    }

    @Transactional(readOnly = true)
    public TenantRole getById(UUID roleId) {
        return roleRepository.findActiveById(roleId)
                .map(roleMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("role.not_found"));
    }

    public List<RoleAssignee> getAssignees(UUID roleId) {
        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

        var tuples = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                infrastructure.openfgaStoreId(), "role", roleId.toString(), "assignee"));

        var userIds = tuples.stream()
                .map(t -> UUID.fromString(t.user().replace("user:", "")))
                .toList();

        if (userIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findAllById(userIds).stream()
                .map(u -> new RoleAssignee(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName()))
                .toList();
    }

    public List<RolePermissionPreview> getPermissionPreview(UUID roleId) {
        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());
        String userSubject = "role:" + roleId + "#assignee";

        return RESOURCE_ACTIONS.entrySet().stream()
                .map(entry -> {
                    String resourceType = entry.getKey();
                    List<String> grantedActions = entry.getValue().stream()
                            .filter(action -> openFgaService.check(new OpenFgaCheckRequest(
                                    infrastructure.openfgaStoreId(),
                                    null,
                                    action,
                                    resourceType,
                                    tenantInfo.tenantId(),
                                    userSubject)))
                            .toList();
                    return new RolePermissionPreview(resourceType, grantedActions);
                })
                .toList();
    }

    @Transactional
    public TenantRole update(UUID roleId, String name, String description) {
        var entity = roleRepository.findActiveById(roleId)
                .orElseThrow(() -> new NotFoundException("role.not_found"));

        if (entity.isSystemRole()) {
            throw new BadRequestException("role.system_role_cannot_be_modified");
        }

        // Name-Uniqueness-Check: nur pruefen wenn Name sich aendert
        if (!entity.getName().equals(name)) {
            roleRepository.findActiveByName(name).ifPresent(existing -> {
                if (!existing.getId().equals(roleId)) {
                    throw new ConflictException("role.name_already_exists");
                }
            });
        }

        UUID currentUserId = resolveCurrentUserId();
        entity.setName(name);
        entity.setDescription(description);
        entity.getAuditInfo().setUpdatedAt(OffsetDateTime.now());
        entity.getAuditInfo().setUpdatedBy(currentUserId);

        entity = roleRepository.save(entity);
        log.info("Role updated: id={}, name={}", roleId, name);
        return roleMapper.toDomain(entity);
    }

    @Transactional
    public void delete(UUID roleId) {
        var entity = roleRepository.findActiveById(roleId)
                .orElseThrow(() -> new NotFoundException("role.not_found"));

        if (entity.isSystemRole()) {
            throw new BadRequestException("role.system_role_cannot_be_deleted");
        }

        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

        var assigneeTuples = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                infrastructure.openfgaStoreId(), "role", roleId.toString(), "assignee"));

        if (!assigneeTuples.isEmpty()) {
            throw new ConflictException("role.has_assignees");
        }

        UUID currentUserId = resolveCurrentUserId();
        entity.getAuditInfo().setDeletedAt(OffsetDateTime.now());
        entity.getAuditInfo().setDeletedBy(currentUserId);
        roleRepository.save(entity);

        // OpenFGA Cleanup: best-effort
        try {
            var tenantTuple = new RelationshipTuple(
                    "tenant:" + tenantInfo.tenantId(),
                    "tenant",
                    "role:" + roleId
            );
            openFgaService.delete(new OpenFgaDeleteRequest(infrastructure.openfgaStoreId(), List.of(tenantTuple)));
        } catch (Exception e) {
            log.warn("OpenFGA cleanup failed for role={}, continuing", roleId, e);
        }

        log.info("Role deleted: id={}", roleId);
    }

    @Transactional
    public void setAssignees(UUID roleId, List<UUID> userIds) {
        var entity = roleRepository.findActiveById(roleId)
                .orElseThrow(() -> new NotFoundException("role.not_found"));

        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

        // Validate alle userIds existieren in der DB
        if (!userIds.isEmpty()) {
            var foundUsers = userRepository.findAllById(userIds);
            if (foundUsers.size() != userIds.size()) {
                throw new BadRequestException("role.assignee_not_found");
            }
        }

        // Aktuelle Assignees aus OpenFGA lesen
        var currentTuples = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                infrastructure.openfgaStoreId(), "role", roleId.toString(), "assignee"));

        var currentUserIds = currentTuples.stream()
                .map(t -> UUID.fromString(t.user().replace("user:", "")))
                .collect(Collectors.toSet());

        var targetUserIds = new HashSet<>(userIds);

        // Last-Admin-Guard fuer Admin-Systemrolle
        if (entity.isSystemRole() && ADMIN_ROLE_NAME.equals(entity.getName())) {
            long remainingAdminCount = targetUserIds.size();
            if (remainingAdminCount < 1) {
                throw new BadRequestException("role.last_admin_cannot_be_removed");
            }
        }

        // Delta berechnen
        Set<UUID> toAdd = new HashSet<>(targetUserIds);
        toAdd.removeAll(currentUserIds);

        Set<UUID> toRemove = new HashSet<>(currentUserIds);
        toRemove.removeAll(targetUserIds);

        // OpenFGA schreiben
        if (!toAdd.isEmpty()) {
            var addTuples = toAdd.stream()
                    .map(uid -> new RelationshipTuple("user:" + uid, "assignee", "role:" + roleId))
                    .toList();
            openFgaService.write(new OpenFgaWriteRequest(infrastructure.openfgaStoreId(), addTuples));
        }

        if (!toRemove.isEmpty()) {
            var removeTuples = toRemove.stream()
                    .map(uid -> new RelationshipTuple("user:" + uid, "assignee", "role:" + roleId))
                    .toList();
            openFgaService.delete(new OpenFgaDeleteRequest(infrastructure.openfgaStoreId(), removeTuples));
        }

        // Compensating check: nach Remove sicherstellen Admin-Rolle noch Assignees hat
        if (entity.isSystemRole() && ADMIN_ROLE_NAME.equals(entity.getName()) && !toRemove.isEmpty()) {
            var remaining = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                    infrastructure.openfgaStoreId(), "role", roleId.toString(), "assignee"));
            if (remaining.isEmpty()) {
                // Rollback: entfernte Assignees wieder hinzufuegen
                var rollbackTuples = toRemove.stream()
                        .map(uid -> new RelationshipTuple("user:" + uid, "assignee", "role:" + roleId))
                        .toList();
                openFgaService.write(new OpenFgaWriteRequest(infrastructure.openfgaStoreId(), rollbackTuples));
                throw new BadRequestException("role.last_admin_cannot_be_removed");
            }
        }

        log.info("Role assignees updated: roleId={}, added={}, removed={}", roleId, toAdd.size(), toRemove.size());
    }

    public List<TenantRole> getRolesForUser(UUID userId) {
        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

        var roleObjects = openFgaService.listObjects(new OpenFgaListObjectsRequest(
                infrastructure.openfgaStoreId(), userId, "assignee", "role"));

        var roleIds = roleObjects.stream()
                .map(obj -> UUID.fromString(obj.replace("role:", "")))
                .toList();

        if (roleIds.isEmpty()) {
            return List.of();
        }

        return roleRepository.findAllActiveByIds(roleIds).stream()
                .map(roleMapper::toDomain)
                .toList();
    }

    @Transactional
    public void setRolesForUser(UUID userId, List<UUID> roleIds) {
        // Validiere: alle roleIds existieren und sind nicht geloescht
        if (!roleIds.isEmpty()) {
            var foundRoles = roleRepository.findAllActiveByIds(roleIds);
            if (foundRoles.size() != roleIds.size()) {
                throw new BadRequestException("role.not_found");
            }
        }

        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

        // Aktuelle Rollen des Users aus OpenFGA lesen
        var currentRoleObjects = openFgaService.listObjects(new OpenFgaListObjectsRequest(
                infrastructure.openfgaStoreId(), userId, "assignee", "role"));

        var currentRoleIds = currentRoleObjects.stream()
                .map(obj -> UUID.fromString(obj.replace("role:", "")))
                .collect(Collectors.toSet());

        var targetRoleIds = new HashSet<>(roleIds);

        Set<UUID> toAdd = new HashSet<>(targetRoleIds);
        toAdd.removeAll(currentRoleIds);

        Set<UUID> toRemove = new HashSet<>(currentRoleIds);
        toRemove.removeAll(targetRoleIds);

        if (!toAdd.isEmpty()) {
            var addTuples = toAdd.stream()
                    .map(rid -> new RelationshipTuple("user:" + userId, "assignee", "role:" + rid))
                    .toList();
            openFgaService.write(new OpenFgaWriteRequest(infrastructure.openfgaStoreId(), addTuples));
        }

        if (!toRemove.isEmpty()) {
            var removeTuples = toRemove.stream()
                    .map(rid -> new RelationshipTuple("user:" + userId, "assignee", "role:" + rid))
                    .toList();
            openFgaService.delete(new OpenFgaDeleteRequest(infrastructure.openfgaStoreId(), removeTuples));
        }

        log.info("User roles updated: userId={}, added={}, removed={}", userId, toAdd.size(), toRemove.size());
    }

    private UUID resolveCurrentUserId() {
        var authentication = (TenantAwareAuthentication)
                SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString((String) authentication.getPrincipal());
    }
}
