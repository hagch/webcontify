package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminServiceTest {

    @Mock private Keycloak keycloak;
    @Mock private RealmsResource realmsResource;
    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;
    @Mock private UserResource userResource;

    @InjectMocks
    private KeycloakAdminService keycloakAdminService;

    @Test
    void createRealm_shouldCreateRealmWithCorrectSettings() {
        when(keycloak.realms()).thenReturn(realmsResource);
        var smtp = new SmtpConfig("localhost", 1025, "", "", false);
        var request = new CreateRealmRequest("acme", smtp);
        var result = keycloakAdminService.createRealm(request);
        assertThat(result.realmName()).isEqualTo("tenant-acme");

        var realmCaptor = ArgumentCaptor.forClass(RealmRepresentation.class);
        verify(realmsResource).create(realmCaptor.capture());
        var realm = realmCaptor.getValue();
        assertThat(realm.getRealm()).isEqualTo("tenant-acme");
        assertThat(realm.isEnabled()).isTrue();
        assertThat(realm.isRegistrationAllowed()).isFalse();
        assertThat(realm.isResetPasswordAllowed()).isTrue();
        assertThat(realm.getSmtpServer()).containsEntry("host", "localhost");
        assertThat(realm.getClients()).hasSize(1);
        assertThat(realm.getClients().get(0).getClientId()).isEqualTo("tenant-ui");
        assertThat(realm.getClients().get(0).isPublicClient()).isTrue();
    }

    @Test
    void createUser_shouldCreateUserWithUpdatePasswordAction() {
        when(keycloak.realm("tenant-acme")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        var userId = UUID.randomUUID();
        var response = mock(Response.class);
        when(response.getLocation()).thenReturn(URI.create("/users/" + userId));
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);

        var request = new CreateKeycloakUserRequest("tenant-acme", "alice@example.com", "Alice", "Smith");
        var result = keycloakAdminService.createUser(request);
        assertThat(result.keycloakUserId()).isEqualTo(userId);

        var userCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(userCaptor.capture());
        var user = userCaptor.getValue();
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getFirstName()).isEqualTo("Alice");
        assertThat(user.getLastName()).isEqualTo("Smith");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getRequiredActions()).contains("UPDATE_PASSWORD");
    }

    @Test
    void sendInviteEmail_shouldTriggerExecuteActionsEmail() {
        var userId = UUID.randomUUID();
        when(keycloak.realm("tenant-acme")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(userId.toString())).thenReturn(userResource);
        keycloakAdminService.sendInviteEmail(new SendInviteEmailRequest("tenant-acme", userId));
        verify(userResource).executeActionsEmail(List.of("UPDATE_PASSWORD"));
    }
}
