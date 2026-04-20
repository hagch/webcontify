package io.webcontify.tenant.mapper;

import io.webcontify.generated.model.CreateTenantRequest;
import io.webcontify.generated.model.TenantResponse;
import io.webcontify.generated.model.UpdateTenantRequest;
import io.webcontify.tenant.model.Tenant;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.entity.TenantEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    @Mapping(source = "auditInfo.createdAt", target = "createdAt")
    @Mapping(source = "auditInfo.createdBy", target = "createdBy")
    @Mapping(source = "auditInfo.updatedAt", target = "updatedAt")
    @Mapping(source = "auditInfo.updatedBy", target = "updatedBy")
    @Mapping(source = "status", target = "status", qualifiedByName = "stringToTenantStatus")
    Tenant toDomain(TenantEntity entity);

    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(source = "status", target = "status", qualifiedByName = "tenantStatusToString")
    TenantEntity toEntity(Tenant tenant);

    @Mapping(source = "status", target = "status", qualifiedByName = "domainStatusToApiStatus")
    TenantResponse toResponse(Tenant tenant);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Tenant toDomain(CreateTenantRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Tenant toDomain(UpdateTenantRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(@MappingTarget TenantEntity entity, Tenant tenant);

    @Named("stringToTenantStatus")
    default TenantStatus stringToTenantStatus(String status) {
        return status == null ? null : TenantStatus.valueOf(status);
    }

    @Named("tenantStatusToString")
    default String tenantStatusToString(TenantStatus status) {
        return status == null ? null : status.name();
    }

    @Named("domainStatusToApiStatus")
    default io.webcontify.generated.model.TenantStatus domainStatusToApiStatus(TenantStatus status) {
        return status == null ? null : io.webcontify.generated.model.TenantStatus.fromValue(status.name());
    }
}
