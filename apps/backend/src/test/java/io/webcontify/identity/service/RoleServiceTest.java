package io.webcontify.identity.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

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
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock TenantRoleRepository roleRepository;
    @Mock TenantUserRepository userRepository;
    @Mock OpenFgaService openFgaService;
    @Mock TenantInfrastructureLookupService lookupService;
    @Mock RoleMapper roleMapper;
    @Mock PermissionService permissionService;

    @InjectMocks RoleService roleService;

    static final UUID TENANT_ID = UUID.randomUUID();
    static final UUID CURRENT_USER_ID = UUID.randomUUID();
    static final UUID ROLE_ID = UUID.randomUUID();
    static final UUID USER_ID_1 = UUID.randomUUID();
    static final UUID USER_ID_2 = UUID.randomUUID();
    static final String STORE_ID = "store-abc";

    @BeforeEach
    void setUp() {
        var tenantInfo = new TenantInformation(TENANT_ID, false);
        var auth = new TenantAwareAuthentication(tenantInfo, CURRENT_USER_ID, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        var infra = new TenantInfrastructureInfo(STORE_ID, "tenant-test");
        lenient().when(lookupService.getInfrastructure(TENANT_ID)).thenReturn(infra);
    }

    private TenantRoleEntity createRoleEntity(UUID id, String name, boolean systemRole) {
        var entity = new TenantRoleEntity();
        entity.setId(id);
        entity.setTenantId(TENANT_ID);
        entity.setName(name);
        entity.setDescription("Test description");
        entity.setSystemRole(systemRole);
        var auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(CURRENT_USER_ID);
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(CURRENT_USER_ID);
        entity.setAuditInfo(auditInfo);
        return entity;
    }

    private TenantUserEntity createUserEntity(UUID id) {
        var entity = new TenantUserEntity();
        entity.setId(id);
        entity.setTenantId(TENANT_ID);
        entity.setEmail("user@test.com");
        entity.setFirstName("Max");
        entity.setLastName("Muster");
        entity.setStatus(UserStatus.ACTIVE);
        var auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(CURRENT_USER_ID);
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(CURRENT_USER_ID);
        entity.setAuditInfo(auditInfo);
        return entity;
    }

    @Nested
    class Create {

        @Test
        void shouldCreateCustomRole() {
            when(roleRepository.findActiveByName("Editor")).thenReturn(Optional.empty());
            var savedEntity = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.save(any())).thenReturn(savedEntity);
            var expectedDomain = new TenantRole();
            expectedDomain.setId(ROLE_ID);
            when(roleMapper.toDomain(savedEntity)).thenReturn(expectedDomain);

            var result = roleService.create("Editor", "Can edit content");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(ROLE_ID);
            verify(roleRepository).save(any());
            verify(openFgaService).write(argThat(req ->
                req.storeId().equals(STORE_ID) &&
                req.tuples().size() == 1 &&
                req.tuples().get(0).relation().equals("tenant") &&
                req.tuples().get(0).object().startsWith("role:")
            ));
        }

        @Test
        void shouldThrowConflictWhenNameExists() {
            var existing = createRoleEntity(UUID.randomUUID(), "Admin", true);
            when(roleRepository.findActiveByName("Admin")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> roleService.create("Admin", "desc"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("role.name_already_exists");

            verify(roleRepository, never()).save(any());
            verify(openFgaService, never()).write(any());
        }
    }

    @Nested
    class Update {

        @Test
        void shouldThrowWhenUpdatingSystemRole() {
            var systemRole = createRoleEntity(ROLE_ID, "Admin", true);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(systemRole));

            assertThatThrownBy(() -> roleService.update(ROLE_ID, "NewName", "desc"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("role.system_role_cannot_be_modified");

            verify(roleRepository, never()).save(any());
        }

        @Test
        void shouldUpdateCustomRole() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));
            when(roleRepository.findActiveByName("NewName")).thenReturn(Optional.empty());
            when(roleRepository.save(any())).thenReturn(customRole);
            var expectedDomain = new TenantRole();
            expectedDomain.setName("NewName");
            when(roleMapper.toDomain(customRole)).thenReturn(expectedDomain);

            var result = roleService.update(ROLE_ID, "NewName", "new desc");

            assertThat(result).isNotNull();
            verify(roleRepository).save(argThat(e -> e.getName().equals("NewName")));
        }

        @Test
        void shouldThrowConflictWhenNewNameAlreadyExists() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            var otherRole = createRoleEntity(UUID.randomUUID(), "Viewer", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));
            when(roleRepository.findActiveByName("Viewer")).thenReturn(Optional.of(otherRole));

            assertThatThrownBy(() -> roleService.update(ROLE_ID, "Viewer", "desc"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("role.name_already_exists");
        }

        @Test
        void shouldThrowNotFoundWhenRoleDoesNotExist() {
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roleService.update(ROLE_ID, "Name", "desc"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("role.not_found");
        }
    }

    @Nested
    class Delete {

        @Test
        void shouldThrowWhenDeletingSystemRole() {
            var systemRole = createRoleEntity(ROLE_ID, "Admin", true);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(systemRole));

            assertThatThrownBy(() -> roleService.delete(ROLE_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("role.system_role_cannot_be_deleted");

            verify(roleRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenRoleHasAssignees() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));
            var assigneeTuple = new RelationshipTuple("user:" + USER_ID_1, "assignee", "role:" + ROLE_ID);
            when(openFgaService.readTuples(argThat(req ->
                req.storeId().equals(STORE_ID) &&
                req.objectType().equals("role") &&
                req.objectId().equals(ROLE_ID.toString()) &&
                req.relation().equals("assignee")
            ))).thenReturn(List.of(assigneeTuple));

            assertThatThrownBy(() -> roleService.delete(ROLE_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("role.has_assignees");

            verify(roleRepository, never()).save(any());
        }

        @Test
        void shouldDeleteCustomRole() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));
            when(openFgaService.readTuples(any())).thenReturn(List.of());
            when(roleRepository.save(any())).thenReturn(customRole);

            roleService.delete(ROLE_ID);

            verify(roleRepository).save(argThat(e ->
                e.getAuditInfo().getDeletedAt() != null &&
                e.getAuditInfo().getDeletedBy().equals(CURRENT_USER_ID)
            ));
            // Tenant-Tuple cleanup
            verify(openFgaService).delete(any());
        }

        @Test
        void shouldCleanupPermissionTuplesOnDelete() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));

            String roleSubject = "role:" + ROLE_ID + "#assignee";
            var permissionTuple = new RelationshipTuple(
                    roleSubject, "inviter", "user_management:" + TENANT_ID);

            // Default: leere Liste fuer alle readTuples-Anfragen
            when(openFgaService.readTuples(any())).thenReturn(List.of());
            // Ueberschreibe fuer den spezifischen inviter/user_management-Match
            when(openFgaService.readTuples(argThat(req ->
                req != null && "inviter".equals(req.relation()) && "user_management".equals(req.objectType())
            ))).thenReturn(List.of(permissionTuple));
            when(roleRepository.save(any())).thenReturn(customRole);

            roleService.delete(ROLE_ID);

            // Muss zweimal delete aufrufen: einmal Permission-Cleanup, einmal Tenant-Tuple
            verify(openFgaService, atLeast(2)).delete(any());
        }

        @Test
        void shouldContinueDeleteEvenIfOpenFgaCleanupFails() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));
            when(openFgaService.readTuples(any())).thenReturn(List.of());
            when(roleRepository.save(any())).thenReturn(customRole);
            doThrow(new RuntimeException("OpenFGA unavailable")).when(openFgaService).delete(any());

            // Sollte keine Exception werfen (best-effort cleanup)
            assertThatCode(() -> roleService.delete(ROLE_ID)).doesNotThrowAnyException();
            verify(roleRepository).save(any());
        }
    }

    @Nested
    class SetPermissions {

        private RolePermissionEntry entry(String resourceType, String... actions) {
            return new RolePermissionEntry(resourceType, List.of(actions));
        }

        @Test
        void shouldWritePermissionTuples() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));
            // Keine bestehenden Tuples
            when(openFgaService.readTuples(any())).thenReturn(List.of());
            // Privilege escalation check: current user hat inviter-Recht
            when(permissionService.check(any())).thenReturn(true);

            roleService.setPermissions(ROLE_ID, List.of(entry("user_management", "inviter")));

            verify(openFgaService).write(argThat(req ->
                req.storeId().equals(STORE_ID) &&
                req.tuples().size() == 1 &&
                req.tuples().get(0).user().equals("role:" + ROLE_ID + "#assignee") &&
                req.tuples().get(0).relation().equals("inviter") &&
                req.tuples().get(0).object().equals("user_management:" + TENANT_ID)
            ));
            verify(openFgaService, never()).delete(any());
        }

        @Test
        void shouldRejectSystemRole() {
            var adminRole = createRoleEntity(ROLE_ID, "Admin", true);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(adminRole));

            assertThatThrownBy(() -> roleService.setPermissions(ROLE_ID,
                    List.of(entry("user_management", "inviter"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("role.system_role_permissions_cannot_be_modified");

            verify(openFgaService, never()).write(any());
        }

        @Test
        void shouldWriteAppManagementPermissionTuples() {
            var customRole = createRoleEntity(ROLE_ID, "AppManager", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));
            when(openFgaService.readTuples(any())).thenReturn(List.of());
            when(permissionService.check(any())).thenReturn(true);

            roleService.setPermissions(ROLE_ID, List.of(entry("app_management", "viewer", "editor")));

            verify(openFgaService).write(argThat(req ->
                req.storeId().equals(STORE_ID) &&
                req.tuples().size() == 2 &&
                req.tuples().stream().allMatch(t ->
                    t.user().equals("role:" + ROLE_ID + "#assignee") &&
                    t.object().equals("app_management:" + TENANT_ID)) &&
                req.tuples().stream().anyMatch(t -> t.relation().equals("viewer")) &&
                req.tuples().stream().anyMatch(t -> t.relation().equals("editor"))
            ));
            verify(openFgaService, never()).delete(any());
        }

        @Test
        void shouldRejectInvalidAppManagementAction() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));

            assertThatThrownBy(() -> roleService.setPermissions(ROLE_ID,
                    List.of(entry("app_management", "deleter"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("role.invalid_action");

            verify(openFgaService, never()).write(any());
        }

        @Test
        void shouldRejectUnknownResourceType() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));

            assertThatThrownBy(() -> roleService.setPermissions(ROLE_ID,
                    List.of(entry("unknown_resource", "reader"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("role.invalid_resource_type");

            verify(openFgaService, never()).write(any());
        }

        @Test
        void shouldRejectPrivilegeEscalation() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));
            // Current user hat NICHT editor-Recht auf user_management
            when(permissionService.check(any())).thenReturn(false);

            assertThatThrownBy(() -> roleService.setPermissions(ROLE_ID,
                    List.of(entry("user_management", "editor"))))
                    .isInstanceOf(AccessDeniedException.class);

            verify(openFgaService, never()).write(any());
        }

        @Test
        void shouldDeltaUpdatePermissions() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));

            String roleSubject = "role:" + ROLE_ID + "#assignee";
            // Aktuell hat die Rolle "inviter" auf user_management
            var existingTuple = new RelationshipTuple(
                    roleSubject, "inviter", "user_management:" + TENANT_ID);
            // Default: leere Liste fuer alle readTuples-Anfragen
            when(openFgaService.readTuples(any())).thenReturn(List.of());
            // Ueberschreibe fuer den spezifischen inviter/user_management-Match
            when(openFgaService.readTuples(argThat(req ->
                req != null && "inviter".equals(req.relation()) && "user_management".equals(req.objectType())
            ))).thenReturn(List.of(existingTuple));

            // Privilege escalation check: user hat editor-Recht
            when(permissionService.check(any())).thenReturn(true);

            // Setze nun "editor" (nicht mehr "inviter")
            roleService.setPermissions(ROLE_ID, List.of(entry("user_management", "editor")));

            // "editor" schreiben
            verify(openFgaService).write(argThat(req ->
                req.tuples().size() == 1 &&
                req.tuples().get(0).relation().equals("editor")
            ));
            // "inviter" loeschen
            verify(openFgaService).delete(argThat(req ->
                req.tuples().size() == 1 &&
                req.tuples().get(0).relation().equals("inviter")
            ));
        }
    }

    @Nested
    class SetAssignees {

        @Test
        void shouldSetAssignees() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));

            var user1 = createUserEntity(USER_ID_1);
            var user2 = createUserEntity(USER_ID_2);
            when(userRepository.findAllById(List.of(USER_ID_1, USER_ID_2)))
                    .thenReturn(List.of(user1, user2));

            // Keine aktuellen Assignees
            when(openFgaService.readTuples(argThat(req ->
                req.relation().equals("assignee") && req.objectId().equals(ROLE_ID.toString())
            ))).thenReturn(List.of());

            roleService.setAssignees(ROLE_ID, List.of(USER_ID_1, USER_ID_2));

            verify(openFgaService).write(argThat(req ->
                req.storeId().equals(STORE_ID) &&
                req.tuples().size() == 2
            ));
            verify(openFgaService, never()).delete(any());
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));
            when(userRepository.findAllById(List.of(USER_ID_1, USER_ID_2)))
                    .thenReturn(List.of(createUserEntity(USER_ID_1))); // nur einer gefunden

            assertThatThrownBy(() -> roleService.setAssignees(ROLE_ID, List.of(USER_ID_1, USER_ID_2)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("role.assignee_not_found");

            verify(openFgaService, never()).write(any());
        }

        @Test
        void shouldRemoveAssigneesNoLongerInList() {
            var customRole = createRoleEntity(ROLE_ID, "Editor", false);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(customRole));
            when(userRepository.findAllById(List.of(USER_ID_1)))
                    .thenReturn(List.of(createUserEntity(USER_ID_1)));

            // User2 war vorher assignee, soll entfernt werden
            var existingTuple = new RelationshipTuple("user:" + USER_ID_2, "assignee", "role:" + ROLE_ID);
            when(openFgaService.readTuples(any())).thenReturn(List.of(existingTuple));

            roleService.setAssignees(ROLE_ID, List.of(USER_ID_1));

            verify(openFgaService).write(argThat(req -> req.tuples().size() == 1));
            verify(openFgaService).delete(argThat(req ->
                req.tuples().size() == 1 &&
                req.tuples().get(0).user().equals("user:" + USER_ID_2)
            ));
        }

        @Test
        void shouldThrowWhenRemovingLastAdminAssignee() {
            // Admin-Systemrolle
            var adminRole = createRoleEntity(ROLE_ID, "Admin", true);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(adminRole));

            // Aktuell ein Assignee
            var existingTuple = new RelationshipTuple("user:" + USER_ID_1, "assignee", "role:" + ROLE_ID);
            when(openFgaService.readTuples(any())).thenReturn(List.of(existingTuple));

            // Versuche alle Assignees zu entfernen (leere Liste)
            assertThatThrownBy(() -> roleService.setAssignees(ROLE_ID, List.of()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("role.last_admin_cannot_be_removed");
        }

        @Test
        void shouldWriteAdminTupleWhenAddingUserToAdminRole() {
            var adminRole = createRoleEntity(ROLE_ID, "Admin", true);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(adminRole));

            var user1 = createUserEntity(USER_ID_1);
            when(userRepository.findAllById(List.of(USER_ID_1))).thenReturn(List.of(user1));

            // Keine aktuellen Assignees
            when(openFgaService.readTuples(argThat(req ->
                req.relation().equals("assignee") && req.objectId().equals(ROLE_ID.toString())
            ))).thenReturn(List.of());

            roleService.setAssignees(ROLE_ID, List.of(USER_ID_1));

            // Assignee tuple + admin tuple
            verify(openFgaService, times(2)).write(any());
            verify(openFgaService).write(argThat(req ->
                req.tuples().size() == 1 &&
                req.tuples().get(0).user().equals("user:" + USER_ID_1) &&
                req.tuples().get(0).relation().equals("admin") &&
                req.tuples().get(0).object().equals("tenant:" + TENANT_ID)
            ));
        }

        @Test
        void shouldDeleteAdminTupleWhenRemovingUserFromAdminRole() {
            var adminRole = createRoleEntity(ROLE_ID, "Admin", true);
            when(roleRepository.findActiveById(ROLE_ID)).thenReturn(Optional.of(adminRole));

            var user2 = createUserEntity(USER_ID_2);
            when(userRepository.findAllById(List.of(USER_ID_2))).thenReturn(List.of(user2));

            // First call: both users assigned. Second call (compensating check): user2 remains
            var tuple1 = new RelationshipTuple("user:" + USER_ID_1, "assignee", "role:" + ROLE_ID);
            var tuple2 = new RelationshipTuple("user:" + USER_ID_2, "assignee", "role:" + ROLE_ID);
            when(openFgaService.readTuples(argThat(req ->
                req != null && "assignee".equals(req.relation()) && ROLE_ID.toString().equals(req.objectId())
            ))).thenReturn(List.of(tuple1, tuple2)).thenReturn(List.of(tuple2));

            roleService.setAssignees(ROLE_ID, List.of(USER_ID_2));

            // Assignee remove + admin tuple remove
            verify(openFgaService, times(2)).delete(any());
            verify(openFgaService).delete(argThat(req ->
                req.tuples().size() == 1 &&
                req.tuples().get(0).user().equals("user:" + USER_ID_1) &&
                req.tuples().get(0).relation().equals("admin") &&
                req.tuples().get(0).object().equals("tenant:" + TENANT_ID)
            ));
        }
    }

    @Nested
    class SetRolesForUser {

        @Test
        void shouldWriteAdminTupleWhenAssigningAdminRole() {
            UUID adminRoleId = UUID.randomUUID();
            var adminRoleEntity = createRoleEntity(adminRoleId, "Admin", true);
            when(roleRepository.findAllActiveByIds(List.of(adminRoleId))).thenReturn(List.of(adminRoleEntity));
            when(roleRepository.findActiveByName("Admin")).thenReturn(Optional.of(adminRoleEntity));
            when(openFgaService.listObjects(any())).thenReturn(List.of());

            roleService.setRolesForUser(USER_ID_1, List.of(adminRoleId));

            // Assignee tuple + admin tuple
            verify(openFgaService, times(2)).write(any());
            verify(openFgaService).write(argThat(req ->
                req.tuples().size() == 1 &&
                req.tuples().get(0).user().equals("user:" + USER_ID_1) &&
                req.tuples().get(0).relation().equals("admin") &&
                req.tuples().get(0).object().equals("tenant:" + TENANT_ID)
            ));
        }

        @Test
        void shouldDeleteAdminTupleWhenRemovingAdminRole() {
            UUID adminRoleId = UUID.randomUUID();
            UUID otherRoleId = UUID.randomUUID();
            var adminRoleEntity = createRoleEntity(adminRoleId, "Admin", true);
            var otherRoleEntity = createRoleEntity(otherRoleId, "Editor", false);

            when(roleRepository.findAllActiveByIds(List.of(otherRoleId))).thenReturn(List.of(otherRoleEntity));
            when(roleRepository.findActiveByName("Admin")).thenReturn(Optional.of(adminRoleEntity));
            // Currently has admin + other role
            when(openFgaService.listObjects(any())).thenReturn(List.of("role:" + adminRoleId, "role:" + otherRoleId));

            roleService.setRolesForUser(USER_ID_1, List.of(otherRoleId));

            // Assignee remove (admin role) + admin tuple remove
            verify(openFgaService).delete(argThat(req ->
                req.tuples().stream().anyMatch(t ->
                    t.user().equals("user:" + USER_ID_1) &&
                    t.relation().equals("assignee") &&
                    t.object().equals("role:" + adminRoleId))
            ));
            verify(openFgaService).delete(argThat(req ->
                req.tuples().stream().anyMatch(t ->
                    t.user().equals("user:" + USER_ID_1) &&
                    t.relation().equals("admin") &&
                    t.object().equals("tenant:" + TENANT_ID))
            ));
        }
    }
}
