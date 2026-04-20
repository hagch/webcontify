package io.webcontify.content.definition.mapper;

import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.generated.model.FieldDefinitionResponse;
import java.util.List;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public abstract class ContentFieldMapper {

    protected FieldConfigConverter fieldConfigConverter;

    @org.springframework.beans.factory.annotation.Autowired
    public void setFieldConfigConverter(FieldConfigConverter converter) {
        this.fieldConfigConverter = converter;
    }

    @Mapping(source = "fieldType", target = "fieldType", qualifiedByName = "stringToFieldType")
    @Mapping(target = "config", ignore = true)
    public abstract FieldDefinition toDomain(ContentFieldEntity entity);

    public abstract List<FieldDefinition> toDomainList(List<ContentFieldEntity> entities);

    @AfterMapping
    protected void mapConfigToDomain(
            ContentFieldEntity entity, @MappingTarget FieldDefinition domain) {
        if (entity.getConfig() != null && domain.getFieldType() != null) {
            domain.setConfig(
                    fieldConfigConverter.fromJson(entity.getConfig(), domain.getFieldType()));
        }
    }

    @Mapping(source = "fieldType", target = "fieldType", qualifiedByName = "fieldTypeToString")
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "definition", ignore = true)
    @Mapping(target = "auditInfo", ignore = true)
    public abstract ContentFieldEntity toEntity(FieldDefinition domain);

    @AfterMapping
    protected void mapConfigToEntity(
            FieldDefinition domain, @MappingTarget ContentFieldEntity entity) {
        entity.setConfig(fieldConfigConverter.toJson(domain.getConfig()));
    }

    @Mapping(source = "uniqueValue", target = "unique")
    @Mapping(source = "fieldType", target = "fieldType", qualifiedByName = "domainToApiFieldType")
    @Mapping(target = "config", ignore = true)
    public abstract FieldDefinitionResponse toResponse(FieldDefinition domain);

    public abstract List<FieldDefinitionResponse> toResponseList(List<FieldDefinition> domains);

    @AfterMapping
    protected void mapConfigToResponse(
            FieldDefinition domain, @MappingTarget FieldDefinitionResponse response) {
        response.setConfig(fieldConfigConverter.toApiConfig(domain.getConfig()));
    }

    @Named("stringToFieldType")
    protected FieldType stringToFieldType(String type) {
        return type == null ? null : FieldType.valueOf(type);
    }

    @Named("fieldTypeToString")
    protected String fieldTypeToString(FieldType type) {
        return type == null ? null : type.name();
    }

    @Named("domainToApiFieldType")
    protected io.webcontify.generated.model.FieldType domainToApiFieldType(FieldType type) {
        return type == null
                ? null
                : io.webcontify.generated.model.FieldType.fromValue(type.name());
    }
}
