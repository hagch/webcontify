package io.webcontify.identity.api;

import io.webcontify.generated.api.TenantUserRolesApi;
import io.webcontify.generated.model.RoleAssigneeResponse;
import io.webcontify.generated.model.RoleDetailResponse;
import io.webcontify.generated.model.RolePermissionPreviewResponse;
import io.webcontify.generated.model.SetRoleAssigneesRequest;
import io.webcontify.generated.model.SetUserRolesRequest;
import io.webcontify.generated.model.UserRolesResponse;
import io.webcontify.identity.mapper.RoleMapper;
import io.webcontify.identity.security.RequiresPermission;
import io.webcontify.identity.service.RoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class UserRoleController implements TenantUserRolesApi {

    private final RoleService roleService;
    private final RoleMapper roleMapper;

    public UserRoleController(RoleService roleService, RoleMapper roleMapper) {
        this.roleService = roleService;
        this.roleMapper = roleMapper;
    }

    @Override
    @RequiresPermission(action = "reader", resource = "user_management")
    public ResponseEntity<UserRolesResponse> getUserRoles(UUID userId) {
        var roles = roleService.getRolesForUser(userId);

        var roleItems = roles.stream()
                .map(role -> roleMapper.toResponse(role, roleService.getAssigneeCount(role.getId())))
                .toList();

        var response = new UserRolesResponse();
        response.setRoles(roleItems);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "editor", resource = "role_management")
    public ResponseEntity<RoleDetailResponse> setRoleAssignees(UUID roleId, SetRoleAssigneesRequest setRoleAssigneesRequest) {
        var userIds = setRoleAssigneesRequest.getUserIds();
        roleService.setAssignees(roleId, userIds);

        var role = roleService.getById(roleId);
        var assignees = roleService.getAssignees(roleId);
        var permissions = roleService.getPermissionPreview(roleId);

        var response = new RoleDetailResponse();
        response.setId(role.getId());
        response.setName(role.getName());
        response.setDescription(role.getDescription());
        response.setSystemRole(role.isSystemRole());
        response.setCreatedAt(role.getCreatedAt());
        response.setUpdatedAt(role.getUpdatedAt());

        response.setAssignees(assignees.stream().map(a -> {
            var ar = new RoleAssigneeResponse();
            ar.setUserId(a.userId());
            ar.setEmail(a.email());
            ar.setFirstName(a.firstName());
            ar.setLastName(a.lastName());
            return ar;
        }).toList());

        response.setPermissions(permissions.stream().map(p -> {
            var pr = new RolePermissionPreviewResponse();
            pr.setResourceType(p.resourceType());
            pr.setActions(p.actions());
            return pr;
        }).toList());

        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "editor", resource = "role_management")
    public ResponseEntity<UserRolesResponse> setUserRoles(UUID userId, SetUserRolesRequest setUserRolesRequest) {
        var roleIds = setUserRolesRequest.getRoleIds();
        roleService.setRolesForUser(userId, roleIds);

        var roles = roleService.getRolesForUser(userId);
        var roleItems = roles.stream()
                .map(role -> roleMapper.toResponse(role, roleService.getAssigneeCount(role.getId())))
                .toList();

        var response = new UserRolesResponse();
        response.setRoles(roleItems);
        return ResponseEntity.ok(response);
    }
}
