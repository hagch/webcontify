package io.webcontify.identity.api;

import io.webcontify.generated.api.AuthApi;
import io.webcontify.generated.model.AccountUrlResponse;
import io.webcontify.generated.model.AvatarUploadResponse;
import io.webcontify.generated.model.EffectivePermissionsResponse;
import io.webcontify.generated.model.MyProfileResponse;
import io.webcontify.generated.model.UpdateProfileRequest;
import io.webcontify.generated.model.UpdateUserPreferencesRequest;
import io.webcontify.generated.model.UserPreferencesResponse;
import io.webcontify.identity.service.AuthService;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<MyProfileResponse> getMyProfile() {
        var user = authService.getMe();
        var response = mapToProfileResponse(user);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AccountUrlResponse> getAccountUrl() {
        var url = authService.getAccountUrl();
        var response = new AccountUrlResponse();
        response.setUrl(url);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<MyProfileResponse> updateProfile(UpdateProfileRequest updateProfileRequest) {
        var user = authService.updateProfile(
                updateProfileRequest.getFirstName(),
                updateProfileRequest.getLastName());
        return ResponseEntity.ok(mapToProfileResponse(user));
    }

    @Override
    public ResponseEntity<AvatarUploadResponse> uploadAvatar(MultipartFile file) {
        var presignedUrl = authService.uploadAvatar(file);
        var response = new AvatarUploadResponse();
        response.setAvatarUrl(presignedUrl);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteAvatar() {
        authService.deleteAvatar();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AvatarUploadResponse> getAvatar() {
        var presignedUrl = authService.getAvatarRedirectUrl();
        var response = new AvatarUploadResponse();
        response.setAvatarUrl(presignedUrl);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserPreferencesResponse> getPreferences() {
        var prefs = authService.getPreferences();
        return ResponseEntity.ok(mapToPreferencesResponse(prefs));
    }

    @Override
    public ResponseEntity<EffectivePermissionsResponse> getEffectivePermissions(String scope) {
        var result = authService.getEffectivePermissions(scope);
        var response = new EffectivePermissionsResponse();
        response.setPermissions(result.permissions());
        response.setIsTenantAdmin(result.tenantAdmin());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserPreferencesResponse> updatePreferences(
            UpdateUserPreferencesRequest updateUserPreferencesRequest) {
        var theme = updateUserPreferencesRequest.getTheme() != null
                ? updateUserPreferencesRequest.getTheme().getValue()
                : null;
        var locale = updateUserPreferencesRequest.getLocale() != null
                ? updateUserPreferencesRequest.getLocale().getValue()
                : null;
        var prefs = authService.updatePreferences(theme, locale);
        return ResponseEntity.ok(mapToPreferencesResponse(prefs));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private MyProfileResponse mapToProfileResponse(UserRepresentation user) {
        var avatarKey = user.getAttributes() != null
                ? user.getAttributes().get("avatar_key")
                : null;
        boolean hasAvatar = avatarKey != null && !avatarKey.isEmpty()
                && avatarKey.get(0) != null && !avatarKey.get(0).isBlank();

        var response = new MyProfileResponse();
        response.setId(UUID.fromString(user.getId()));
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setHasAvatar(hasAvatar);
        return response;
    }

    private UserPreferencesResponse mapToPreferencesResponse(io.webcontify.identity.model.UserPreferences prefs) {
        var response = new UserPreferencesResponse();
        if (prefs.theme() != null) {
            response.setTheme(UserPreferencesResponse.ThemeEnum.fromValue(prefs.theme()));
        }
        if (prefs.locale() != null) {
            response.setLocale(UserPreferencesResponse.LocaleEnum.fromValue(prefs.locale()));
        }
        return response;
    }
}
