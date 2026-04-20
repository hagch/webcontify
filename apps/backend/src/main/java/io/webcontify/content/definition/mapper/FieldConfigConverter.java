package io.webcontify.content.definition.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.webcontify.content.definition.model.*;
import org.springframework.stereotype.Component;

@Component
public class FieldConfigConverter {
    private final ObjectMapper objectMapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public FieldConfig fromJson(String json, FieldType fieldType) {
        if (json == null) return null;
        try {
            return switch (fieldType) {
                case TEXT -> objectMapper.readValue(json, TextFieldConfig.class);
                case INTEGER -> objectMapper.readValue(json, IntegerFieldConfig.class);
                case DECIMAL -> objectMapper.readValue(json, DecimalFieldConfig.class);
                case BOOLEAN -> objectMapper.readValue(json, BooleanFieldConfig.class);
                case DATE -> objectMapper.readValue(json, DateFieldConfig.class);
                case ENUM -> objectMapper.readValue(json, EnumFieldConfig.class);
                case REFERENCE -> objectMapper.readValue(json, ReferenceFieldConfig.class);
                case MIRROR -> objectMapper.readValue(json, MirrorFieldConfig.class);
            };
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize FieldConfig", e);
        }
    }

    public String toJson(FieldConfig config) {
        if (config == null) return null;
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize FieldConfig", e);
        }
    }

    public io.webcontify.generated.model.FieldConfig toApiConfig(FieldConfig config) {
        if (config == null) return null;
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(config);
        String fieldType = switch (config) {
            case TextFieldConfig c -> "TEXT";
            case IntegerFieldConfig c -> "INTEGER";
            case DecimalFieldConfig c -> "DECIMAL";
            case BooleanFieldConfig c -> "BOOLEAN";
            case DateFieldConfig c -> "DATE";
            case EnumFieldConfig c -> "ENUM";
            case ReferenceFieldConfig c -> "REFERENCE";
            case MirrorFieldConfig c -> "MIRROR";
        };
        node.put("fieldType", fieldType);
        return objectMapper.convertValue(node, io.webcontify.generated.model.FieldConfig.class);
    }

    public FieldConfig fromApiConfig(io.webcontify.generated.model.FieldConfig config) {
        if (config == null) return null;
        String fieldTypeStr = config.getFieldType().toString();
        FieldType fieldType = FieldType.valueOf(fieldTypeStr);
        JsonNode node = objectMapper.valueToTree(config);
        return fromJson(node.toString(), fieldType);
    }
}
