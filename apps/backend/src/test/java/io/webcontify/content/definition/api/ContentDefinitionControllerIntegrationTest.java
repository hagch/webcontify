package io.webcontify.content.definition.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class ContentDefinitionControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean PermissionService permissionService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID TEST_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000098");
    private static final UUID TEST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private String appSlug;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(permissionService.check(any())).thenReturn(true);
        lenient()
                .when(permissionService.getEffectiveActions(anyString(), any(UUID.class), anyList()))
                .thenReturn(List.of("viewer", "editor", "deleter"));

        if (tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_TENANT_ID).isEmpty()) {
            TenantEntity tenant = new TenantEntity();
            tenant.setId(TEST_TENANT_ID);
            tenant.setName("Content Test Tenant");
            tenant.setSlug("content-test-tenant");
            tenant.setStatus("ACTIVE");

            AuditInfo auditInfo = new AuditInfo();
            auditInfo.setCreatedAt(OffsetDateTime.now());
            auditInfo.setCreatedBy(SystemUser.ID);
            auditInfo.setUpdatedAt(OffsetDateTime.now());
            auditInfo.setUpdatedBy(SystemUser.ID);
            tenant.setAuditInfo(auditInfo);

            tenantRepository.save(tenant);
        }

        // Create a test app for content definitions
        String appName = "Content Test App " + UUID.randomUUID().toString().substring(0, 8);
        String appRequest = """
                { "name": "%s" }
                """.formatted(appName);

        MvcResult appResult = mockMvc.perform(post("/api/v1/apps")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appRequest))
                .andExpect(status().isCreated())
                .andReturn();

        appSlug = objectMapper
                .readTree(appResult.getResponse().getContentAsString())
                .get("slug")
                .asText();
    }

    private String definitionsUrl() {
        return "/api/v1/content/" + appSlug + "/definitions";
    }

    private String definitionUrl(String definitionId) {
        return definitionsUrl() + "/" + definitionId;
    }

    private String fieldsUrl(String definitionId) {
        return definitionUrl(definitionId) + "/fields";
    }

    private String fieldUrl(String definitionId, String fieldId) {
        return fieldsUrl(definitionId) + "/" + fieldId;
    }

    private MvcResult createDefinition(String name) throws Exception {
        String request = """
                { "name": "%s" }
                """.formatted(name);

        return mockMvc.perform(post(definitionsUrl())
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String extractId(MvcResult result) throws Exception {
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private String extractField(MvcResult result, String field) throws Exception {
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get(field)
                .asText();
    }

    @Test
    void shouldCreateAndRetrieveDefinition() throws Exception {
        MvcResult createResult = mockMvc.perform(post(definitionsUrl())
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Articles" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Articles"))
                .andExpect(jsonPath("$.slug").value("articles"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.fields").isArray())
                .andExpect(jsonPath("$.fields").isEmpty())
                .andReturn();

        String id = extractId(createResult);

        mockMvc.perform(get(definitionUrl(id))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Articles"))
                .andExpect(jsonPath("$.slug").value("articles"));
    }

    @Test
    void shouldReturn409OnDuplicateName() throws Exception {
        createDefinition("Duplicate Definition");

        String request = """
                { "name": "Duplicate Definition" }
                """;

        mockMvc.perform(post(definitionsUrl())
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("content_definition.name_already_exists"));
    }

    @Test
    void shouldUpdateDefinition() throws Exception {
        MvcResult createResult = createDefinition("Before Update");
        String id = extractId(createResult);
        String slugBefore = extractField(createResult, "slug");

        String updateRequest = """
                { "name": "After Update", "description": "Updated desc" }
                """;

        mockMvc.perform(put(definitionUrl(id))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After Update"))
                .andExpect(jsonPath("$.description").value("Updated desc"))
                .andExpect(jsonPath("$.slug").value(slugBefore));
    }

    @Test
    void shouldDeleteDefinition() throws Exception {
        MvcResult createResult = createDefinition("Delete Me");
        String id = extractId(createResult);

        mockMvc.perform(delete(definitionUrl(id))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(definitionUrl(id))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCheckNameAvailability() throws Exception {
        createDefinition("Unique Name Check");

        mockMvc.perform(get(definitionsUrl() + "/name-available")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .param("name", "Unique Name Check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get(definitionsUrl() + "/name-available")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .param("name", "Completely New Definition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void shouldAddTextField() throws Exception {
        MvcResult createResult = createDefinition("Text Field Def");
        String defId = extractId(createResult);

        String fieldRequest = """
                {
                    "fieldKey": "title",
                    "fieldType": "TEXT",
                    "required": true,
                    "config": {
                        "minLength": 1,
                        "maxLength": 255
                    }
                }
                """;

        mockMvc.perform(post(fieldsUrl(defId))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fieldRequest))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.fieldKey").value("title"))
                .andExpect(jsonPath("$.fieldType").value("TEXT"))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.position").value(0))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void shouldAddReferenceFieldAndAutoCreateMirror() throws Exception {
        MvcResult sourceDef = createDefinition("Orders");
        String sourceDefId = extractId(sourceDef);

        MvcResult targetDef = createDefinition("Customers");
        String targetDefId = extractId(targetDef);

        String refFieldRequest = """
                {
                    "fieldKey": "customer",
                    "fieldType": "REFERENCE",
                    "config": {
                        "targetDefinitionId": "%s",
                        "relationType": "MANY_TO_ONE",
                        "onDelete": "SET_NULL"
                    }
                }
                """.formatted(targetDefId);

        mockMvc.perform(post(fieldsUrl(sourceDefId))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refFieldRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fieldKey").value("customer"))
                .andExpect(jsonPath("$.fieldType").value("REFERENCE"));

        // Verify MIRROR field was auto-created on the target definition
        MvcResult targetResult = mockMvc.perform(get(definitionUrl(targetDefId))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode targetBody = objectMapper.readTree(targetResult.getResponse().getContentAsString());
        JsonNode fields = targetBody.get("fields");
        boolean hasMirror = false;
        for (JsonNode field : fields) {
            if ("MIRROR".equals(field.get("fieldType").asText())) {
                hasMirror = true;
                break;
            }
        }
        assert hasMirror : "Expected a MIRROR field on the target definition";
    }

    @Test
    void shouldUpdateField() throws Exception {
        MvcResult createResult = createDefinition("Update Field Def");
        String defId = extractId(createResult);

        String fieldRequest = """
                {
                    "fieldKey": "original_key",
                    "fieldType": "TEXT"
                }
                """;

        MvcResult fieldResult = mockMvc.perform(post(fieldsUrl(defId))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fieldRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String fieldId = extractId(fieldResult);

        String updateRequest = """
                {
                    "fieldKey": "renamed_key",
                    "required": true
                }
                """;

        mockMvc.perform(put(fieldUrl(defId, fieldId))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldKey").value("renamed_key"))
                .andExpect(jsonPath("$.required").value(true));
    }

    @Test
    void shouldDeleteField() throws Exception {
        MvcResult createResult = createDefinition("Delete Field Def");
        String defId = extractId(createResult);

        String fieldRequest = """
                {
                    "fieldKey": "to_delete",
                    "fieldType": "TEXT"
                }
                """;

        MvcResult fieldResult = mockMvc.perform(post(fieldsUrl(defId))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fieldRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String fieldId = extractId(fieldResult);

        mockMvc.perform(delete(fieldUrl(defId, fieldId))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isNoContent());

        // Verify the field no longer appears in the definition
        mockMvc.perform(get(definitionUrl(defId))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields").isEmpty());
    }

    @Test
    void shouldReorderFields() throws Exception {
        MvcResult createResult = createDefinition("Reorder Def");
        String defId = extractId(createResult);

        // Add two fields
        MvcResult field1Result = mockMvc.perform(post(fieldsUrl(defId))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fieldKey": "first", "fieldType": "TEXT" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult field2Result = mockMvc.perform(post(fieldsUrl(defId))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fieldKey": "second", "fieldType": "INTEGER" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String field1Id = extractId(field1Result);
        String field2Id = extractId(field2Result);

        // Reorder: second before first
        String reorderRequest = """
                { "fieldIds": ["%s", "%s"] }
                """.formatted(field2Id, field1Id);

        MvcResult reorderResult = mockMvc.perform(put(fieldsUrl(defId) + "/reorder")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].fieldKey").value("second"))
                .andExpect(jsonPath("$.fields[1].fieldKey").value("first"))
                .andReturn();
    }

    @Test
    void shouldReturn400OnInvalidFieldConfig() throws Exception {
        MvcResult createResult = createDefinition("Invalid Config Def");
        String defId = extractId(createResult);

        // minLength > maxLength should trigger validation error
        String fieldRequest = """
                {
                    "fieldKey": "bad_text",
                    "fieldType": "TEXT",
                    "config": {
                        "minLength": 100,
                        "maxLength": 10
                    }
                }
                """;

        mockMvc.perform(post(fieldsUrl(defId))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fieldRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldBatchUpdatePositions() throws Exception {
        MvcResult def1Result = createDefinition("Position Def A");
        String def1Id = extractId(def1Result);

        MvcResult def2Result = createDefinition("Position Def B");
        String def2Id = extractId(def2Result);

        String positionsRequest = """
                {
                    "positions": [
                        { "definitionId": "%s", "positionX": 100.0, "positionY": 200.0 },
                        { "definitionId": "%s", "positionX": 300.0, "positionY": 400.0 }
                    ]
                }
                """.formatted(def1Id, def2Id);

        mockMvc.perform(put(definitionsUrl() + "/positions")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionsRequest))
                .andExpect(status().isNoContent());

        // Verify positions were updated
        mockMvc.perform(get(definitionUrl(def1Id))
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID)
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionX").value(100.0))
                .andExpect(jsonPath("$.positionY").value(200.0));
    }
}
