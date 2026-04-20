package io.webcontify.identity.api;

import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.ProvisioningStatus;
import io.webcontify.tenant.event.TenantCreatedEvent;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningControllerTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TenantProvisioningController controller;

    @BeforeEach
    void setUp() {
        controller = new TenantProvisioningController(tenantRepository, eventPublisher);
    }

    @Test
    void retryProvisioning_whenTenantInProvisioningStatus_returns202AndPublishesEvent() {
        UUID tenantId = UUID.randomUUID();
        ProvisioningStatus provisioningStatus = ProvisioningStatus.initial("admin@example.com", "John", "Doe");

        TenantEntity entity = new TenantEntity();
        entity.setId(tenantId);
        entity.setSlug("test-tenant");
        entity.setStatus(TenantStatus.PROVISIONING.name());
        entity.setProvisioningStatus(provisioningStatus.toJson());

        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.of(entity));

        ResponseEntity<Void> response = controller.retryProvisioning(tenantId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ArgumentCaptor<TenantCreatedEvent> eventCaptor = ArgumentCaptor.forClass(TenantCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TenantCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.tenantId()).isEqualTo(tenantId);
        assertThat(publishedEvent.slug()).isEqualTo("test-tenant");
        assertThat(publishedEvent.adminEmail()).isEqualTo("admin@example.com");
        assertThat(publishedEvent.adminFirstName()).isEqualTo("John");
        assertThat(publishedEvent.adminLastName()).isEqualTo("Doe");
    }

    @Test
    void retryProvisioning_whenTenantNotInProvisioningStatus_throwsConflictException() {
        UUID tenantId = UUID.randomUUID();

        TenantEntity entity = new TenantEntity();
        entity.setId(tenantId);
        entity.setSlug("active-tenant");
        entity.setStatus(TenantStatus.ACTIVE.name());
        entity.setProvisioningStatus(ProvisioningStatus.initial("admin@example.com", "Jane", "Smith").toJson());

        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> controller.retryProvisioning(tenantId))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void retryProvisioning_whenTenantNotFound_throwsNotFoundException() {
        UUID tenantId = UUID.randomUUID();

        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.retryProvisioning(tenantId))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(eventPublisher);
    }
}
