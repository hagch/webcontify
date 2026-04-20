package io.webcontify.identity.api;

import io.webcontify.generated.api.PlatformAdminsApi;
import io.webcontify.generated.model.InvitePlatformAdminRequest;
import io.webcontify.generated.model.PlatformAdminListResponse;
import io.webcontify.generated.model.PlatformAdminResponse;
import io.webcontify.generated.model.UpdatePlatformAdminStatusRequest;
import io.webcontify.identity.service.PlatformAdminService;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
public class PlatformAdminController implements PlatformAdminsApi {

    private final PlatformAdminService platformAdminService;

    public PlatformAdminController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @Override
    public ResponseEntity<PlatformAdminResponse> invitePlatformAdmin(InvitePlatformAdminRequest request) {
        UUID adminId = platformAdminService.invite(request.getEmail(), request.getFirstName(), request.getLastName());
        var user = platformAdminService.getById(adminId);
        var response = mapToResponse(user);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(adminId)
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Override
    public ResponseEntity<PlatformAdminListResponse> listPlatformAdmins() {
        var admins = platformAdminService.list().stream()
                .map(this::mapToResponse)
                .toList();

        var response = new PlatformAdminListResponse();
        response.setAdmins(admins);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PlatformAdminResponse> getPlatformAdmin(UUID adminId) {
        var user = platformAdminService.getById(adminId);
        return ResponseEntity.ok(mapToResponse(user));
    }

    @Override
    public ResponseEntity<PlatformAdminResponse> updatePlatformAdminStatus(
            UUID adminId,
            UpdatePlatformAdminStatusRequest request) {
        platformAdminService.updateStatus(adminId, request.getEnabled());
        var user = platformAdminService.getById(adminId);
        return ResponseEntity.ok(mapToResponse(user));
    }

    @Override
    public ResponseEntity<Void> deletePlatformAdmin(UUID adminId) {
        platformAdminService.delete(adminId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resetPlatformAdminPassword(UUID adminId) {
        platformAdminService.resetPassword(adminId);
        return ResponseEntity.noContent().build();
    }

    private PlatformAdminResponse mapToResponse(UserRepresentation user) {
        var response = new PlatformAdminResponse();
        response.setId(UUID.fromString(user.getId()));
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEnabled(user.isEnabled());
        Long createdTimestamp = user.getCreatedTimestamp();
        response.setCreatedAt(createdTimestamp != null
                ? Instant.ofEpochMilli(createdTimestamp).atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now(ZoneOffset.UTC));
        return response;
    }
}
