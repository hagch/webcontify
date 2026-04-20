package io.webcontify.app.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.IntegrationTestBase;
import io.webcontify.app.model.AppSnapshot;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AppImportExportControllerIntegrationTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final UUID TEST_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000299");
    private static final UUID TEST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String TENANT_SLUG = "import-export-test-tenant";

    @BeforeEach
    void setUp() {
        lenient().when(permissionService.check(any())).thenReturn(true);

        when(fileStorageService.uploadPrivateAsset(anyString(), anyString(), any(), anyString()))
                .thenReturn("files/snapshots/test-snapshot.json");

        if (tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_TENANT_ID).isEmpty()) {
            TenantEntity tenant = new TenantEntity();
            tenant.setId(TEST_TENANT_ID);
            tenant.setName("Import Export Test Tenant");
            tenant.setSlug(TENANT_SLUG);
            tenant.setStatus("ACTIVE");

            AuditInfo auditInfo = new AuditInfo();
            auditInfo.setCreatedAt(OffsetDateTime.now());
            auditInfo.setCreatedBy(SystemUser.ID);
            auditInfo.setUpdatedAt(OffsetDateTime.now());
            auditInfo.setUpdatedBy(SystemUser.ID);
            tenant.setAuditInfo(auditInfo);

            tenantRepository.save(tenant);
        }
    }

    @Test
    void shouldExportBlueprintForPublishedVersion() throws Exception {
        String appId = createApp("Export Blueprint App");
        String versionId = publishApp(appId, "0.1.0");

        // Mock the getObject call for snapshot download during export
        AppSnapshot snapshot = new AppSnapshot(
                appId,
                "0.1.0",
                new AppSnapshot.AppSnapshotMetadata("Export Blueprint App", "export-blueprint-app", "Test app"),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        when(fileStorageService.getObject(anyString(), anyString()))
                .thenReturn(new ByteArrayInputStream(snapshotJson.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/apps/" + appId + "/versions/" + versionId + "/export")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportedAt").isNotEmpty())
                .andExpect(jsonPath("$.apps").isArray())
                .andExpect(jsonPath("$.apps.length()").value(1))
                .andExpect(jsonPath("$.apps[0].slug").value("export-blueprint-app"))
                .andExpect(jsonPath("$.apps[0].version").value("0.1.0"))
                .andExpect(jsonPath("$.apps[0].snapshot").isNotEmpty());
    }

    @Test
    void shouldAnalyzeBlueprintAndDetectNoCollisions() throws Exception {
        String blueprintJson = """
                {
                    "exportedAt": "2026-04-04T10:00:00Z",
                    "apps": [
                        {
                            "slug": "unique-app-no-collision",
                            "name": "Unique App No Collision",
                            "description": "A unique app",
                            "version": "0.1.0",
                            "dependencies": [],
                            "snapshot": {
                                "appId": "00000000-0000-0000-0000-000000000001",
                                "version": "0.1.0",
                                "metadata": { "name": "Unique App No Collision", "slug": "unique-app-no-collision", "description": "A unique app" },
                                "dependencies": [],
                                "contentDefinitions": []
                            }
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/apps/import/analyze")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blueprintJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canImport").value(true))
                .andExpect(jsonPath("$.apps.length()").value(1))
                .andExpect(jsonPath("$.apps[0].originalSlug").value("unique-app-no-collision"))
                .andExpect(jsonPath("$.apps[0].hasSlugCollision").value(false));
    }

    @Test
    void shouldAnalyzeBlueprintAndDetectSlugCollision() throws Exception {
        createApp("Collision Detect App");

        String blueprintJson = """
                {
                    "exportedAt": "2026-04-04T10:00:00Z",
                    "apps": [
                        {
                            "slug": "collision-detect-app",
                            "name": "Collision Detect App",
                            "description": "Will collide",
                            "version": "0.1.0",
                            "dependencies": [],
                            "snapshot": {
                                "appId": "00000000-0000-0000-0000-000000000001",
                                "version": "0.1.0",
                                "metadata": { "name": "Collision Detect App", "slug": "collision-detect-app", "description": "Will collide" },
                                "dependencies": [],
                                "contentDefinitions": []
                            }
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/apps/import/analyze")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blueprintJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canImport").value(true))
                .andExpect(jsonPath("$.apps[0].hasSlugCollision").value(true))
                .andExpect(jsonPath("$.apps[0].suggestedSlug").value("collision-detect-app-imported"));
    }

    @Test
    void shouldImportSingleApp() throws Exception {
        String blueprintJson = """
                {
                    "blueprint": {
                        "exportedAt": "2026-04-04T10:00:00Z",
                        "apps": [
                            {
                                "slug": "imported-simple-app",
                                "name": "Imported Simple App",
                                "description": "A simple imported app",
                                "version": "0.1.0",
                                "dependencies": [],
                                "snapshot": {
                                    "appId": "00000000-0000-0000-0000-000000000001",
                                    "version": "0.1.0",
                                    "metadata": { "name": "Imported Simple App", "slug": "imported-simple-app", "description": "A simple imported app" },
                                    "dependencies": [],
                                    "contentDefinitions": []
                                }
                            }
                        ]
                    },
                    "slugOverrides": {}
                }
                """;

        mockMvc.perform(post("/api/v1/apps/import/execute")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blueprintJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedApps").isArray())
                .andExpect(jsonPath("$.importedApps.length()").value(1))
                .andExpect(jsonPath("$.importedApps[0].name").value("Imported Simple App"))
                .andExpect(jsonPath("$.importedApps[0].id").isNotEmpty());
    }

    @Test
    void shouldImportWithAutoRename() throws Exception {
        createApp("Rename Import App");

        String blueprintJson = """
                {
                    "blueprint": {
                        "exportedAt": "2026-04-04T10:00:00Z",
                        "apps": [
                            {
                                "slug": "rename-import-app",
                                "name": "Rename Import App",
                                "description": "Will be renamed",
                                "version": "0.1.0",
                                "dependencies": [],
                                "snapshot": {
                                    "appId": "00000000-0000-0000-0000-000000000001",
                                    "version": "0.1.0",
                                    "metadata": { "name": "Rename Import App", "slug": "rename-import-app", "description": "Will be renamed" },
                                    "dependencies": [],
                                    "contentDefinitions": []
                                }
                            }
                        ]
                    },
                    "slugOverrides": {
                        "rename-import-app": "rename-import-app-v2"
                    }
                }
                """;

        mockMvc.perform(post("/api/v1/apps/import/execute")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blueprintJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedApps.length()").value(1))
                .andExpect(jsonPath("$.importedApps[0].name").value("Rename Import App (imported)"))
                .andExpect(jsonPath("$.importedApps[0].id").isNotEmpty());
    }

    private String createApp(String name) throws Exception {
        String request = """
                { "name": "%s", "description": "Test app" }
                """.formatted(name);

        MvcResult result = mockMvc.perform(post("/api/v1/apps")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private String publishApp(String appId, String version) throws Exception {
        String publishRequest = """
                { "version": "%s" }
                """.formatted(version);

        MvcResult result = mockMvc.perform(post("/api/v1/apps/" + appId + "/publish")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishRequest))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }
}
