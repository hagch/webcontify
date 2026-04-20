package io.webcontify.tenant.api;

import io.webcontify.generated.api.PlatformAdminApi;
import io.webcontify.generated.model.*;
import io.webcontify.tenant.mapper.TenantMapper;
import io.webcontify.tenant.model.Tenant;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.service.TenantService;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

// TODO: Add @PreAuthorize("hasRole('PLATFORM_ADMIN')") when Keycloak is integrated (Phase 2)
@RestController
public class TenantAdminController implements PlatformAdminApi {

    private final TenantService tenantService;
    private final TenantMapper tenantMapper;

    public TenantAdminController(TenantService tenantService, TenantMapper tenantMapper) {
        this.tenantService = tenantService;
        this.tenantMapper = tenantMapper;
    }

    @Override
    public ResponseEntity<TenantResponse> createTenant(CreateTenantRequest createTenantRequest) {
        Tenant tenant = tenantMapper.toDomain(createTenantRequest);
        Tenant created = tenantService.create(tenant);
        TenantResponse response = tenantMapper.toResponse(created);
        return ResponseEntity.created(URI.create("/api/v1/admin/tenants/" + created.getId()))
                .body(response);
    }

    @Override
    public ResponseEntity<TenantListResponse> listTenants(
            Integer page,
            Integer size,
            @Nullable io.webcontify.generated.model.TenantStatus status) {
        TenantStatus domainStatus = status != null ? TenantStatus.valueOf(status.getValue()) : null;
        Page<Tenant> result = tenantService.list(PageRequest.of(page, size), domainStatus);

        TenantListResponse response = new TenantListResponse();
        response.setItems(result.getContent().stream().map(tenantMapper::toResponse).toList());

        PageInfo pageInfo = new PageInfo();
        pageInfo.setPage(result.getNumber());
        pageInfo.setSize(result.getSize());
        pageInfo.setTotalElements(result.getTotalElements());
        pageInfo.setTotalPages(result.getTotalPages());
        response.setPageInfo(pageInfo);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<TenantResponse> getTenant(UUID tenantId) {
        Tenant tenant = tenantService.getById(tenantId);
        return ResponseEntity.ok(tenantMapper.toResponse(tenant));
    }

    @Override
    public ResponseEntity<TenantResponse> updateTenant(
            UUID tenantId, UpdateTenantRequest updateTenantRequest) {
        Tenant update = tenantMapper.toDomain(updateTenantRequest);
        Tenant updated = tenantService.update(tenantId, update);
        return ResponseEntity.ok(tenantMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<TenantResponse> updateTenantStatus(
            UUID tenantId, UpdateTenantStatusRequest updateTenantStatusRequest) {
        TenantStatus status =
                TenantStatus.valueOf(updateTenantStatusRequest.getStatus().getValue());
        Tenant updated = tenantService.updateStatus(tenantId, status);
        return ResponseEntity.ok(tenantMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<Void> deleteTenant(UUID tenantId) {
        tenantService.delete(tenantId);
        return ResponseEntity.noContent().build();
    }
}
