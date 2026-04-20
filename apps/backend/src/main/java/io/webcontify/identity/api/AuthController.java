package io.webcontify.identity.api;

import io.webcontify.generated.api.AuthApi;
import io.webcontify.generated.model.AccountUrlResponse;
import io.webcontify.generated.model.MyProfileResponse;
import io.webcontify.identity.service.AuthService;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<MyProfileResponse> getMyProfile() {
        var user = authService.getMe();
        var response = mapToProfileResponse(user);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AccountUrlResponse> getAccountUrl() {
        var url = authService.getAccountUrl();
        var response = new AccountUrlResponse();
        response.setUrl(url);
        return ResponseEntity.ok(response);
    }

    private MyProfileResponse mapToProfileResponse(UserRepresentation user) {
        var response = new MyProfileResponse();
        response.setId(UUID.fromString(user.getId()));
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        return response;
    }
}
