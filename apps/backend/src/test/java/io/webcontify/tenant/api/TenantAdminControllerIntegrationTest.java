package io.webcontify.tenant.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.generated.model.CreateTenantRequest;
import io.webcontify.generated.model.UpdateTenantRequest;
import io.webcontify.generated.model.UpdateTenantStatusRequest;
import io.webcontify.shared.security.TestSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class TenantAdminControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateTenantAndReturn201WithLocation() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest("Integration Test", "admin@example.com", "Admin", "User");

        mockMvc.perform(
                        post("/api/v1/admin/tenants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Integration Test"))
                .andExpect(jsonPath("$.slug").value("integration-test"))
                .andExpect(jsonPath("$.status").value("PROVISIONING"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void shouldReturn409OnDuplicateName() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest("First", "admin@example.com", "Admin", "User");
        mockMvc.perform(
                post("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(
                        post("/api/v1/admin/tenants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("tenant.name_already_exists"));
    }

    @Test
    void shouldListTenants() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest("Listed", "admin@example.com", "Admin", "User");
        mockMvc.perform(
                post("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(get("/api/v1/admin/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.pageInfo.page").value(0));
    }

    @Test
    void shouldGetTenantById() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest("Get Test", "admin@example.com", "Admin", "User");
        MvcResult createResult =
                mockMvc.perform(
                                post("/api/v1/admin/tenants")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andReturn();

        String id =
                objectMapper
                        .readTree(createResult.getResponse().getContentAsString())
                        .get("id")
                        .asText();

        mockMvc.perform(get("/api/v1/admin/tenants/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Get Test"));
    }

    @Test
    void shouldReturn404ForNonExistentTenant() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("tenant.not_found"));
    }

    @Test
    void shouldUpdateTenant() throws Exception {
        CreateTenantRequest createRequest = new CreateTenantRequest("Before Update", "admin@example.com", "Admin", "User");
        MvcResult createResult =
                mockMvc.perform(
                                post("/api/v1/admin/tenants")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(createRequest)))
                        .andReturn();

        String id =
                objectMapper
                        .readTree(createResult.getResponse().getContentAsString())
                        .get("id")
                        .asText();

        UpdateTenantRequest updateRequest = new UpdateTenantRequest("After Update", "after-update");

        mockMvc.perform(
                        put("/api/v1/admin/tenants/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After Update"))
                .andExpect(jsonPath("$.slug").value("after-update"));
    }

    @Test
    void shouldUpdateTenantStatus() throws Exception {
        CreateTenantRequest createRequest = new CreateTenantRequest("Status Test", "admin@example.com", "Admin", "User");
        MvcResult createResult =
                mockMvc.perform(
                                post("/api/v1/admin/tenants")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(createRequest)))
                        .andReturn();

        String id =
                objectMapper
                        .readTree(createResult.getResponse().getContentAsString())
                        .get("id")
                        .asText();

        UpdateTenantStatusRequest statusRequest = new UpdateTenantStatusRequest();
        statusRequest.setStatus(io.webcontify.generated.model.TenantStatus.ACTIVE);

        mockMvc.perform(
                        put("/api/v1/admin/tenants/" + id + "/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldSoftDeleteTenant() throws Exception {
        CreateTenantRequest createRequest = new CreateTenantRequest("Delete Test", "admin@example.com", "Admin", "User");
        MvcResult createResult =
                mockMvc.perform(
                                post("/api/v1/admin/tenants")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(createRequest)))
                        .andReturn();

        String id =
                objectMapper
                        .readTree(createResult.getResponse().getContentAsString())
                        .get("id")
                        .asText();

        mockMvc.perform(delete("/api/v1/admin/tenants/" + id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/tenants/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400OnInvalidRequest() throws Exception {
        String invalidRequest = "{\"name\": \"\", \"slug\": \"INVALID_SLUG!\"}";

        mockMvc.perform(
                        post("/api/v1/admin/tenants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }
}
