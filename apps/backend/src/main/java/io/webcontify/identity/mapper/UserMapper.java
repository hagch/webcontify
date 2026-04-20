package io.webcontify.identity.mapper;

import io.webcontify.identity.model.TenantUser;
import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "auditInfo.createdAt", target = "createdAt")
    @Mapping(source = "auditInfo.createdBy", target = "createdBy")
    @Mapping(source = "auditInfo.updatedAt", target = "updatedAt")
    @Mapping(source = "auditInfo.updatedBy", target = "updatedBy")
    TenantUser toDomain(TenantUserEntity entity);

    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    TenantUserEntity toEntity(TenantUser domain);

    @Named("domainStatusToApiStatus")
    default io.webcontify.generated.model.UserStatus domainStatusToApiStatus(UserStatus status) {
        return status == null ? null : io.webcontify.generated.model.UserStatus.fromValue(status.name());
    }

    @Mapping(source = "status", target = "status", qualifiedByName = "domainStatusToApiStatus")
    io.webcontify.generated.model.UserResponse toResponse(TenantUser domain);
}
