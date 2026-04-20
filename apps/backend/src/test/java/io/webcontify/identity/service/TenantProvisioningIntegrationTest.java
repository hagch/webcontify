package io.webcontify.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.identity.model.CreateKeycloakUserResponse;
import io.webcontify.identity.model.CreateRealmResponse;
import io.webcontify.identity.model.CreateStoreResponse;
import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.model.ProvisioningStatus;
import io.webcontify.shared.model.ProvisioningStepStatus;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.repository.TenantRepository;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class TenantProvisioningIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    KeycloakAdminService keycloakAdminService;

    @MockitoBean
    OpenFgaService openFgaService;

    @MockitoBean
    FileStorageService fileStorageService;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    TenantUserRepository tenantUserRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpMocks() {
        when(keycloakAdminService.createRealm(any()))
                .thenReturn(new CreateRealmResponse("tenant-acme"));
        when(keycloakAdminService.createUser(any()))
                .thenReturn(new CreateKeycloakUserResponse(UUID.randomUUID()));
        when(openFgaService.createStore(any()))
                .thenReturn(new CreateStoreResponse("store-123"));
        // fileStorageService.createBucket() is void — no stubbing required for happy path
        // keycloakAdminService.sendInviteEmail() is void — no stubbing required
    }

    @Test
    void shouldCompleteFullProvisioningFlow() throws Exception {
        String requestBody = """
                {
                    "name": "Acme Corp",
                    "adminEmail": "admin@acme.com",
                    "adminFirstName": "Max",
                    "adminLastName": "Muster"
                }
                """;

        // Step 1: Create tenant — must respond with PROVISIONING status
        var result = mockMvc.perform(post("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROVISIONING"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String tenantIdStr = objectMapper.readTree(responseBody).get("id").asText();
        UUID tenantId = UUID.fromString(tenantIdStr);

        // Step 2: Wait for async provisioning to complete (max 10 seconds)
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> tenantRepository.findById(tenantId)
                        .map(e -> "ACTIVE".equals(e.getStatus()))
                        .orElse(false));

        // Step 3: Verify final DB state
        var entity = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getKeycloakRealmName()).isEqualTo("tenant-acme");
        assertThat(entity.getOpenfgaStoreId()).isEqualTo("store-123");

        var provisioningStatus = ProvisioningStatus.fromJson(entity.getProvisioningStatus());
        assertThat(provisioningStatus.allCompleted()).isTrue();

        // Step 4: Verify all external service calls were made
        verify(fileStorageService).createBucket(startsWith("tenant-"));
        verify(keycloakAdminService).createRealm(any());
        verify(openFgaService).createStore(any());
        verify(keycloakAdminService).createUser(any());
        verify(keycloakAdminService).sendInviteEmail(any());
        verify(openFgaService).write(any());

        // Step 5: Verify tenant_user entry was created
        var tenantUsers = tenantUserRepository.findAll();
        assertThat(tenantUsers).hasSize(1);
        var tenantUser = tenantUsers.get(0);
        assertThat(tenantUser.getEmail()).isEqualTo("admin@acme.com");
        assertThat(tenantUser.getStatus()).isEqualTo(UserStatus.INVITED);
        assertThat(tenantUser.getInvitedBy()).isNull(); // System-Erstellung
    }

    @Test
    void shouldMarkS3StepAsFailedWhenBucketCreationFails() throws Exception {
        doThrow(new RuntimeException("S3 unavailable"))
                .when(fileStorageService).createBucket(any());

        String requestBody = """
                {
                    "name": "Fail Corp",
                    "adminEmail": "admin@fail.com",
                    "adminFirstName": "Max",
                    "adminLastName": "Muster"
                }
                """;

        var result = mockMvc.perform(post("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String tenantIdStr = objectMapper.readTree(responseBody).get("id").asText();
        UUID tenantId = UUID.fromString(tenantIdStr);

        // Wait until the provisioning attempt has been processed (step transitions to FAILED)
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> {
                    var entity = tenantRepository.findById(tenantId).orElse(null);
                    if (entity == null || entity.getProvisioningStatus() == null) {
                        return false;
                    }
                    var status = ProvisioningStatus.fromJson(entity.getProvisioningStatus());
                    return status.hasFailed();
                });

        // Tenant must remain in PROVISIONING — not ACTIVE
        var entity = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo("PROVISIONING");

        var provisioningStatus = ProvisioningStatus.fromJson(entity.getProvisioningStatus());
        assertThat(provisioningStatus.hasFailed()).isTrue();
        assertThat(provisioningStatus.steps().get(0).status()).isEqualTo(ProvisioningStepStatus.FAILED);
    }
}
