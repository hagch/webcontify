package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private OpenFgaService openFgaService;
    @Mock
    private TenantInfrastructureLookupService lookupService;

    @InjectMocks
    private PermissionService permissionService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String STORE_ID = "store-123";

    private void setupTenantContext(org.mockito.MockedStatic<TenantContext> mockedContext) {
        mockedContext.when(TenantContext::getTenantInformation)
            .thenReturn(new TenantInformation(TENANT_ID, false));
        when(lookupService.getInfrastructure(TENANT_ID))
            .thenReturn(new TenantInfrastructureInfo(STORE_ID, "tenant-acme"));
    }

    @Test
    void check_shouldDelegateToOpenFgaWithResolvedStoreId() {
        try (var mockedContext = mockStatic(TenantContext.class)) {
            setupTenantContext(mockedContext);
            var userId = UUID.randomUUID();
            when(openFgaService.check(any(OpenFgaCheckRequest.class))).thenReturn(true);

            var result = permissionService.check(
                new CheckPermissionRequest(userId, "reader", "user_management", null));

            assertThat(result).isTrue();
            verify(openFgaService).check(argThat(req ->
                req.storeId().equals(STORE_ID) && req.userId().equals(userId)));
        }
    }

    @Test
    void grant_shouldWriteTupleViaOpenFga() {
        try (var mockedContext = mockStatic(TenantContext.class)) {
            setupTenantContext(mockedContext);
            var userId = UUID.randomUUID();
            var resourceId = UUID.randomUUID();

            permissionService.grant(
                new GrantPermissionRequest(userId, "admin", "tenant", resourceId));

            verify(openFgaService).write(argThat(req ->
                req.storeId().equals(STORE_ID) &&
                req.tuples().get(0).user().equals("user:" + userId)));
        }
    }

    @Test
    void revoke_shouldDeleteTupleViaOpenFga() {
        try (var mockedContext = mockStatic(TenantContext.class)) {
            setupTenantContext(mockedContext);
            var userId = UUID.randomUUID();
            var resourceId = UUID.randomUUID();

            permissionService.revoke(
                new RevokePermissionRequest(userId, "admin", "tenant", resourceId));

            verify(openFgaService).delete(argThat(req ->
                req.storeId().equals(STORE_ID)));
        }
    }

    @Test
    void listObjects_shouldDelegateAndMapToUUIDs() {
        try (var mockedContext = mockStatic(TenantContext.class)) {
            setupTenantContext(mockedContext);
            var userId = UUID.randomUUID();
            var objectId = UUID.randomUUID();
            when(openFgaService.listObjects(any(OpenFgaListObjectsRequest.class)))
                .thenReturn(List.of("user_management:" + objectId));

            var result = permissionService.listObjects(
                new ListPermittedObjectsRequest(userId, "reader", "user_management"));

            assertThat(result).containsExactly(objectId);
        }
    }
}
