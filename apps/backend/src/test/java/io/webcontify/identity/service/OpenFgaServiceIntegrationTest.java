package io.webcontify.identity.service;

import io.webcontify.identity.config.OpenFgaConfiguration;
import io.webcontify.identity.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {OpenFgaConfiguration.class, OpenFgaService.class})
@Testcontainers
class OpenFgaServiceIntegrationTest {

    @Container
    static GenericContainer<?> openfgaContainer = new GenericContainer<>("openfga/openfga:v1.8.1")
            .withCommand("run")
            .withExposedPorts(8080);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("openfga.api-url", () ->
                "http://" + openfgaContainer.getHost() + ":" + openfgaContainer.getMappedPort(8080));
        registry.add("openfga.authorization-model-path", () -> "classpath:openfga/model.json");
    }

    @Autowired
    private OpenFgaService openFgaService;

    @Test
    void shouldCreateStoreAndDeployModel() {
        var response = openFgaService.createStore(new CreateStoreRequest("integration-test-tenant"));

        assertThat(response).isNotNull();
        assertThat(response.storeId()).isNotBlank();
    }

    @Test
    void shouldWriteAndCheckPermission() {
        var storeId = openFgaService.createStore(new CreateStoreRequest("write-check-tenant")).storeId();
        var userId = UUID.randomUUID();
        var tenantId = "test-tenant";
        var userManagementId = UUID.randomUUID();

        // user ist admin im tenant
        openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(
                new RelationshipTuple("user:" + userId, "admin", "tenant:" + tenantId)
        )));

        // user_management gehört zum tenant
        openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(
                new RelationshipTuple("tenant:" + tenantId, "tenant", "user_management:" + userManagementId)
        )));

        // user hat reader auf das konkrete user_management Objekt via Inheritance: admin -> tenant -> reader
        var checkRequest = new OpenFgaCheckRequest(storeId, userId, "reader", "user_management", userManagementId);
        assertThat(openFgaService.check(checkRequest)).isTrue();
    }

    @Test
    void shouldDenyUnauthorizedAccess() {
        var storeId = openFgaService.createStore(new CreateStoreRequest("deny-test-tenant")).storeId();
        var unauthorizedUserId = UUID.randomUUID();
        var userManagementId = UUID.randomUUID();

        // kein Tuple geschrieben — kein Zugriff
        var checkRequest = new OpenFgaCheckRequest(storeId, unauthorizedUserId, "reader", "user_management", userManagementId);
        assertThat(openFgaService.check(checkRequest)).isFalse();
    }
}
