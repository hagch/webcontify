package io.webcontify.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.model.AuditInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class UserActivationServiceTest {

    @Mock TenantUserRepository userRepository;
    @InjectMocks UserActivationService activationService;

    private TenantUserEntity createEntity(UUID id, UserStatus status) {
        var entity = new TenantUserEntity();
        entity.setId(id);
        entity.setStatus(status);
        var auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(UUID.randomUUID());
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(UUID.randomUUID());
        entity.setAuditInfo(auditInfo);
        return entity;
    }

    @Test
    void shouldActivateInvitedUserOnFirstLogin() {
        UUID userId = UUID.randomUUID();
        var entity = createEntity(userId, UserStatus.INVITED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));

        activationService.activateOnFirstLogin(userId);

        assertThat(entity.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(entity);
    }

    @Test
    void shouldNotChangeAlreadyActiveUser() {
        UUID userId = UUID.randomUUID();
        var entity = createEntity(userId, UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));

        activationService.activateOnFirstLogin(userId);

        assertThat(entity.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNotActivateDeactivatedUser() {
        UUID userId = UUID.randomUUID();
        var entity = createEntity(userId, UserStatus.DEACTIVATED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));

        activationService.activateOnFirstLogin(userId);

        assertThat(entity.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldHandleNonExistentUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        activationService.activateOnFirstLogin(userId);

        verify(userRepository, never()).save(any());
    }
}
