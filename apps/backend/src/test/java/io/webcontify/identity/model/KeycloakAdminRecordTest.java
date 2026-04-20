package io.webcontify.identity.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KeycloakAdminRecordTest {

    @Test
    void testSmtpConfigRecord() {
        SmtpConfig config = new SmtpConfig("smtp.example.com", 587, "user@example.com", "password", true);

        assertEquals("smtp.example.com", config.host());
        assertEquals(587, config.port());
        assertEquals("user@example.com", config.username());
        assertEquals("password", config.password());
        assertEquals(true, config.starttls());
    }

    @Test
    void testCreateRealmRequest() {
        SmtpConfig smtp = new SmtpConfig("smtp.example.com", 587, "user@example.com", "password", true);
        CreateRealmRequest request = new CreateRealmRequest("test-tenant", smtp);

        assertEquals("test-tenant", request.tenantSlug());
        assertNotNull(request.platformSmtpConfig());
        assertEquals("smtp.example.com", request.platformSmtpConfig().host());
    }

    @Test
    void testCreateRealmResponse() {
        CreateRealmResponse response = new CreateRealmResponse("test-realm");

        assertEquals("test-realm", response.realmName());
    }

    @Test
    void testCreateKeycloakUserRequest() {
        CreateKeycloakUserRequest request =
                new CreateKeycloakUserRequest("test-realm", "user@example.com", "John", "Doe");

        assertEquals("test-realm", request.realmName());
        assertEquals("user@example.com", request.email());
        assertEquals("John", request.firstName());
        assertEquals("Doe", request.lastName());
    }

    @Test
    void testCreateKeycloakUserResponse() {
        UUID userId = UUID.randomUUID();
        CreateKeycloakUserResponse response = new CreateKeycloakUserResponse(userId);

        assertEquals(userId, response.keycloakUserId());
    }

    @Test
    void testSendInviteEmailRequest() {
        UUID userId = UUID.randomUUID();
        SendInviteEmailRequest request = new SendInviteEmailRequest("test-realm", userId);

        assertEquals("test-realm", request.realmName());
        assertEquals(userId, request.userId());
    }

    @Test
    void testUpdateRealmSmtpRequest() {
        SmtpConfig smtp = new SmtpConfig("smtp.example.com", 587, "user@example.com", "password", true);
        UpdateRealmSmtpRequest request = new UpdateRealmSmtpRequest("test-realm", smtp);

        assertEquals("test-realm", request.realmName());
        assertNotNull(request.smtpConfig());
        assertEquals("smtp.example.com", request.smtpConfig().host());
    }

    @Test
    void testUpdateRealmBrandingRequest() {
        Map<String, String> branding = new HashMap<>();
        branding.put("primaryColor", "#6366F1");
        branding.put("logo", "https://example.com/logo.png");

        UpdateRealmBrandingRequest request = new UpdateRealmBrandingRequest("test-realm", branding);

        assertEquals("test-realm", request.realmName());
        assertEquals("#6366F1", request.brandingAttributes().get("primaryColor"));
        assertEquals("https://example.com/logo.png", request.brandingAttributes().get("logo"));
    }

    @Test
    void testDeleteKeycloakUserRequest() {
        UUID userId = UUID.randomUUID();
        DeleteKeycloakUserRequest request = new DeleteKeycloakUserRequest("test-realm", userId);

        assertEquals("test-realm", request.realmName());
        assertEquals(userId, request.userId());
    }

    @Test
    void testUpdateKeycloakUserRequest() {
        UUID userId = UUID.randomUUID();
        UpdateKeycloakUserRequest request =
                new UpdateKeycloakUserRequest("test-realm", userId, "new@example.com", "Jane", "Smith");

        assertEquals("test-realm", request.realmName());
        assertEquals(userId, request.userId());
        assertEquals("new@example.com", request.email());
        assertEquals("Jane", request.firstName());
        assertEquals("Smith", request.lastName());
    }
}
