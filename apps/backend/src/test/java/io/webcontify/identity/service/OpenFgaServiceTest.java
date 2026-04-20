package io.webcontify.identity.service;

import dev.openfga.sdk.api.client.ApiResponse;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.*;
import dev.openfga.sdk.api.configuration.ClientBatchCheckOptions;
import dev.openfga.sdk.api.configuration.ClientCheckOptions;
import dev.openfga.sdk.api.configuration.ClientDeleteTuplesOptions;
import dev.openfga.sdk.api.configuration.ClientListObjectsOptions;
import dev.openfga.sdk.api.configuration.ClientWriteOptions;
import dev.openfga.sdk.api.model.*;
import io.webcontify.identity.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenFgaServiceTest {

    @Mock
    private OpenFgaClient openFgaClient;

    @Mock
    private ResourceLoader resourceLoader;

    private OpenFgaService openFgaService;

    @BeforeEach
    void setUp() {
        openFgaService = new OpenFgaService(openFgaClient, "classpath:openfga/model.json", resourceLoader);
    }

    @Test
    void createStore_shouldCreateStoreAndDeployModel() throws Exception {
        var sdkCreateStoreResponse = new dev.openfga.sdk.api.model.CreateStoreResponse();
        sdkCreateStoreResponse.setId("store-new-123");
        var clientCreateStoreResponse = new ClientCreateStoreResponse(
                new ApiResponse<>(200, Map.of(), "", sdkCreateStoreResponse));
        when(openFgaClient.createStore(any(dev.openfga.sdk.api.model.CreateStoreRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(clientCreateStoreResponse));

        var sdkWriteModelResponse = new WriteAuthorizationModelResponse();
        sdkWriteModelResponse.setAuthorizationModelId("model-1");
        var clientWriteModelResponse = new ClientWriteAuthorizationModelResponse(
                new ApiResponse<>(200, Map.of(), "", sdkWriteModelResponse));
        when(openFgaClient.writeAuthorizationModel(any(WriteAuthorizationModelRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(clientWriteModelResponse));

        when(resourceLoader.getResource("classpath:openfga/model.json"))
                .thenReturn(new ClassPathResource("openfga/model.json"));

        var result = openFgaService.createStore(new io.webcontify.identity.model.CreateStoreRequest("test-tenant"));

        assertThat(result).isNotNull();
        assertThat(result.storeId()).isEqualTo("store-new-123");
        verify(openFgaClient).createStore(any(dev.openfga.sdk.api.model.CreateStoreRequest.class));
        verify(openFgaClient).writeAuthorizationModel(any(WriteAuthorizationModelRequest.class), any());
    }

    @Test
    void check_shouldReturnTrueWhenAllowed() throws Exception {
        var sdkCheckResponse = new CheckResponse();
        sdkCheckResponse.setAllowed(true);
        var clientCheckResponse = new ClientCheckResponse(
                new ApiResponse<>(200, Map.of(), "", sdkCheckResponse));
        when(openFgaClient.check(any(ClientCheckRequest.class), any(ClientCheckOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(clientCheckResponse));

        var request = new OpenFgaCheckRequest("store-123", UUID.randomUUID(), "reader", "user_management", null);

        assertThat(openFgaService.check(request)).isTrue();
    }

    @Test
    void check_shouldReturnFalseWhenDenied() throws Exception {
        var sdkCheckResponse = new CheckResponse();
        sdkCheckResponse.setAllowed(false);
        var clientCheckResponse = new ClientCheckResponse(
                new ApiResponse<>(200, Map.of(), "", sdkCheckResponse));
        when(openFgaClient.check(any(ClientCheckRequest.class), any(ClientCheckOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(clientCheckResponse));

        var request = new OpenFgaCheckRequest("store-123", UUID.randomUUID(), "editor", "user_management", null);

        assertThat(openFgaService.check(request)).isFalse();
    }

    @Test
    void write_shouldWriteTuples() throws Exception {
        var clientWriteResponse = new ClientWriteResponse(new ApiResponse<>(200, Map.of(), "", null));
        when(openFgaClient.write(any(ClientWriteRequest.class), any(ClientWriteOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(clientWriteResponse));

        var tuples = List.of(new RelationshipTuple("user:123", "admin", "tenant:456"));
        openFgaService.write(new OpenFgaWriteRequest("store-123", tuples));

        verify(openFgaClient).write(any(ClientWriteRequest.class), any(ClientWriteOptions.class));
    }

    @Test
    void delete_shouldDeleteTuples() throws Exception {
        var clientWriteResponse = new ClientWriteResponse(new ApiResponse<>(200, Map.of(), "", null));
        when(openFgaClient.deleteTuples(any(), any(ClientDeleteTuplesOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(clientWriteResponse));

        var tuples = List.of(new RelationshipTuple("user:123", "admin", "tenant:456"));
        openFgaService.delete(new OpenFgaDeleteRequest("store-123", tuples));

        verify(openFgaClient).deleteTuples(any(), any(ClientDeleteTuplesOptions.class));
    }

    @Test
    void listObjects_shouldReturnObjectIds() throws Exception {
        var sdkListObjectsResponse = new ListObjectsResponse();
        sdkListObjectsResponse.setObjects(List.of("user_management:tenant-1", "user_management:tenant-2"));
        var clientListObjectsResponse = new ClientListObjectsResponse(
                new ApiResponse<>(200, Map.of(), "", sdkListObjectsResponse));
        when(openFgaClient.listObjects(any(ClientListObjectsRequest.class), any(ClientListObjectsOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(clientListObjectsResponse));

        var request = new OpenFgaListObjectsRequest("store-123", UUID.randomUUID(), "reader", "user_management");
        var result = openFgaService.listObjects(request);

        assertThat(result).containsExactly("user_management:tenant-1", "user_management:tenant-2");
    }

    @Nested
    class BatchCheck {

        @Test
        void batchCheck_shouldReturnResultsMapWithCorrectKeysAndValues() throws Exception {
            var userId = UUID.randomUUID();
            var checkAllowed = new OpenFgaCheckRequest("store-123", userId, "reader", "user_management", null);
            var checkDenied = new OpenFgaCheckRequest("store-123", userId, "editor", "user_management", null);
            var batchRequest = new OpenFgaBatchCheckRequest("store-123", List.of(checkAllowed, checkDenied));

            var reqAllowed = new ClientCheckRequest()
                    .user("user:" + userId)
                    .relation("reader")
                    ._object("user_management:*");
            var reqDenied = new ClientCheckRequest()
                    .user("user:" + userId)
                    .relation("editor")
                    ._object("user_management:*");

            ClientBatchCheckResponse resp1 = mock(ClientBatchCheckResponse.class);
            when(resp1.getRequest()).thenReturn(reqAllowed);
            when(resp1.getAllowed()).thenReturn(true);

            ClientBatchCheckResponse resp2 = mock(ClientBatchCheckResponse.class);
            when(resp2.getRequest()).thenReturn(reqDenied);
            when(resp2.getAllowed()).thenReturn(false);

            when(openFgaClient.batchCheck(anyList(), any(ClientBatchCheckOptions.class)))
                    .thenReturn(CompletableFuture.completedFuture(List.of(resp1, resp2)));

            var result = openFgaService.batchCheck(batchRequest);

            assertThat(result.results()).hasSize(2);
            assertThat(result.results()).containsEntry("reader:user_management", true);
            assertThat(result.results()).containsEntry("editor:user_management", false);
        }
    }
}
