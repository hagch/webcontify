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
                "#10B981",
                "#EF4444",
                "#059669",
                "#F59E0B",
                "inter",
                "0.5rem",
                "https://s3.example.com/tenant-acme/config/logo.png",
                null);

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
        assertThat(request.brandingAttributes()).containsEntry("brandAccentColor", "#10B981");
        assertThat(request.brandingAttributes()).containsEntry("brandDangerColor", "#EF4444");
        assertThat(request.brandingAttributes()).containsEntry("brandSuccessColor", "#059669");
        assertThat(request.brandingAttributes()).containsEntry("brandWarningColor", "#F59E0B");
        assertThat(request.brandingAttributes()).containsEntry("brandFontFamily", "'Inter', sans-serif");
        assertThat(request.brandingAttributes()).containsEntry("brandBorderRadius", "0.5rem");
    }

    @Test
    void skipsInvalidHexColor() {
        var event = new TenantBrandingUpdatedEvent(
                UUID.randomUUID(),
                "Acme Corp",
                "tenant-acme",
                "not-a-hex",
                "#445566",
                null, null, null, null, null, null,
                null,
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
                null, null, null, null, null, null, null,
                "javascript:alert(1)",
                null);

        listener.onBrandingUpdated(event);

        ArgumentCaptor<UpdateRealmBrandingRequest> captor =
                ArgumentCaptor.forClass(UpdateRealmBrandingRequest.class);
        verify(keycloakAdminService).updateRealmBranding(captor.capture());

        var attributes = captor.getValue().brandingAttributes();
        assertThat(attributes).doesNotContainKey("brandLogo");
    }

    @Test
    void skipsInvalidFontFamily() {
        var event = new TenantBrandingUpdatedEvent(
                UUID.randomUUID(),
                "Acme Corp",
                "tenant-acme",
                "#112233",
                null, null, null, null, null,
                "comic-sans",
                null,
                null,
                null);

        var attributes = listener.buildAttributes(event);
        assertThat(attributes).doesNotContainKey("brandFontFamily");
    }

    @Test
    void skipsInvalidBorderRadius() {
        var event = new TenantBrandingUpdatedEvent(
                UUID.randomUUID(),
                "Acme Corp",
                "tenant-acme",
                "#112233",
                null, null, null, null, null, null,
                "99px",
                null,
                null);

        var attributes = listener.buildAttributes(event);
        assertThat(attributes).doesNotContainKey("brandBorderRadius");
    }

    @Test
    void mapsRobotoFontToCss() {
        var event = new TenantBrandingUpdatedEvent(
                UUID.randomUUID(),
                "Acme Corp",
                "tenant-acme",
                null, null, null, null, null, null,
                "roboto",
                null,
                null,
                null);

        var attributes = listener.buildAttributes(event);
        assertThat(attributes).containsEntry("brandFontFamily", "'Roboto', sans-serif");
    }

    @Test
    void mapsMerriweatherFontToCssWithSerifStack() {
        var event = new TenantBrandingUpdatedEvent(
                UUID.randomUUID(),
                "Acme Corp",
                "tenant-acme",
                null, null, null, null, null, null,
                "merriweather",
                null,
                null,
                null);

        var attributes = listener.buildAttributes(event);
        assertThat(attributes).containsEntry("brandFontFamily", "'Merriweather', serif");
    }

    @Test
    void unknownFontFallsBackToSystemUi() {
        var event = new TenantBrandingUpdatedEvent(
                UUID.randomUUID(),
                "Acme Corp",
                "tenant-acme",
                null, null, null, null, null, null,
                "system",
                null,
                null,
                null);

        var attributes = listener.buildAttributes(event);
        assertThat(attributes).containsEntry("brandFontFamily", "system-ui, sans-serif");
    }

    @Test
    void handlesKeycloakFailureGracefully() {
        var event = new TenantBrandingUpdatedEvent(
                UUID.randomUUID(),
                "Acme Corp",
                "tenant-acme",
                "#112233",
                null, null, null, null, null, null, null,
                null,
                null);

        doThrow(new RuntimeException("Keycloak unavailable"))
                .when(keycloakAdminService).updateRealmBranding(any());

        // Must not propagate exception
        listener.onBrandingUpdated(event);

        verify(keycloakAdminService).updateRealmBranding(any());
    }
}
