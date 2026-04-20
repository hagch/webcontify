package io.webcontify.identity.api;

import io.webcontify.generated.api.TenantRolesApi;
import io.webcontify.generated.model.CreateRoleRequest;
import io.webcontify.generated.model.RoleAppPermissionsResponse;
import io.webcontify.generated.model.RoleAssigneeResponse;
import io.webcontify.generated.model.RoleDetailResponse;
import io.webcontify.generated.model.RoleListResponse;
import io.webcontify.generated.model.RolePermissionPreviewResponse;
import io.webcontify.generated.model.RoleResponse;
import io.webcontify.generated.model.SetRoleAppPermissionsRequest;
import io.webcontify.generated.model.SetRolePermissionsRequest;
import io.webcontify.generated.model.UpdateRoleRequest;
import io.webcontify.identity.mapper.RoleMapper;
import io.webcontify.identity.model.RolePermissionEntry;
import io.webcontify.identity.security.RequiresPermission;
import io.webcontify.identity.service.RoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
public class RoleController implements TenantRolesApi {

    private final RoleService roleService;
    private final RoleMapper roleMapper;

    public RoleController(RoleService roleService, RoleMapper roleMapper) {
        this.roleService = roleService;
        this.roleMapper = roleMapper;
    }

    @Override
    @RequiresPermission(action = "creator", resource = "role_management")
    public ResponseEntity<RoleResponse> createRole(CreateRoleRequest createRoleRequest) {
        var role = roleService.create(createRoleRequest.getName(), createRoleRequest.getDescription());
        int assigneeCount = roleService.getAssigneeCount(role.getId());
        var response = roleMapper.toResponse(role, assigneeCount);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(role.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Override
    @RequiresPermission(action = "reader", resource = "role_management")
    public ResponseEntity<RoleListResponse> listRoles() {
        var roles = roleService.list();

        var roleItems = roles.stream()
                .map(role -> roleMapper.toResponse(role, roleService.getAssigneeCount(role.getId())))
                .toList();

        var response = new RoleListResponse();
        response.setRoles(roleItems);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "reader", resource = "role_management")
    public ResponseEntity<RoleDetailResponse> getRole(UUID roleId) {
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
    public ResponseEntity<RoleResponse> updateRole(UUID roleId, UpdateRoleRequest updateRoleRequest) {
        var role = roleService.update(roleId, updateRoleRequest.getName(), updateRoleRequest.getDescription());
        int assigneeCount = roleService.getAssigneeCount(roleId);
        return ResponseEntity.ok(roleMapper.toResponse(role, assigneeCount));
    }

    @Override
    @RequiresPermission(action = "deleter", resource = "role_management")
    public ResponseEntity<Void> deleteRole(UUID roleId) {
        roleService.delete(roleId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(action = "editor", resource = "role_management")
    public ResponseEntity<RoleDetailResponse> setRolePermissions(UUID roleId, SetRolePermissionsRequest setRolePermissionsRequest) {
        var entries = setRolePermissionsRequest.getPermissions().stream()
                .map(p -> new RolePermissionEntry(p.getResourceType(), p.getActions()))
                .toList();
        roleService.setPermissions(roleId, entries);
        return getRole(roleId);
    }

    @Override
    @RequiresPermission(action = "reader", resource = "role_management")
    public ResponseEntity<RoleAppPermissionsResponse> getRoleAppPermissions(UUID roleId) {
        var items = roleService.getAppPermissions(roleId);
        var response = new RoleAppPermissionsResponse();
        response.setItems(items);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "editor", resource = "role_management")
    public ResponseEntity<RoleAppPermissionsResponse> setRoleAppPermissions(UUID roleId, SetRoleAppPermissionsRequest setRoleAppPermissionsRequest) {
        roleService.setAppPermissions(roleId, setRoleAppPermissionsRequest.getAppPermissions());
        return getRoleAppPermissions(roleId);
    }
}
