package io.webcontify.tenant.service;

import io.webcontify.tenant.event.TenantDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class TenantSchemaCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaCleanupListener.class);

    private final TenantSchemaService tenantSchemaService;

    public TenantSchemaCleanupListener(TenantSchemaService tenantSchemaService) {
        this.tenantSchemaService = tenantSchemaService;
    }

    @ApplicationModuleListener
    public void onTenantDeleted(TenantDeletedEvent event) {
        log.info("Dropping schema for deleted tenant slug={}", event.slug());
        tenantSchemaService.dropSchema(event.slug());
    }
}
