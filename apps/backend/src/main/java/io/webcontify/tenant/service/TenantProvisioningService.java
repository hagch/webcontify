package io.webcontify.tenant.service;

import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.tenant.model.Tenant;
import io.webcontify.tenant.model.TenantStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestriert die vollstaendige Bereitstellung eines neuen Tenants:
 * 1. DB-Eintrag anlegen (PROVISIONING)
 * 2. S3-Bucket erstellen
 * 3. Status auf ACTIVE setzen
 *
 * Bei Bucket-Fehlern bleibt der Tenant PROVISIONING und ein WARN wird geloggt.
 * Kein Fehler wird nach aussen weitergegeben — der Tenant kann spaeter neu provisioniert werden.
 *
 * @deprecated Wird in Task 20 durch TenantProvisioningListener ersetzt.
 *             Diese Klasse wird vorerst als Basis fuer den Listener behalten.
 */
@Service
public class TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

    private final TenantService tenantService;
    private final FileStorageService fileStorageService;

    public TenantProvisioningService(TenantService tenantService, FileStorageService fileStorageService) {
        this.tenantService = tenantService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Erstellt einen neuen Tenant und richtet alle notwendigen Ressourcen ein.
     *
     * @param tenant         Tenant-Daten (ohne ID und Status)
     * @param adminEmail     E-Mail des initialen Admin-Users
     * @param adminFirstName Vorname des initialen Admin-Users
     * @param adminLastName  Nachname des initialen Admin-Users
     * @return erstellter Tenant — ACTIVE bei Erfolg, PROVISIONING bei Bucket-Fehler
     */
    public Tenant provision(Tenant tenant, String adminEmail, String adminFirstName, String adminLastName) {
        Tenant created = tenantService.create(tenant, adminEmail, adminFirstName, adminLastName);
        String bucketName = "tenant-" + created.getSlug();

        try {
            fileStorageService.createBucket(bucketName);
            Tenant activated = tenantService.updateStatus(created.getId(), TenantStatus.ACTIVE);
            log.info(
                    "Tenant erfolgreich bereitgestellt. tenantId={} slug={} bucket={}",
                    created.getId(),
                    created.getSlug(),
                    bucketName);
            return activated;
        } catch (Exception e) {
            log.warn(
                    "S3-Bucket konnte nicht erstellt werden — Tenant bleibt PROVISIONING. tenantId={} slug={} bucket={} fehler={}",
                    created.getId(),
                    created.getSlug(),
                    bucketName,
                    e.getMessage());
            return created;
        }
    }
}
