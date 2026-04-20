package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KeycloakAdminService {

    private final Keycloak keycloak;

    public KeycloakAdminService(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    public CreateRealmResponse createRealm(CreateRealmRequest request) {
        String realmName = "tenant-" + request.tenantSlug();
        var realm = new RealmRepresentation();
        realm.setRealm(realmName);
        realm.setEnabled(true);
        realm.setRegistrationAllowed(false);
        realm.setResetPasswordAllowed(true);
        realm.setLoginWithEmailAllowed(true);
        var smtp = request.platformSmtpConfig();
        realm.setSmtpServer(Map.of(
                "host", smtp.host(),
                "port", String.valueOf(smtp.port()),
                "user", smtp.username(),
                "password", smtp.password(),
                "starttls", String.valueOf(smtp.starttls()),
                "auth", "true"
        ));
        var client = new ClientRepresentation();
        client.setClientId("tenant-ui");
        client.setPublicClient(true);
        client.setStandardFlowEnabled(true);
        client.setDirectAccessGrantsEnabled(false);
        client.setRedirectUris(List.of("http://" + request.tenantSlug() + ".localhost/*"));
        client.setWebOrigins(List.of("http://" + request.tenantSlug() + ".localhost"));
        client.setAttributes(Map.of("pkce.code.challenge.method", "S256"));
        realm.setClients(List.of(client));
        keycloak.realms().create(realm);
        return new CreateRealmResponse(realmName);
    }

    public CreateKeycloakUserResponse createUser(CreateKeycloakUserRequest request) {
        var user = new UserRepresentation();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setRequiredActions(List.of("UPDATE_PASSWORD"));
        var response = keycloak.realm(request.realmName()).users().create(user);
        String path = response.getLocation().getPath();
        String userId = path.replaceAll(".*/([^/]+)$", "$1");
        return new CreateKeycloakUserResponse(UUID.fromString(userId));
    }

    public void sendInviteEmail(SendInviteEmailRequest request) {
        keycloak.realm(request.realmName())
                .users()
                .get(request.userId().toString())
                .executeActionsEmail(List.of("UPDATE_PASSWORD"));
    }

    public void updateRealmSmtp(UpdateRealmSmtpRequest request) {
        var realmResource = keycloak.realm(request.realmName());
        var realm = realmResource.toRepresentation();
        var smtp = request.smtpConfig();
        realm.setSmtpServer(Map.of(
                "host", smtp.host(),
                "port", String.valueOf(smtp.port()),
                "user", smtp.username(),
                "password", smtp.password(),
                "starttls", String.valueOf(smtp.starttls()),
                "auth", "true"
        ));
        realmResource.update(realm);
    }

    public void updateRealmBranding(UpdateRealmBrandingRequest request) {
        var realmResource = keycloak.realm(request.realmName());
        var realm = realmResource.toRepresentation();
        realm.setAttributes(request.brandingAttributes());
        realmResource.update(realm);
    }

    public void deleteUser(DeleteKeycloakUserRequest request) {
        keycloak.realm(request.realmName())
                .users()
                .get(request.userId().toString())
                .remove();
    }

    public void updateUser(UpdateKeycloakUserRequest request) {
        var userResource = keycloak.realm(request.realmName())
                .users()
                .get(request.userId().toString());
        var user = userResource.toRepresentation();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        userResource.update(user);
    }

    public void enableUser(EnableKeycloakUserRequest request) {
        var userResource = keycloak.realm(request.realmName())
                .users()
                .get(request.userId().toString());
        var user = userResource.toRepresentation();
        user.setEnabled(true);
        userResource.update(user);
    }

    public void disableUser(DisableKeycloakUserRequest request) {
        var userResource = keycloak.realm(request.realmName())
                .users()
                .get(request.userId().toString());
        var user = userResource.toRepresentation();
        user.setEnabled(false);
        userResource.update(user);
    }
}
