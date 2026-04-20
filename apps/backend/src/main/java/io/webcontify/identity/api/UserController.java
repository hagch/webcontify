package io.webcontify.identity.api;

import io.webcontify.generated.api.TenantUsersApi;
import io.webcontify.generated.model.InviteUserRequest;
import io.webcontify.generated.model.UpdateUserStatusRequest;
import io.webcontify.generated.model.UserListResponse;
import io.webcontify.generated.model.UserResponse;
import io.webcontify.identity.mapper.UserMapper;
import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.security.RequiresPermission;
import io.webcontify.identity.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
public class UserController implements TenantUsersApi {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @Override
    @RequiresPermission(action = "inviter", resource = "user_management")
    public ResponseEntity<UserResponse> inviteUser(InviteUserRequest request) {
        var user = userService.invite(request.getEmail(), request.getFirstName(), request.getLastName());
        var response = userMapper.toResponse(user);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Override
    @RequiresPermission(action = "reader", resource = "user_management")
    public ResponseEntity<UserListResponse> listUsers(Integer page, Integer size) {
        var pageResult = userService.list(PageRequest.of(page, size));

        var response = new UserListResponse();
        response.setItems(pageResult.getContent().stream()
                .map(userMapper::toResponse)
                .toList());
        response.setPage(pageResult.getNumber());
        response.setSize(pageResult.getSize());
        response.setTotalElements(pageResult.getTotalElements());
        response.setTotalPages(pageResult.getTotalPages());

        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "reader", resource = "user_management")
    public ResponseEntity<UserResponse> getUser(UUID userId) {
        var user = userService.getById(userId);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @Override
    @RequiresPermission(action = "editor", resource = "user_management")
    public ResponseEntity<UserResponse> updateUserStatus(UUID userId, UpdateUserStatusRequest request) {
        var newStatus = UserStatus.valueOf(request.getStatus().getValue());
        var user = userService.updateStatus(userId, newStatus);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @Override
    @RequiresPermission(action = "inviter", resource = "user_management")
    public ResponseEntity<Void> resendInvite(UUID userId) {
        userService.resendInvite(userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(action = "deleter", resource = "user_management")
    public ResponseEntity<Void> deleteUser(UUID userId) {
        userService.delete(userId);
        return ResponseEntity.noContent().build();
    }
}
