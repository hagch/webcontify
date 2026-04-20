package io.webcontify.content.definition.api;

import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.definition.mapper.ContentFieldMapper;
import io.webcontify.content.definition.mapper.FieldConfigConverter;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.service.ContentDefinitionService;
import io.webcontify.content.definition.service.ContentFieldService;
import io.webcontify.generated.api.ContentFieldApi;
import io.webcontify.generated.model.*;
import io.webcontify.identity.security.RequiresPermission;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentFieldController implements ContentFieldApi {

    private final ContentFieldService fieldService;
    private final ContentDefinitionService definitionService;
    private final ContentFieldMapper fieldMapper;
    private final ContentDefinitionMapper definitionMapper;
    private final FieldConfigConverter fieldConfigConverter;

    public ContentFieldController(
            ContentFieldService fieldService,
            ContentDefinitionService definitionService,
            ContentFieldMapper fieldMapper,
            ContentDefinitionMapper definitionMapper,
            FieldConfigConverter fieldConfigConverter) {
        this.fieldService = fieldService;
        this.definitionService = definitionService;
        this.fieldMapper = fieldMapper;
        this.definitionMapper = definitionMapper;
        this.fieldConfigConverter = fieldConfigConverter;
    }

    @Override
    @RequiresPermission(action = "editor", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<FieldDefinitionResponse> addField(
            String appSlug, UUID definitionId, CreateFieldRequest createFieldRequest) {
        FieldDefinition field = new FieldDefinition();
        field.setFieldKey(createFieldRequest.getFieldKey());
        field.setFieldType(FieldType.valueOf(createFieldRequest.getFieldType().getValue()));
        field.setRequired(Boolean.TRUE.equals(createFieldRequest.getRequired()));
        field.setUniqueValue(Boolean.TRUE.equals(createFieldRequest.getUnique()));
        field.setSortable(Boolean.TRUE.equals(createFieldRequest.getSortable()));
        field.setFilterable(Boolean.TRUE.equals(createFieldRequest.getFilterable()));
        field.setConfig(fieldConfigConverter.fromApiConfig(createFieldRequest.getConfig()));

        FieldDefinition created = fieldService.addField(definitionId, field);
        FieldDefinitionResponse response = fieldMapper.toResponse(created);
        return ResponseEntity.created(
                        URI.create("/api/v1/content/" + appSlug + "/definitions/" + definitionId
                                + "/fields/" + created.getId()))
                .body(response);
    }

    @Override
    @RequiresPermission(action = "editor", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<FieldDefinitionResponse> updateField(
            String appSlug,
            UUID definitionId,
            UUID fieldId,
            UpdateFieldRequest updateFieldRequest) {
        FieldDefinition update = new FieldDefinition();
        update.setFieldKey(updateFieldRequest.getFieldKey());
        update.setRequired(Boolean.TRUE.equals(updateFieldRequest.getRequired()));
        update.setUniqueValue(Boolean.TRUE.equals(updateFieldRequest.getUnique()));
        update.setSortable(Boolean.TRUE.equals(updateFieldRequest.getSortable()));
        update.setFilterable(Boolean.TRUE.equals(updateFieldRequest.getFilterable()));

        if (updateFieldRequest.getConfig() != null) {
            update.setConfig(fieldConfigConverter.fromApiConfig(updateFieldRequest.getConfig()));
        }

        FieldDefinition updated = fieldService.updateField(fieldId, update);
        FieldDefinitionResponse response = fieldMapper.toResponse(updated);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "editor", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<Void> deleteField(String appSlug, UUID definitionId, UUID fieldId) {
        fieldService.deleteField(fieldId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(action = "editor", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<ContentDefinitionResponse> reorderFields(
            String appSlug, UUID definitionId, ReorderFieldsRequest reorderFieldsRequest) {
        fieldService.reorderFields(definitionId, reorderFieldsRequest.getFieldIds());
        ContentDefinition definition = definitionService.getById(definitionId);
        definition.setFields(fieldService.listByDefinition(definitionId));
        ContentDefinitionResponse response = definitionMapper.toResponse(definition);
        if (definition.getFields() != null) {
            response.setFields(fieldMapper.toResponseList(definition.getFields()));
        }
        return ResponseEntity.ok(response);
    }
}
