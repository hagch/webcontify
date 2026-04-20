package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
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
        realm.setDisplayName(request.displayName());
        realm.setEnabled(true);
        realm.setRegistrationAllowed(false);
        realm.setResetPasswordAllowed(true);
        realm.setLoginWithEmailAllowed(true);
        var smtp = request.platformSmtpConfig();
        realm.setSmtpServer(Map.of(
                "host", smtp.host(),
                "port", String.valueOf(smtp.port()),
                "from", smtp.from(),
                "user", smtp.username(),
                "password", smtp.password(),
                "starttls", String.valueOf(smtp.starttls()),
                "auth", "true"
        ));
        if (request.loginTheme() != null) {
            realm.setLoginTheme(request.loginTheme());
        }
        if (request.emailTheme() != null) {
            realm.setEmailTheme(request.emailTheme());
        }
        if (request.initialAttributes() != null) {
            realm.setAttributes(request.initialAttributes());
        }
        realm.setInternationalizationEnabled(true);
        realm.setDefaultLocale("en");
        realm.setSupportedLocales(java.util.Set.of("en"));
        var tenantIdMapper = new ProtocolMapperRepresentation();
        tenantIdMapper.setName("tenant_id-mapper");
        tenantIdMapper.setProtocol("openid-connect");
        tenantIdMapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
        tenantIdMapper.setConfig(Map.of(
                "claim.name", "tenant_id",
                "claim.value", request.tenantId().toString(),
                "jsonType.label", "String",
                "id.token.claim", "false",
                "access.token.claim", "true",
                "userinfo.token.claim", "false"
        ));
        var client = new ClientRepresentation();
        client.setClientId("tenant-ui");
        client.setPublicClient(true);
        client.setStandardFlowEnabled(true);
        client.setDirectAccessGrantsEnabled(false);
        client.setRedirectUris(List.of("http://" + request.tenantSlug() + ".localhost/*"));
        client.setWebOrigins(List.of("http://" + request.tenantSlug() + ".localhost"));
        client.setAttributes(Map.of("pkce.code.challenge.method", "S256"));
        client.setProtocolMappers(List.of(tenantIdMapper));
        realm.setClients(List.of(client));
        keycloak.realms().create(realm);
        if (request.initialAttributes() != null) {
            updateRealmLocalizationTexts(realmName, request.initialAttributes());
        }
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
        user.setUsername(request.email());
        var response = keycloak.realm(request.realmName()).users().create(user);
        if (response.getStatus() != 201 || response.getLocation() == null) {
            String body = response.readEntity(String.class);
            throw new RuntimeException("Failed to create Keycloak user (status=" + response.getStatus() + "): " + body);
        }
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
                "from", smtp.from(),
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
        var existing = realm.getAttributes() != null
                ? new HashMap<>(realm.getAttributes())
                : new HashMap<String, String>();
        existing.putAll(request.brandingAttributes());
        realm.setAttributes(existing);
        realm.setDisplayName(request.brandingAttributes().getOrDefault("brandTenantName", realm.getDisplayName()));
        realmResource.update(realm);
        updateRealmLocalizationTexts(request.realmName(), request.brandingAttributes());
    }

    public void updateRealmLocalizationTexts(String realmName, Map<String, String> brandingTexts) {
        var localization = keycloak.realm(realmName).localization();
        for (var entry : brandingTexts.entrySet()) {
            localization.saveRealmLocalizationText("en", entry.getKey(), entry.getValue());
        }
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

    public List<UserRepresentation> listUsersWithRole(String realmName, String roleName) {
        return keycloak.realm(realmName)
                .roles()
                .get(roleName)
                .getUserMembers();
    }

    public void assignRealmRole(String realmName, UUID userId, String roleName) {
        var roleRepresentation = keycloak.realm(realmName)
                .roles()
                .get(roleName)
                .toRepresentation();
        keycloak.realm(realmName)
                .users()
                .get(userId.toString())
                .roles()
                .realmLevel()
                .add(List.of(roleRepresentation));
    }

    public UserRepresentation getUserById(String realmName, UUID userId) {
        return keycloak.realm(realmName)
                .users()
                .get(userId.toString())
                .toRepresentation();
    }

    public List<RoleRepresentation> getUserRealmRoles(String realmName, UUID userId) {
        return keycloak.realm(realmName)
                .users()
                .get(userId.toString())
                .roles()
                .realmLevel()
                .listEffective();
    }

    public void addRequiredAction(String realmName, UUID userId, String action) {
        var userResource = keycloak.realm(realmName)
                .users()
                .get(userId.toString());
        var user = userResource.toRepresentation();
        var actions = user.getRequiredActions() != null
                ? new ArrayList<>(user.getRequiredActions())
                : new ArrayList<String>();
        if (!actions.contains(action)) {
            actions.add(action);
        }
        user.setRequiredActions(actions);
        userResource.update(user);
    }
}
