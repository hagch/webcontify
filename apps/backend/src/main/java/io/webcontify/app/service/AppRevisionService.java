package io.webcontify.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.zjsonpatch.DiffFlags;
import com.flipkart.zjsonpatch.JsonDiff;
import com.flipkart.zjsonpatch.JsonPatch;
import java.util.EnumSet;
import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.generated.model.AppRevisionResponse;
import io.webcontify.shared.changelog.model.ChangeRecord;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.shared.exception.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppRevisionService {

    private final ChangeRecordService changeRecordService;
    private final AppService appService;
    private final AppDependencyService dependencyService;
    private final AppRepository appRepository;
    private final AppMapper appMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AppRevisionService(
            ChangeRecordService changeRecordService,
            AppService appService,
            AppDependencyService dependencyService,
            AppRepository appRepository,
            AppMapper appMapper) {
        this.changeRecordService = changeRecordService;
        this.appService = appService;
        this.dependencyService = dependencyService;
        this.appRepository = appRepository;
        this.appMapper = appMapper;
    }

    @Transactional(readOnly = true)
    public List<AppRevisionResponse> listRevisions(UUID appId) {
        appService.getById(appId);
        List<ChangeRecord> records = changeRecordService.getDraftRecords("app", appId);
        return records.stream().map(this::toRevisionResponse).toList();
    }

    @Transactional(readOnly = true)
    public AppRevisionResponse getRevision(UUID revisionId) {
        ChangeRecord record = changeRecordService.getById(revisionId);
        return toRevisionResponse(record);
    }

    public App revertRevision(UUID appId, UUID revisionId) {
        ChangeRecord record = changeRecordService.getById(revisionId);

        AppEntity entity = appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)
                .orElseThrow(() -> new NotFoundException("app.not_found"));

        try {
            JsonNode patch = objectMapper.readTree(record.getPayload());

            // Split patch into property changes and dependency changes
            ArrayNode propertyOps = objectMapper.createArrayNode();
            List<JsonNode> dependencyOps = new ArrayList<>();

            for (JsonNode operation : patch) {
                String path = operation.get("path").asText();
                if (path.startsWith("/dependencies/")) {
                    dependencyOps.add(operation);
                } else {
                    propertyOps.add(operation);
                }
            }

            // Revert dependency changes via service calls
            for (JsonNode depOp : dependencyOps) {
                String op = depOp.get("op").asText();
                String path = depOp.get("path").asText();
                String depSlug = path.replace("/dependencies/", "");

                AppEntity depApp = appRepository.findBySlugAndAuditInfoDeletedAtIsNull(depSlug)
                        .orElse(null);
                if (depApp == null) continue;

                switch (op) {
                    case "add" -> dependencyService.removeDependency(appId, depApp.getId());
                    case "remove" -> dependencyService.addDependency(appId, depApp.getId());
                }
            }

            // Revert property changes via JSON Patch
            if (propertyOps.size() > 0) {
                JsonNode reversePatch = computeReversePatch(propertyOps);
                JsonNode beforeState = serializeAppState(entity);
                JsonNode revertedState = JsonPatch.apply(reversePatch, beforeState);

                if (revertedState.has("name")) {
                    entity.setName(revertedState.get("name").asText());
                }
                if (revertedState.has("description")) {
                    entity.setDescription(revertedState.get("description").asText());
                }

                entity = appRepository.save(entity);

                JsonNode afterState = serializeAppState(entity);
                JsonNode revertDiff = JsonDiff.asJson(beforeState, afterState,
                        EnumSet.of(DiffFlags.ADD_ORIGINAL_VALUE_ON_REPLACE));

                if (revertDiff.size() > 0) {
                    changeRecordService.create(
                            "app", appId, "reverted", revertDiff.toString(), "system:revert", "draft");
                }
            }

            // Reload entity after potential dependency changes
            entity = appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)
                    .orElseThrow(() -> new NotFoundException("app.not_found"));
            return appMapper.toDomain(entity);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse revision payload", e);
        }
    }

    public String generateSummary(String jsonPatchPayload) {
        try {
            JsonNode patch = objectMapper.readTree(jsonPatchPayload);
            List<String> descriptions = new ArrayList<>();

            for (JsonNode operation : patch) {
                String op = operation.get("op").asText();
                String path = operation.get("path").asText();

                switch (op) {
                    case "replace" -> {
                        String fieldName = path.substring(path.lastIndexOf('/') + 1);
                        descriptions.add("Changed " + fieldName);
                    }
                    case "add" -> descriptions.add("Added " + path);
                    case "remove" -> descriptions.add("Removed " + path);
                    default -> descriptions.add(op + " " + path);
                }
            }

            return String.join(", ", descriptions);
        } catch (JsonProcessingException e) {
            return "Unknown changes";
        }
    }

    private JsonNode computeReversePatch(JsonNode patch) {
        ArrayNode reversed = objectMapper.createArrayNode();

        for (int i = patch.size() - 1; i >= 0; i--) {
            JsonNode operation = patch.get(i);
            String op = operation.get("op").asText();
            ObjectNode reverseOp = objectMapper.createObjectNode();

            switch (op) {
                case "replace" -> {
                    reverseOp.put("op", "replace");
                    reverseOp.set("path", operation.get("path"));
                    if (operation.has("fromValue")) {
                        reverseOp.set("value", operation.get("fromValue"));
                    } else {
                        // Revert-revisions store the old value in "value", not "fromValue"
                        reverseOp.set("value", operation.get("value"));
                    }
                }
                case "add" -> {
                    reverseOp.put("op", "remove");
                    reverseOp.set("path", operation.get("path"));
                }
                case "remove" -> {
                    reverseOp.put("op", "add");
                    reverseOp.set("path", operation.get("path"));
                    if (operation.has("value")) {
                        reverseOp.set("value", operation.get("value"));
                    }
                }
                default -> {
                    reversed.add(operation.deepCopy());
                    continue;
                }
            }

            reversed.add(reverseOp);
        }

        return reversed;
    }

    private AppRevisionResponse toRevisionResponse(ChangeRecord record) {
        AppRevisionResponse response = new AppRevisionResponse();
        response.setId(record.getId());
        response.setChangeType(record.getChangeType());
        response.setSource(record.getSource());
        response.setSummary(generateSummary(record.getPayload()));
        response.setCreatedAt(record.getCreatedAt());
        response.setCreatedBy(record.getCreatedBy());

        try {
            response.setPatch(objectMapper.readValue(record.getPayload(), List.class));
        } catch (JsonProcessingException e) {
            response.setPatch(record.getPayload());
        }

        return response;
    }

    private JsonNode serializeAppState(AppEntity entity) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", entity.getName());
        node.put("description", entity.getDescription() != null ? entity.getDescription() : "");
        return node;
    }
}
