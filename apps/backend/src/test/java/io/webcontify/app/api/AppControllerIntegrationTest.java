package io.webcontify.app.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.IntegrationTestBase;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AppControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID TEST_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID TEST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        lenient().when(permissionService.check(any())).thenReturn(true);

        if (tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_TENANT_ID).isEmpty()) {
            TenantEntity tenant = new TenantEntity();
            tenant.setId(TEST_TENANT_ID);
            tenant.setName("App Test Tenant");
            tenant.setSlug("app-test-tenant");
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
    void shouldCreateAndRetrieveApp() throws Exception {
        String request = """
                { "name": "My First App", "description": "A test app" }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/apps")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("My First App"))
                .andExpect(jsonPath("$.slug").value("my-first-app"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        String id = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(get("/api/v1/apps/" + id)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My First App"))
                .andExpect(jsonPath("$.slug").value("my-first-app"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void shouldListApps() throws Exception {
        String request = """
                { "name": "Listed App" }
                """;

        mockMvc.perform(post("/api/v1/apps")
                .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));

        mockMvc.perform(get("/api/v1/apps")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.pageInfo.page").value(0));
    }

    @Test
    void shouldUpdateApp() throws Exception {
        String createRequest = """
                { "name": "Before Update App" }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/apps")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();

        String id = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        String updateRequest = """
                { "name": "After Update App", "description": "Updated description" }
                """;

        mockMvc.perform(put("/api/v1/apps/" + id)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After Update App"))
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    void shouldChangeAppStatus() throws Exception {
        String createRequest = """
                { "name": "Status Change App" }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/apps")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();

        String id = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        String statusRequest = """
                { "status": "ACTIVE" }
                """;

        mockMvc.perform(put("/api/v1/apps/" + id + "/status")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldSoftDeleteApp() throws Exception {
        String createRequest = """
                { "name": "Delete Me App" }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/apps")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();

        String id = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(delete("/api/v1/apps/" + id)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/apps/" + id)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn409OnDuplicateName() throws Exception {
        String request = """
                { "name": "Duplicate Name App" }
                """;

        mockMvc.perform(post("/api/v1/apps")
                .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));

        mockMvc.perform(post("/api/v1/apps")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("app.name_already_exists"));
    }

    @Test
    void shouldCheckNameAvailability() throws Exception {
        String request = """
                { "name": "Availability Check App" }
                """;

        mockMvc.perform(post("/api/v1/apps")
                .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));

        mockMvc.perform(get("/api/v1/apps/name-available")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .param("name", "Availability Check App"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get("/api/v1/apps/name-available")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .param("name", "Completely New Name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }
}
