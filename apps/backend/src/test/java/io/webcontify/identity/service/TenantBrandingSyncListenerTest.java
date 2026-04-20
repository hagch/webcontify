package io.webcontify.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.webcontify.identity.model.UpdateRealmBrandingRequest;
import io.webcontify.tenant.event.TenantBrandingUpdatedEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantBrandingSyncListenerTest {

    @Mock
    private KeycloakAdminService keycloakAdminService;

    private TenantBrandingSyncListener listener;

    @BeforeEach
    void setUp() {
        listener = new TenantBrandingSyncListener(keycloakAdminService);
    }

    @Test
    void syncsBrandingToKeycloak() {
        var event = new TenantBrandingUpdatedEvent(
                UUID.randomUUID(),
                "Acme Corp",
                "tenant-acme",
                "#112233",
                "#445566",
                "https://s3.example.com/tenant-acme/config/logo.png");

        listener.onBrandingUpdated(event);

        ArgumentCaptor<UpdateRealmBrandingRequest> captor =
                ArgumentCaptor.forClass(UpdateRealmBrandingRequest.class);
        verify(keycloakAdminService).updateRealmBranding(captor.capture());

        var request = captor.getValue();
        assertThat(request.realmName()).isEqualTo("tenant-acme");
        assertThat(request.brandingAttributes()).containsEntry("brandTenantName", "Acme Corp");
        assertThat(request.brandingAttributes()).containsEntry("brandPrimaryColor", "#112233");
        assertThat(request.brandingAttributes()).containsEntry("brandSecondaryColor", "#445566");
        assertThat(request.brandingAttributes()).containsEntry("brandLogo",
                "https://s3.example.com/tenant-acme/config/logo.png");
    }

    @Test
    void skipsInvalidHexColor() {
        var event = new TenantBrandingUpdatedEvent(
                UUID.randomUUID(),
                "Acme Corp",
                "tenant-acme",
                "not-a-hex",
                "#445566",
                null);

        listener.onBrandingUpdated(event);

        ArgumentCaptor<UpdateRealmBrandingRequest> captor =
                ArgumentCaptor.forClass(UpdateRealmBrandingRequest.class);
        verify(keycloakAdminService).updateRealmBranding(captor.capture());

        var attributes = captor.getValue().brandingAttributes();
        assertThat(attributes).doesNotContainKey("brandPrimaryColor");
        assertThat(attributes).containsEntry("brandSecondaryColor", "#445566");
    }

    @Test
    void skipsInvalidLogoUrl() {
        var event = new TenantBrandingUpdatedEvent(
                UUID.randomUUID(),
                "Acme Corp",
                "tenant-acme",
                "#112233",
                null,
                "javascript:alert(1)");

        listener.onBrandingUpdated(event);

        ArgumentCaptor<UpdateRealmBrandingRequest> captor =
                ArgumentCaptor.forClass(UpdateRealmBrandingRequest.class);
        verify(keycloakAdminService).updateRealmBranding(captor.capture());

        var attributes = captor.getValue().brandingAttributes();
        assertThat(attributes).doesNotContainKey("brandLogo");
    }

    @Test
    void handlesKeycloakFailureGracefully() {
        var event = new TenantBrandingUpdatedEvent(
                UUID.randomUUID(),
                "Acme Corp",
                "tenant-acme",
                "#112233",
                null,
                null);

        doThrow(new RuntimeException("Keycloak unavailable"))
                .when(keycloakAdminService).updateRealmBranding(any());

        // Must not propagate exception
        listener.onBrandingUpdated(event);

        verify(keycloakAdminService).updateRealmBranding(any());
    }
}
