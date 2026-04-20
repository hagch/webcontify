package io.webcontify.identity.service;

import io.webcontify.identity.model.OpenFgaBatchCheckRequest;
import io.webcontify.identity.model.OpenFgaBatchCheckResponse;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private KeycloakAdminService keycloakAdminService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private TenantInfrastructureLookupService tenantInfrastructureLookupService;

    @Mock
    private OpenFgaService openFgaService;

    @InjectMocks
    private AuthService authService;

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // -----------------------------------------------------------------------
    // Helper: set up JWT auth (platform admin path)
    // -----------------------------------------------------------------------
    private void withJwtAuth(UUID userId, String realm) {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(userId.toString());
        try {
            when(jwt.getIssuer()).thenReturn(new java.net.URL("http://auth.localhost/realms/" + realm));
        } catch (java.net.MalformedURLException e) {
            throw new RuntimeException(e);
        }
        var auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // -----------------------------------------------------------------------
    // Helper: set up TenantAwareAuthentication (tenant user or platform admin)
    // -----------------------------------------------------------------------
    private void withTenantAuth(UUID userId, boolean platformAdmin) {
        var tenantInfo = platformAdmin
                ? new TenantInformation(null, true)
                : new TenantInformation(TENANT_ID, false);
        var auth = new TenantAwareAuthentication(tenantInfo, userId.toString(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // -----------------------------------------------------------------------
    // updateProfile
    // -----------------------------------------------------------------------
    @Nested
    class UpdateProfile {

        @Test
        void shouldUpdateFirstAndLastNameInKeycloak() {
            withJwtAuth(USER_ID, "tenant-acme");

            var updatedUser = new UserRepresentation();
            updatedUser.setId(USER_ID.toString());
            updatedUser.setEmail("user@example.com");
            updatedUser.setFirstName("Alice");
            updatedUser.setLastName("Smith");
            when(keycloakAdminService.getUserById("tenant-acme", USER_ID)).thenReturn(updatedUser);

            var result = authService.updateProfile("Alice", "Smith");

            verify(keycloakAdminService).updateUser(
                    argThat(r -> r.realmName().equals("tenant-acme")
                            && r.userId().equals(USER_ID)
                            && r.firstName().equals("Alice")
                            && r.lastName().equals("Smith")));

            assertThat(result.getFirstName()).isEqualTo("Alice");
            assertThat(result.getLastName()).isEqualTo("Smith");
        }
    }

    // -----------------------------------------------------------------------
    // uploadAvatar
    // -----------------------------------------------------------------------
    @Nested
    class UploadAvatar {

        @Test
        void shouldUploadJpegAvatarForPlatformAdmin() throws IOException {
            withTenantAuth(USER_ID, true);

            // JPEG magic bytes
            byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
            var file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", content);

            when(fileStorageService.uploadPrivateAsset(
                    eq("platform"), eq("profiles/" + USER_ID + ".jpg"),
                    any(InputStream.class), eq("image/jpeg")))
                    .thenReturn("files/profiles/" + USER_ID + ".jpg");
            when(fileStorageService.getPresignedUrl(eq("platform"),
                    eq("files/profiles/" + USER_ID + ".jpg"), any(Duration.class)))
                    .thenReturn("https://s3.example.com/signed-url");

            // User has no existing avatar
            var userRep = new UserRepresentation();
            userRep.setId(USER_ID.toString());
            userRep.setAttributes(null);
            when(keycloakAdminService.getUserById("webcontify-platform", USER_ID)).thenReturn(userRep);

            var result = authService.uploadAvatar(file);

            assertThat(result).isEqualTo("https://s3.example.com/signed-url");

            var attrCaptor = ArgumentCaptor.forClass(Map.class);
            verify(keycloakAdminService).updateUserAttributes(
                    eq("webcontify-platform"), eq(USER_ID), attrCaptor.capture());
            @SuppressWarnings("unchecked")
            Map<String, List<String>> attrs = attrCaptor.getValue();
            assertThat(attrs).containsKey("avatar_key");
            assertThat(attrs.get("avatar_key")).containsExactly("files/profiles/" + USER_ID + ".jpg");
        }

        @Test
        void shouldUploadPngAvatarForTenantUser() throws IOException {
            withTenantAuth(USER_ID, false);

            // PNG magic bytes
            byte[] content = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};
            var file = new MockMultipartFile("file", "avatar.png", "image/png", content);

            when(tenantInfrastructureLookupService.getInfrastructure(TENANT_ID))
                    .thenReturn(new io.webcontify.tenant.model.TenantInfrastructureInfo("store-1", "tenant-acme"));
            when(fileStorageService.uploadPrivateAsset(
                    eq("tenant-acme"), eq("profiles/" + USER_ID + ".png"),
                    any(InputStream.class), eq("image/png")))
                    .thenReturn("files/profiles/" + USER_ID + ".png");
            when(fileStorageService.getPresignedUrl(eq("tenant-acme"),
                    eq("files/profiles/" + USER_ID + ".png"), any(Duration.class)))
                    .thenReturn("https://s3.example.com/signed-tenant-url");

            var userRep = new UserRepresentation();
            userRep.setId(USER_ID.toString());
            userRep.setAttributes(null);
            when(keycloakAdminService.getUserById("tenant-acme", USER_ID)).thenReturn(userRep);

            var result = authService.uploadAvatar(file);

            assertThat(result).isEqualTo("https://s3.example.com/signed-tenant-url");
        }

        @Test
        void shouldRejectFileLargerThan2MB() {
            withTenantAuth(USER_ID, true);

            byte[] oversized = new byte[2 * 1024 * 1024 + 1];
            oversized[0] = (byte) 0xFF;
            oversized[1] = (byte) 0xD8;
            oversized[2] = (byte) 0xFF;
            var file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", oversized);

            assertThatThrownBy(() -> authService.uploadAvatar(file))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void shouldRejectUnsupportedContentType() {
            withTenantAuth(USER_ID, true);

            byte[] content = "not-an-image".getBytes();
            var file = new MockMultipartFile("file", "file.gif", "image/gif", content);

            assertThatThrownBy(() -> authService.uploadAvatar(file))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void shouldRejectFileWithInvalidMagicBytes() {
            withTenantAuth(USER_ID, true);

            // Content type says JPEG but magic bytes are wrong
            byte[] content = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};
            var file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", content);

            assertThatThrownBy(() -> authService.uploadAvatar(file))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void shouldDeleteOldAvatarBeforeUploadingNew() throws IOException {
            withTenantAuth(USER_ID, true);

            byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00};
            var file = new MockMultipartFile("file", "new.jpg", "image/jpeg", content);

            var userRep = new UserRepresentation();
            userRep.setId(USER_ID.toString());
            userRep.setAttributes(Map.of("avatar_key", List.of("files/profiles/old-key.jpg")));
            when(keycloakAdminService.getUserById("webcontify-platform", USER_ID)).thenReturn(userRep);
            when(fileStorageService.uploadPrivateAsset(any(), any(), any(), any()))
                    .thenReturn("files/profiles/" + USER_ID + ".jpg");
            when(fileStorageService.getPresignedUrl(any(), any(), any()))
                    .thenReturn("https://s3.example.com/new-url");

            authService.uploadAvatar(file);

            verify(fileStorageService).deleteAsset("platform", "files/profiles/old-key.jpg");
        }
    }

    // -----------------------------------------------------------------------
    // deleteAvatar
    // -----------------------------------------------------------------------
    @Nested
    class DeleteAvatar {

        @Test
        void shouldDeleteAvatarAndClearKeycloakAttribute() throws IOException {
            withTenantAuth(USER_ID, true);

            var userRep = new UserRepresentation();
            userRep.setId(USER_ID.toString());
            userRep.setAttributes(Map.of("avatar_key", List.of("files/profiles/" + USER_ID + ".jpg")));
            when(keycloakAdminService.getUserById("webcontify-platform", USER_ID)).thenReturn(userRep);

            authService.deleteAvatar();

            verify(fileStorageService).deleteAsset("platform", "files/profiles/" + USER_ID + ".jpg");
            var attrCaptor = ArgumentCaptor.forClass(Map.class);
            verify(keycloakAdminService).updateUserAttributes(
                    eq("webcontify-platform"), eq(USER_ID), attrCaptor.capture());
            @SuppressWarnings("unchecked")
            Map<String, List<String>> attrs = attrCaptor.getValue();
            assertThat(attrs.get("avatar_key")).isEmpty();
        }

        @Test
        void shouldDoNothingWhenNoAvatarIsSet() throws IOException {
            withTenantAuth(USER_ID, true);

            var userRep = new UserRepresentation();
            userRep.setId(USER_ID.toString());
            userRep.setAttributes(null);
            when(keycloakAdminService.getUserById("webcontify-platform", USER_ID)).thenReturn(userRep);

            authService.deleteAvatar();

            verify(fileStorageService, never()).deleteAsset(any(), any());
        }
    }

    // -----------------------------------------------------------------------
    // getAvatarRedirectUrl
    // -----------------------------------------------------------------------
    @Nested
    class GetAvatarRedirectUrl {

        @Test
        void shouldReturnPresignedUrlForExistingAvatar() {
            withTenantAuth(USER_ID, true);

            var userRep = new UserRepresentation();
            userRep.setId(USER_ID.toString());
            userRep.setAttributes(Map.of("avatar_key", List.of("files/profiles/" + USER_ID + ".jpg")));
            when(keycloakAdminService.getUserById("webcontify-platform", USER_ID)).thenReturn(userRep);
            when(fileStorageService.getPresignedUrl(eq("platform"),
                    eq("files/profiles/" + USER_ID + ".jpg"), any(Duration.class)))
                    .thenReturn("https://s3.example.com/presigned");

            var result = authService.getAvatarRedirectUrl();

            assertThat(result).isEqualTo("https://s3.example.com/presigned");
        }

        @Test
        void shouldThrowNotFoundWhenNoAvatarIsSet() {
            withTenantAuth(USER_ID, true);

            var userRep = new UserRepresentation();
            userRep.setId(USER_ID.toString());
            userRep.setAttributes(null);
            when(keycloakAdminService.getUserById("webcontify-platform", USER_ID)).thenReturn(userRep);

            assertThatThrownBy(() -> authService.getAvatarRedirectUrl())
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // -----------------------------------------------------------------------
    // getPreferences
    // -----------------------------------------------------------------------
    @Nested
    class GetPreferences {

        @Test
        void shouldReturnDefaultsWhenNoAttributesSet() {
            withTenantAuth(USER_ID, true);

            var userRep = new UserRepresentation();
            userRep.setId(USER_ID.toString());
            userRep.setAttributes(null);
            when(keycloakAdminService.getUserById("webcontify-platform", USER_ID)).thenReturn(userRep);

            var result = authService.getPreferences();

            assertThat(result.theme()).isEqualTo("system");
            assertThat(result.locale()).isEqualTo("en");
        }

        @Test
        void shouldReturnStoredPreferences() {
            withTenantAuth(USER_ID, true);

            var userRep = new UserRepresentation();
            userRep.setId(USER_ID.toString());
            userRep.setAttributes(Map.of(
                    "preferences_theme", List.of("dark"),
                    "preferences_locale", List.of("de")));
            when(keycloakAdminService.getUserById("webcontify-platform", USER_ID)).thenReturn(userRep);

            var result = authService.getPreferences();

            assertThat(result.theme()).isEqualTo("dark");
            assertThat(result.locale()).isEqualTo("de");
        }
    }

    // -----------------------------------------------------------------------
    // updatePreferences
    // -----------------------------------------------------------------------
    @Nested
    class UpdatePreferences {

        @Test
        void shouldWritePreferenceAttributesToKeycloak() {
            withTenantAuth(USER_ID, true);

            var result = authService.updatePreferences("dark", "de");

            var attrCaptor = ArgumentCaptor.forClass(Map.class);
            verify(keycloakAdminService).updateUserAttributes(
                    eq("webcontify-platform"), eq(USER_ID), attrCaptor.capture());
            @SuppressWarnings("unchecked")
            Map<String, List<String>> attrs = attrCaptor.getValue();
            assertThat(attrs.get("preferences_theme")).containsExactly("dark");
            assertThat(attrs.get("preferences_locale")).containsExactly("de");

            assertThat(result.theme()).isEqualTo("dark");
            assertThat(result.locale()).isEqualTo("de");
        }

        @Test
        void shouldPreserveExistingPreferenceWhenOnlyOneFieldProvided() {
            withTenantAuth(USER_ID, true);

            var result = authService.updatePreferences("light", null);

            var attrCaptor = ArgumentCaptor.forClass(Map.class);
            verify(keycloakAdminService).updateUserAttributes(
                    eq("webcontify-platform"), eq(USER_ID), attrCaptor.capture());
            @SuppressWarnings("unchecked")
            Map<String, List<String>> attrs = attrCaptor.getValue();
            assertThat(attrs).containsKey("preferences_theme");
            assertThat(attrs).doesNotContainKey("preferences_locale");

            assertThat(result.theme()).isEqualTo("light");
        }
    }

    // -----------------------------------------------------------------------
    // getEffectivePermissions
    // -----------------------------------------------------------------------
    @Nested
    class GetEffectivePermissions {

        @Test
        void shouldReturnAllPermissionsForPlatformAdmin() {
            withTenantAuth(USER_ID, true);

            var result = authService.getEffectivePermissions("navigation");

            assertThat(result.tenantAdmin()).isTrue();
            assertThat(result.permissions()).containsKey("app_management");
            assertThat(result.permissions().get("app_management"))
                    .containsExactlyInAnyOrder("viewer", "editor", "admin");
            assertThat(result.permissions()).containsKey("user_management");
            assertThat(result.permissions()).containsKey("role_management");
            assertThat(result.permissions()).containsKey("tenant_config");
        }

        @Test
        void shouldIncludeAppManagementInBatchCheckForTenantUser() {
            withTenantAuth(USER_ID, false);

            when(tenantInfrastructureLookupService.getInfrastructure(TENANT_ID))
                    .thenReturn(new TenantInfrastructureInfo("store-1", "tenant-acme"));

            // Build response: grant app_management viewer + editor
            var results = new java.util.HashMap<String, Boolean>();
            results.put("viewer:app_management", true);
            results.put("editor:app_management", true);
            results.put("admin:app_management", false);
            results.put("admin:tenant", false);
            // All other permissions denied
            results.put("inviter:user_management", false);
            results.put("editor:user_management", false);
            results.put("deleter:user_management", false);
            results.put("reader:user_management", false);
            results.put("creator:role_management", false);
            results.put("editor:role_management", false);
            results.put("deleter:role_management", false);
            results.put("reader:role_management", false);
            results.put("editor:tenant_config", false);
            results.put("reader:tenant_config", false);

            when(openFgaService.batchCheck(any(OpenFgaBatchCheckRequest.class)))
                    .thenReturn(new OpenFgaBatchCheckResponse(results));

            var result = authService.getEffectivePermissions("navigation");

            assertThat(result.tenantAdmin()).isFalse();
            assertThat(result.permissions()).containsKey("app_management");
            assertThat(result.permissions().get("app_management"))
                    .containsExactlyInAnyOrder("viewer", "editor");
            assertThat(result.permissions()).doesNotContainKey("user_management");
        }
    }
}
