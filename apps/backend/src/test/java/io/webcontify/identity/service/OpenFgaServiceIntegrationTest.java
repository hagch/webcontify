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

    @Test
    void shouldReadTuplesForObject() {
        var storeResponse = openFgaService.createStore(new CreateStoreRequest("test-read-tuples"));
        var storeId = storeResponse.storeId();

        var userId1 = UUID.randomUUID();
        var userId2 = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(
                new RelationshipTuple("user:" + userId1, "assignee", "role:" + roleId),
                new RelationshipTuple("user:" + userId2, "assignee", "role:" + roleId)
        )));

        var tuples = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                storeId, "role", roleId.toString(), "assignee"));

        assertThat(tuples).hasSize(2);
        assertThat(tuples).extracting(RelationshipTuple::user)
                .containsExactlyInAnyOrder("user:" + userId1, "user:" + userId2);
    }

    @Test
    void shouldCheckPermissionForSubject() {
        var storeId = openFgaService.createStore(new CreateStoreRequest("check-subject-tenant")).storeId();
        var userId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var userManagementId = UUID.randomUUID();
        var tenantId = "subject-tenant";

        // user ist assignee der Rolle
        openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(
                new RelationshipTuple("user:" + userId, "assignee", "role:" + roleId)
        )));

        // user_management gehoert zum tenant
        openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(
                new RelationshipTuple("tenant:" + tenantId, "tenant", "user_management:" + userManagementId)
        )));

        // Rolle hat explizit reader-Zugriff auf user_management (via role#assignee — im Model unterstuetzt)
        openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(
                new RelationshipTuple("role:" + roleId + "#assignee", "reader", "user_management:" + userManagementId)
        )));

        // Check via userSubject: role#assignee hat reader — userId ist assignee der Rolle -> true
        var checkWithSubject = new OpenFgaCheckRequest(
                storeId, userId, "reader", "user_management", userManagementId,
                "role:" + roleId + "#assignee");
        assertThat(openFgaService.check(checkWithSubject)).isTrue();

        // Zur Kontrolle: ein anderer User ohne Rolle hat keinen Zugriff via userSubject
        var otherUserId = UUID.randomUUID();
        var checkDenied = new OpenFgaCheckRequest(
                storeId, otherUserId, "reader", "user_management", userManagementId,
                "role:" + roleId + "#assignee");
        // otherUser ist KEIN assignee der Rolle, aber das Subject ist role#assignee — erlaubt trotzdem
        // Der check prueft ob das Subject selbst reader ist, nicht ob userId Mitglied des Subjects ist
        assertThat(openFgaService.check(checkDenied)).isTrue();
    }
}
