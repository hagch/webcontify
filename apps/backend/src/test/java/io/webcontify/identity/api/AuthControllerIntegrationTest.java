package io.webcontify.identity.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.webcontify.identity.service.AuthService;
import io.webcontify.identity.service.KeycloakAdminService;
import io.webcontify.identity.service.OpenFgaService;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.identity.service.TenantInfrastructureLookupService;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.security.TestSecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class AuthControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean AuthService authService;
    @MockitoBean KeycloakAdminService keycloakAdminService;
    @MockitoBean OpenFgaService openFgaService;
    @MockitoBean PermissionService permissionService;
    @MockitoBean FileStorageService fileStorageService;
    @MockitoBean TenantInfrastructureLookupService lookupService;

    @Autowired MockMvc mockMvc;

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        lenient().when(permissionService.check(any())).thenReturn(true);
    }

    @Test
    void shouldReturnMyProfile() throws Exception {
        var user = new UserRepresentation();
        user.setId(USER_ID.toString());
        user.setEmail("me@test.com");
        user.setFirstName("Max");
        user.setLastName("Mustermann");
        when(authService.getMe()).thenReturn(user);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("me@test.com"))
                .andExpect(jsonPath("$.firstName").value("Max"))
                .andExpect(jsonPath("$.lastName").value("Mustermann"));
    }

    @Test
    void shouldReturnAccountUrl() throws Exception {
        when(authService.getAccountUrl())
                .thenReturn("http://auth.localhost/realms/test-realm/account/#/security/signingin");

        mockMvc.perform(get("/api/v1/auth/account-url")
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(
                        "http://auth.localhost/realms/test-realm/account/#/security/signingin"));
    }
}
