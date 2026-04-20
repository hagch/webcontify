package io.webcontify.identity.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.webcontify.identity.mapper.UserMapper;
import io.webcontify.identity.model.*;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
import io.webcontify.shared.security.TenantAwareAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock TenantUserRepository userRepository;
    @Mock KeycloakAdminService keycloakAdminService;
    @Mock OpenFgaService openFgaService;
    @Mock PermissionService permissionService;
    @Mock TenantInfrastructureLookupService lookupService;
    @Mock UserMapper userMapper;

    @InjectMocks UserService userService;

    static final UUID TENANT_ID = UUID.randomUUID();
    static final UUID CURRENT_USER_ID = UUID.randomUUID();
    static final UUID TARGET_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var tenantInfo = new TenantInformation(TENANT_ID, false);
        var auth = new TenantAwareAuthentication(tenantInfo, CURRENT_USER_ID.toString(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        var infra = new TenantInfrastructureInfo("store-123", "tenant-test");
        lenient().when(lookupService.getInfrastructure(TENANT_ID)).thenReturn(infra);
    }

    private TenantUserEntity createUserEntity(UUID id, UserStatus status) {
        var entity = new TenantUserEntity();
        entity.setId(id);
        entity.setTenantId(TENANT_ID);
        entity.setEmail("user@test.com");
        entity.setFirstName("Max");
        entity.setLastName("Muster");
        entity.setStatus(status);
        var auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(CURRENT_USER_ID);
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(CURRENT_USER_ID);
        entity.setAuditInfo(auditInfo);
        return entity;
    }

    @Nested
    class Invite {
        @Test
        void shouldInviteUserSuccessfully() {
            when(userRepository.findActiveByEmail("new@test.com")).thenReturn(Optional.empty());
            when(keycloakAdminService.createUser(any()))
                    .thenReturn(new CreateKeycloakUserResponse(TARGET_USER_ID));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userMapper.toDomain(any(TenantUserEntity.class))).thenReturn(new TenantUser());

            userService.invite("new@test.com", "Max", "Muster");

            verify(keycloakAdminService).createUser(any());
            verify(userRepository).save(any());
            verify(permissionService).grant(any());
            verify(keycloakAdminService).sendInviteEmail(any());
        }

        @Test
        void shouldThrowConflictWhenEmailExists() {
            var existing = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
            when(userRepository.findActiveByEmail("existing@test.com")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> userService.invite("existing@test.com", "Max", "Muster"))
                    .isInstanceOf(ConflictException.class);

            verify(keycloakAdminService, never()).createUser(any());
        }

        @Test
        void shouldCleanupKeycloakUserOnDbError() {
            when(userRepository.findActiveByEmail(any())).thenReturn(Optional.empty());
            when(keycloakAdminService.createUser(any()))
                    .thenReturn(new CreateKeycloakUserResponse(TARGET_USER_ID));
            when(userRepository.save(any())).thenThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() -> userService.invite("new@test.com", "Max", "Muster"))
                    .isInstanceOf(RuntimeException.class);

            verify(keycloakAdminService).deleteUser(any());
        }
    }

    @Nested
    class UpdateStatus {
        @Test
        void shouldDeactivateActiveUser() {
            var entity = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
            when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));
            when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(2L);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userMapper.toDomain(any(TenantUserEntity.class))).thenReturn(new TenantUser());

            userService.updateStatus(TARGET_USER_ID, UserStatus.DEACTIVATED);

            verify(keycloakAdminService).disableUser(any());
            verify(userRepository).save(any());
        }

        @Test
        void shouldRejectInvalidTransition() {
            var entity = createUserEntity(TARGET_USER_ID, UserStatus.DEACTIVATED);
            when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> userService.updateStatus(TARGET_USER_ID, UserStatus.DEACTIVATED))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void shouldRejectSelfDeactivation() {
            var entity = createUserEntity(CURRENT_USER_ID, UserStatus.ACTIVE);
            when(userRepository.findActiveById(CURRENT_USER_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> userService.updateStatus(CURRENT_USER_ID, UserStatus.DEACTIVATED))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void shouldRejectDeactivatingLastAdmin() {
            var entity = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
            when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));
            when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(1L);

            assertThatThrownBy(() -> userService.updateStatus(TARGET_USER_ID, UserStatus.DEACTIVATED))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void shouldActivateDeactivatedUser() {
            var entity = createUserEntity(TARGET_USER_ID, UserStatus.DEACTIVATED);
            when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userMapper.toDomain(any(TenantUserEntity.class))).thenReturn(new TenantUser());

            userService.updateStatus(TARGET_USER_ID, UserStatus.ACTIVE);

            verify(keycloakAdminService).enableUser(any());
        }
    }

    @Nested
    class Delete {
        @Test
        void shouldDeleteUser() {
            var entity = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
            when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));
            when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(2L);

            userService.delete(TARGET_USER_ID);

            verify(userRepository).save(any());
            verify(keycloakAdminService).deleteUser(any());
            verify(permissionService).revoke(any());
        }

        @Test
        void shouldRejectSelfDeletion() {
            assertThatThrownBy(() -> userService.delete(CURRENT_USER_ID))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void shouldRejectDeletingLastAdmin() {
            var entity = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
            when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));
            when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(1L);

            assertThatThrownBy(() -> userService.delete(TARGET_USER_ID))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    class ResendInvite {
        @Test
        void shouldResendInviteForInvitedUser() {
            var entity = createUserEntity(TARGET_USER_ID, UserStatus.INVITED);
            when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));

            userService.resendInvite(TARGET_USER_ID);

            verify(keycloakAdminService).sendInviteEmail(any());
        }

        @Test
        void shouldRejectResendForActiveUser() {
            var entity = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
            when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> userService.resendInvite(TARGET_USER_ID))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
