package io.webcontify.app.mapper;

import io.webcontify.app.model.App;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.generated.model.AppResponse;
import io.webcontify.generated.model.CreateAppRequest;
import io.webcontify.generated.model.UpdateAppRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface AppMapper {

    @Mapping(source = "auditInfo.createdAt", target = "createdAt")
    @Mapping(source = "auditInfo.createdBy", target = "createdBy")
    @Mapping(source = "auditInfo.updatedAt", target = "updatedAt")
    @Mapping(source = "auditInfo.updatedBy", target = "updatedBy")
    @Mapping(source = "status", target = "status", qualifiedByName = "stringToAppStatus")
    App toDomain(AppEntity entity);

    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(source = "status", target = "status", qualifiedByName = "appStatusToString")
    AppEntity toEntity(App app);

    @Mapping(source = "status", target = "status", qualifiedByName = "domainStatusToApiStatus")
    AppResponse toResponse(App app);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "hasDraft", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    App toDomain(CreateAppRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "hasDraft", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    App toDomain(UpdateAppRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "hasDraft", ignore = true)
    void updateEntity(@MappingTarget AppEntity entity, App app);

    @Named("stringToAppStatus")
    default AppStatus stringToAppStatus(String status) {
        return status == null ? null : AppStatus.valueOf(status);
    }

    @Named("appStatusToString")
    default String appStatusToString(AppStatus status) {
        return status == null ? null : status.name();
    }

    @Named("domainStatusToApiStatus")
    default io.webcontify.generated.model.AppStatus domainStatusToApiStatus(AppStatus status) {
        return status == null ? null : io.webcontify.generated.model.AppStatus.fromValue(status.name());
    }
}
