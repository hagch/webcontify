package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import io.webcontify.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final OpenFgaService openFgaService;
    private final TenantInfrastructureLookupService lookupService;

    public PermissionService(OpenFgaService openFgaService,
                             TenantInfrastructureLookupService lookupService) {
        this.openFgaService = openFgaService;
        this.lookupService = lookupService;
    }

    public boolean check(CheckPermissionRequest request) {
        var storeId = resolveStoreId();
        log.debug("Checking permission userId={} action={} resourceType={} resourceId={}",
            request.userId(), request.action(), request.resourceType(), request.resourceId());
        return openFgaService.check(new OpenFgaCheckRequest(
            storeId, request.userId(), request.action(),
            request.resourceType(), request.resourceId()
        ));
    }

    public void grant(GrantPermissionRequest request) {
        var storeId = resolveStoreId();
        log.info("Granting permission userId={} relation={} resourceType={} resourceId={}",
            request.userId(), request.relation(), request.resourceType(), request.resourceId());
        var tuple = new RelationshipTuple(
            "user:" + request.userId(),
            request.relation(),
            request.resourceType() + ":" + request.resourceId()
        );
        openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(tuple)));
    }

    public void revoke(RevokePermissionRequest request) {
        var storeId = resolveStoreId();
        log.info("Revoking permission userId={} relation={} resourceType={} resourceId={}",
            request.userId(), request.relation(), request.resourceType(), request.resourceId());
        var tuple = new RelationshipTuple(
            "user:" + request.userId(),
            request.relation(),
            request.resourceType() + ":" + request.resourceId()
        );
        openFgaService.delete(new OpenFgaDeleteRequest(storeId, List.of(tuple)));
    }

    public List<UUID> listObjects(ListPermittedObjectsRequest request) {
        var storeId = resolveStoreId();
        log.debug("Listing permitted objects userId={} action={} resourceType={}",
            request.userId(), request.action(), request.resourceType());
        var objects = openFgaService.listObjects(new OpenFgaListObjectsRequest(
            storeId, request.userId(), request.action(), request.resourceType()
        ));
        return objects.stream()
            .map(obj -> UUID.fromString(obj.split(":")[1]))
            .toList();
    }

    private String resolveStoreId() {
        var tenantInfo = TenantContext.getTenantInformation();
        return lookupService.getInfrastructure(tenantInfo.tenantId()).openfgaStoreId();
    }
}
