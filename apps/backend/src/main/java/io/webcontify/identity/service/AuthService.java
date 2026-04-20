package io.webcontify.identity.service;

import io.webcontify.identity.model.EffectivePermissions;
import io.webcontify.identity.model.OpenFgaBatchCheckRequest;
import io.webcontify.identity.model.OpenFgaCheckRequest;
import io.webcontify.identity.model.UpdateKeycloakUserRequest;
import io.webcontify.identity.model.UserPreferences;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.shared.security.TenantContext;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String PLATFORM_REALM = "webcontify-platform";
    private static final String AVATAR_KEY_ATTR = "avatar_key";
    private static final String PREF_THEME_ATTR = "preferences_theme";
    private static final String PREF_LOCALE_ATTR = "preferences_locale";
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final Duration AVATAR_URL_TTL = Duration.ofMinutes(15);

    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final Map<String, List<String>> NAVIGATION_RESOURCE_ACTIONS = Map.of(
            "user_management", List.of("inviter", "editor", "deleter", "reader"),
            "role_management", List.of("creator", "editor", "deleter", "reader"),
            "tenant_config", List.of("editor", "reader")
    );

    private final KeycloakAdminService keycloakAdminService;
    private final FileStorageService fileStorageService;
    private final TenantInfrastructureLookupService tenantInfrastructureLookupService;
    private final OpenFgaService openFgaService;
    private final String keycloakBaseUrl;

    public AuthService(
            KeycloakAdminService keycloakAdminService,
            FileStorageService fileStorageService,
            TenantInfrastructureLookupService tenantInfrastructureLookupService,
            OpenFgaService openFgaService,
            @Value("${keycloak.admin.server-url}") String keycloakBaseUrl) {
        this.keycloakAdminService = keycloakAdminService;
        this.fileStorageService = fileStorageService;
        this.tenantInfrastructureLookupService = tenantInfrastructureLookupService;
        this.openFgaService = openFgaService;
        this.keycloakBaseUrl = keycloakBaseUrl;
    }

    // -----------------------------------------------------------------------
    // Existing methods
    // -----------------------------------------------------------------------

    public UserRepresentation getMe() {
        var realm = extractRealmFromIssuer();
        var userId = resolveCurrentUserId();
        log.debug("Fetching profile for userId={} in realm={}", userId, realm);
        return keycloakAdminService.getUserById(realm, userId);
    }

    public String getAccountUrl() {
        var realm = extractRealmFromIssuer();
        return keycloakBaseUrl + "/realms/" + realm + "/account/#/security/signingin";
    }

    // -----------------------------------------------------------------------
    // updateProfile
    // -----------------------------------------------------------------------

    public UserRepresentation updateProfile(String firstName, String lastName) {
        var realm = resolveCurrentRealm();
        var userId = resolveCurrentUserId();
        var existing = keycloakAdminService.getUserById(realm, userId);
        keycloakAdminService.updateUser(new UpdateKeycloakUserRequest(
                realm, userId, existing.getEmail(), firstName, lastName));
        log.info("Updated profile for userId={} in realm={}", userId, realm);
        return keycloakAdminService.getUserById(realm, userId);
    }

    // -----------------------------------------------------------------------
    // uploadAvatar
    // -----------------------------------------------------------------------

    public String uploadAvatar(MultipartFile file) {
        validateAvatarFile(file);

        var userId = resolveCurrentUserId();
        var realm = resolveCurrentRealm();
        var bucket = determineBucket();
        var extension = resolveExtension(file.getContentType());

        // Delete old avatar if present
        var user = keycloakAdminService.getUserById(realm, userId);
        var oldKey = getAttributeValue(user, AVATAR_KEY_ATTR);
        if (oldKey != null && !oldKey.isBlank()) {
            log.debug("Deleting old avatar: bucket={}, key={}", bucket, oldKey);
            fileStorageService.deleteAsset(bucket, oldKey);
        }

        // Upload new avatar — uploadPrivateAsset prepends "files/" and returns full key
        String actualKey;
        try {
            actualKey = fileStorageService.uploadPrivateAsset(bucket, "profiles/" + userId + extension,
                    file.getInputStream(), file.getContentType());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read avatar input stream", e);
        }

        // Store key in Keycloak
        keycloakAdminService.updateUserAttributes(realm, userId,
                Map.of(AVATAR_KEY_ATTR, List.of(actualKey)));

        log.info("Uploaded avatar for userId={} to bucket={}, key={}", userId, bucket, actualKey);
        return fileStorageService.getPresignedUrl(bucket, actualKey, AVATAR_URL_TTL);
    }

    // -----------------------------------------------------------------------
    // deleteAvatar
    // -----------------------------------------------------------------------

    public void deleteAvatar() {
        var userId = resolveCurrentUserId();
        var realm = resolveCurrentRealm();
        var bucket = determineBucket();

        var user = keycloakAdminService.getUserById(realm, userId);
        var avatarKey = getAttributeValue(user, AVATAR_KEY_ATTR);
        if (avatarKey == null || avatarKey.isBlank()) {
            log.debug("No avatar to delete for userId={}", userId);
            return;
        }

        fileStorageService.deleteAsset(bucket, avatarKey);

        // Clear avatar_key attribute
        keycloakAdminService.updateUserAttributes(realm, userId,
                Map.of(AVATAR_KEY_ATTR, Collections.emptyList()));

        log.info("Deleted avatar for userId={}", userId);
    }

    // -----------------------------------------------------------------------
    // getAvatarRedirectUrl
    // -----------------------------------------------------------------------

    public String getAvatarRedirectUrl() {
        var userId = resolveCurrentUserId();
        var realm = resolveCurrentRealm();
        var bucket = determineBucket();

        var user = keycloakAdminService.getUserById(realm, userId);
        var avatarKey = getAttributeValue(user, AVATAR_KEY_ATTR);
        if (avatarKey == null || avatarKey.isBlank()) {
            throw new NotFoundException("auth.avatar.not_found");
        }

        return fileStorageService.getPresignedUrl(bucket, avatarKey, AVATAR_URL_TTL);
    }

    // -----------------------------------------------------------------------
    // getPreferences
    // -----------------------------------------------------------------------

    public UserPreferences getPreferences() {
        var userId = resolveCurrentUserId();
        var realm = resolveCurrentRealm();

        var user = keycloakAdminService.getUserById(realm, userId);
        var theme = getAttributeValueOrDefault(user, PREF_THEME_ATTR, "system");
        var locale = getAttributeValueOrDefault(user, PREF_LOCALE_ATTR, "en");

        return new UserPreferences(theme, locale);
    }

    // -----------------------------------------------------------------------
    // updatePreferences
    // -----------------------------------------------------------------------

    public UserPreferences updatePreferences(String theme, String locale) {
        var userId = resolveCurrentUserId();
        var realm = resolveCurrentRealm();

        var attrs = new HashMap<String, List<String>>();
        if (theme != null) {
            attrs.put(PREF_THEME_ATTR, List.of(theme));
        }
        if (locale != null) {
            attrs.put(PREF_LOCALE_ATTR, List.of(locale));
        }

        if (!attrs.isEmpty()) {
            keycloakAdminService.updateUserAttributes(realm, userId, attrs);
        }

        return new UserPreferences(
                theme != null ? theme : "system",
                locale != null ? locale : "en");
    }

    // -----------------------------------------------------------------------
    // getEffectivePermissions
    // -----------------------------------------------------------------------

    public EffectivePermissions getEffectivePermissions(String scope) {
        if (scope != null && !scope.equals("navigation")) {
            throw new BadRequestException("auth.invalid_scope");
        }

        var tenantInfo = TenantContext.getTenantInformation();

        if (tenantInfo.platformAdmin()) {
            var allPermissions = NAVIGATION_RESOURCE_ACTIONS.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return new EffectivePermissions(allPermissions, true);
        }

        var infrastructure = tenantInfrastructureLookupService.getInfrastructure(tenantInfo.tenantId());
        var userId = resolveCurrentUserId();

        var checks = new ArrayList<OpenFgaCheckRequest>();
        NAVIGATION_RESOURCE_ACTIONS.forEach((resourceType, actions) ->
                actions.forEach(action ->
                        checks.add(new OpenFgaCheckRequest(
                                infrastructure.openfgaStoreId(),
                                userId,
                                action,
                                resourceType,
                                tenantInfo.tenantId()))
                )
        );
        checks.add(new OpenFgaCheckRequest(
                infrastructure.openfgaStoreId(),
                userId,
                "admin",
                "tenant",
                tenantInfo.tenantId()));

        var batchResponse = openFgaService.batchCheck(
                new OpenFgaBatchCheckRequest(infrastructure.openfgaStoreId(), checks));

        var permissions = new HashMap<String, List<String>>();
        NAVIGATION_RESOURCE_ACTIONS.forEach((resourceType, actions) -> {
            var grantedActions = actions.stream()
                    .filter(action -> Boolean.TRUE.equals(
                            batchResponse.results().get(action + ":" + resourceType)))
                    .toList();
            if (!grantedActions.isEmpty()) {
                permissions.put(resourceType, grantedActions);
            }
        });

        boolean isTenantAdmin = Boolean.TRUE.equals(
                batchResponse.results().get("admin:tenant"));

        return new EffectivePermissions(permissions, isTenantAdmin);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String extractRealmFromIssuer() {
        var tenantInfo = TenantContext.getTenantInformation();
        if (tenantInfo.realmName() != null) {
            return tenantInfo.realmName();
        }
        // Fallback: try JWT directly (should not be needed after JwtToTenantAuthenticationConverter fix)
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            var issuer = jwtAuth.getToken().getIssuer().toString();
            return issuer.substring(issuer.lastIndexOf("/") + 1);
        }
        throw new IllegalStateException("Cannot extract realm — no realm name in TenantInformation and no JWT authentication");
    }

    private UUID resolveCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return UUID.fromString(jwtAuth.getToken().getSubject());
        }
        var tenantAuth = (TenantAwareAuthentication) authentication;
        return UUID.fromString((String) tenantAuth.getPrincipal());
    }

    private String resolveCurrentRealm() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            var issuer = jwtAuth.getToken().getIssuer().toString();
            return issuer.substring(issuer.lastIndexOf("/") + 1);
        }
        var tenantAuth = (TenantAwareAuthentication) authentication;
        var tenantInfo = tenantAuth.getTenantInformation();
        if (tenantInfo.platformAdmin()) {
            return PLATFORM_REALM;
        }
        return tenantInfrastructureLookupService
                .getInfrastructure(tenantInfo.tenantId())
                .keycloakRealmName();
    }

    private String determineBucket() {
        var tenantInfo = TenantContext.getTenantInformation();
        if (tenantInfo.platformAdmin()) {
            return "platform";
        }
        return tenantInfrastructureLookupService
                .getInfrastructure(tenantInfo.tenantId())
                .keycloakRealmName();
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("auth.avatar.file_required");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BadRequestException("auth.avatar.file_too_large");
        }

        var contentType = file.getContentType();
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw new BadRequestException("auth.avatar.unsupported_format");
        }

        validateMagicBytes(file, contentType);
    }

    private void validateMagicBytes(MultipartFile file, String contentType) {
        try {
            byte[] header = file.getBytes();
            if ("image/jpeg".equals(contentType)) {
                if (!startsWith(header, JPEG_MAGIC)) {
                    throw new BadRequestException("auth.avatar.invalid_file_content");
                }
            } else if ("image/png".equals(contentType)) {
                if (!startsWith(header, PNG_MAGIC)) {
                    throw new BadRequestException("auth.avatar.invalid_file_content");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file header for magic byte validation", e);
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private String resolveExtension(String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return ".jpg";
        } else if ("image/png".equals(contentType)) {
            return ".png";
        }
        throw new BadRequestException("auth.avatar.unsupported_format");
    }

    private String getAttributeValue(UserRepresentation user, String key) {
        if (user.getAttributes() == null) {
            return null;
        }
        var values = user.getAttributes().get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private String getAttributeValueOrDefault(UserRepresentation user, String key, String defaultValue) {
        var value = getAttributeValue(user, key);
        return value != null ? value : defaultValue;
    }
}
