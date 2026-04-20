package io.webcontify.content.definition.mapper;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.generated.model.ContentDefinitionResponse;
import io.webcontify.generated.model.CreateContentDefinitionRequest;
import io.webcontify.generated.model.UpdateContentDefinitionRequest;
import java.util.List;
import java.util.UUID;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {ContentFieldMapper.class})
public interface ContentDefinitionMapper {

    @Mapping(source = "auditInfo.createdAt", target = "createdAt")
    @Mapping(source = "auditInfo.createdBy", target = "createdBy")
    @Mapping(source = "auditInfo.updatedAt", target = "updatedAt")
    @Mapping(source = "auditInfo.updatedBy", target = "updatedBy")
    ContentDefinition toDomain(ContentDefinitionEntity entity);

    List<ContentDefinition> toDomainList(List<ContentDefinitionEntity> entities);

    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(target = "fields", ignore = true)
    ContentDefinitionEntity toEntity(ContentDefinition domain);

    @Mapping(source = "createdBy", target = "createdBy", qualifiedByName = "uuidToString")
    @Mapping(source = "updatedBy", target = "updatedBy", qualifiedByName = "uuidToString")
    @Mapping(target = "effectiveActions", ignore = true)
    ContentDefinitionResponse toResponse(ContentDefinition domain);

    List<ContentDefinitionResponse> toResponseList(List<ContentDefinition> domains);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "appId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "displayFormat", ignore = true)
    @Mapping(target = "positionX", ignore = true)
    @Mapping(target = "positionY", ignore = true)
    @Mapping(target = "fields", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ContentDefinition toDomain(CreateContentDefinitionRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "appId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "fields", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ContentDefinition toDomain(UpdateContentDefinitionRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "appId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "fields", ignore = true)
    void updateEntity(@MappingTarget ContentDefinitionEntity entity, ContentDefinition domain);

    @Named("uuidToString")
    default String uuidToString(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }
}
