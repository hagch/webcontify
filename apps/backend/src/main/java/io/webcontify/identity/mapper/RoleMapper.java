package io.webcontify.identity.mapper;

import io.webcontify.identity.model.TenantRole;
import io.webcontify.identity.repository.entity.TenantRoleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(source = "auditInfo.createdAt", target = "createdAt")
    @Mapping(source = "auditInfo.createdBy", target = "createdBy")
    @Mapping(source = "auditInfo.updatedAt", target = "updatedAt")
    @Mapping(source = "auditInfo.updatedBy", target = "updatedBy")
    TenantRole toDomain(TenantRoleEntity entity);

    @Mapping(target = "assigneeCount", ignore = true)
    io.webcontify.generated.model.RoleResponse toResponseInternal(TenantRole domain);

    default io.webcontify.generated.model.RoleResponse toResponse(TenantRole domain, int assigneeCount) {
        var response = toResponseInternal(domain);
        response.setAssigneeCount(assigneeCount);
        return response;
    }
}
