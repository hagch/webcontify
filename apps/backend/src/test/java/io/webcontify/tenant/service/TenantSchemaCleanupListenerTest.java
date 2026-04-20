package io.webcontify.tenant.service;

import static org.mockito.Mockito.verify;

import io.webcontify.tenant.event.TenantDeletedEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantSchemaCleanupListenerTest {

    @Mock
    private TenantSchemaService tenantSchemaService;

    private TenantSchemaCleanupListener listener;

    @BeforeEach
    void setUp() {
        listener = new TenantSchemaCleanupListener(tenantSchemaService);
    }

    @Test
    void shouldDropSchemaOnTenantDeletedEvent() {
        var event = new TenantDeletedEvent(UUID.randomUUID(), "acme-corp");

        listener.onTenantDeleted(event);

        verify(tenantSchemaService).dropSchema("acme-corp");
    }
}
