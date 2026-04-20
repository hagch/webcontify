package io.webcontify.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.*;
import dev.openfga.sdk.api.configuration.*;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import io.webcontify.identity.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class OpenFgaService {

    private static final Logger log = LoggerFactory.getLogger(OpenFgaService.class);

    private final OpenFgaClient client;
    private final String authorizationModelPath;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public OpenFgaService(
            OpenFgaClient client,
            @Value("${openfga.authorization-model-path}") String authorizationModelPath,
            ResourceLoader resourceLoader) {
        this.client = client;
        this.authorizationModelPath = authorizationModelPath;
        this.resourceLoader = resourceLoader;
        this.objectMapper = new ObjectMapper();
    }

    public CreateStoreResponse createStore(CreateStoreRequest request) {
        try {
            log.info("Creating OpenFGA store for tenant slug={}", request.tenantSlug());

            var sdkCreateRequest = new dev.openfga.sdk.api.model.CreateStoreRequest()
                    .name(request.tenantSlug());
            var createResponse = client.createStore(sdkCreateRequest).get();
            var storeId = createResponse.getId();

            log.info("OpenFGA store created storeId={}", storeId);

            deployAuthorizationModel(storeId);

            return new CreateStoreResponse(storeId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OpenFGA store for tenant: " + request.tenantSlug(), e);
        }
    }

    public boolean check(OpenFgaCheckRequest request) {
        try {
            var object = buildObject(request.resourceType(), request.resourceId());
            var subject = request.userSubject() != null ? request.userSubject() : "user:" + request.userId();
            var sdkRequest = new ClientCheckRequest()
                    .user(subject)
                    .relation(request.action())
                    ._object(object);
            var options = new ClientCheckOptions();
            client.setStoreId(request.storeId());
            var response = client.check(sdkRequest, options).get();
            var allowed = Boolean.TRUE.equals(response.getAllowed());

            log.debug("OpenFGA check storeId={} user={} relation={} object={} allowed={}",
                    request.storeId(), request.userId(), request.action(), object, allowed);

            return allowed;
        } catch (Exception e) {
            throw new RuntimeException("OpenFGA check failed", e);
        }
    }

    public void write(OpenFgaWriteRequest request) {
        try {
            log.info("Writing {} OpenFGA tuples to storeId={}", request.tuples().size(), request.storeId());
            var writes = request.tuples().stream()
                    .map(this::toClientTupleKey)
                    .toList();
            var sdkRequest = ClientWriteRequest.ofWrites(writes);
            var options = new ClientWriteOptions();
            client.setStoreId(request.storeId());
            client.write(sdkRequest, options).get();
        } catch (Exception e) {
            throw new RuntimeException("OpenFGA write failed", e);
        }
    }

    public void delete(OpenFgaDeleteRequest request) {
        try {
            log.info("Deleting {} OpenFGA tuples from storeId={}", request.tuples().size(), request.storeId());
            var deletes = request.tuples().stream()
                    .map(this::toClientTupleKeyWithoutCondition)
                    .toList();
            var options = new ClientDeleteTuplesOptions();
            client.setStoreId(request.storeId());
            client.deleteTuples(deletes, options).get();
        } catch (Exception e) {
            throw new RuntimeException("OpenFGA delete failed", e);
        }
    }

    // TODO: OpenFGA read paginiert (Standard 100 Tupel). Fuer Phase 3d ausreichend (maxItems: 100).
    public List<RelationshipTuple> readTuples(OpenFgaReadTuplesRequest request) {
        try {
            var sdkRequest = new ClientReadRequest()
                    .relation(request.relation())
                    ._object(request.objectType() + ":" + request.objectId());
            client.setStoreId(request.storeId());
            var response = client.read(sdkRequest).get();
            if (response.getTuples() == null) {
                return List.of();
            }
            return response.getTuples().stream()
                    .map(t -> new RelationshipTuple(
                            t.getKey().getUser(),
                            t.getKey().getRelation(),
                            t.getKey().getObject()))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("OpenFGA readTuples failed", e);
        }
    }

    public OpenFgaBatchCheckResponse batchCheck(OpenFgaBatchCheckRequest request) {
        try {
            var checks = request.checks().stream()
                    .map(check -> {
                        var subject = check.userSubject() != null ? check.userSubject() : "user:" + check.userId();
                        var object = buildObject(check.resourceType(), check.resourceId());
                        return new ClientCheckRequest()
                                .user(subject)
                                .relation(check.action())
                                ._object(object);
                    })
                    .toList();

            client.setStoreId(request.storeId());
            var responses = client.batchCheck(checks, new ClientBatchCheckOptions()).get();

            var results = new HashMap<String, Boolean>();
            for (var response : responses) {
                var req = response.getRequest();
                var objectParts = req.getObject().split(":");
                var key = req.getRelation() + ":" + objectParts[0];
                results.put(key, Boolean.TRUE.equals(response.getAllowed()));
            }

            log.debug("OpenFGA batchCheck storeId={} checks={} results={}",
                    request.storeId(), checks.size(), results.size());
            return new OpenFgaBatchCheckResponse(results);
        } catch (Exception e) {
            throw new RuntimeException("OpenFGA batchCheck failed", e);
        }
    }

    public List<String> listObjects(OpenFgaListObjectsRequest request) {
        try {
            var sdkRequest = new ClientListObjectsRequest()
                    .user("user:" + request.userId())
                    .relation(request.action())
                    .type(request.resourceType());
            var options = new ClientListObjectsOptions();
            client.setStoreId(request.storeId());
            var response = client.listObjects(sdkRequest, options).get();
            return response.getObjects() != null ? response.getObjects() : List.of();
        } catch (Exception e) {
            throw new RuntimeException("OpenFGA listObjects failed", e);
        }
    }

    private void deployAuthorizationModel(String storeId) throws Exception {
        log.info("Deploying authorization model to storeId={}", storeId);
        var resource = resourceLoader.getResource(authorizationModelPath);
        var modelRequest = objectMapper.readValue(resource.getInputStream(), WriteAuthorizationModelRequest.class);
        var options = new ClientWriteAuthorizationModelOptions();
        client.setStoreId(storeId);
        var response = client.writeAuthorizationModel(modelRequest, options).get();
        log.info("Authorization model deployed storeId={} modelId={}", storeId, response.getAuthorizationModelId());
    }

    private String buildObject(String resourceType, java.util.UUID resourceId) {
        if (resourceId != null) {
            return resourceType + ":" + resourceId;
        }
        return resourceType + ":*";
    }

    private ClientTupleKey toClientTupleKey(RelationshipTuple tuple) {
        return new ClientTupleKey()
                .user(tuple.user())
                .relation(tuple.relation())
                ._object(tuple.object());
    }

    private ClientTupleKeyWithoutCondition toClientTupleKeyWithoutCondition(RelationshipTuple tuple) {
        return new ClientTupleKeyWithoutCondition()
                .user(tuple.user())
                .relation(tuple.relation())
                ._object(tuple.object());
    }
}
