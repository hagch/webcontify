package io.webcontify.identity.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlatformSmtpProperties.class)
public class KeycloakAdminConfiguration {

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.client-secret:}")
    private String clientSecret;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.grant-type:client_credentials}")
    private String grantType;

    @Value("${keycloak.admin.username:}")
    private String username;

    @Value("${keycloak.admin.password:}")
    private String password;

    @Bean
    public Keycloak keycloakAdminClient() {
        var builder = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .clientId(clientId)
                .grantType(grantType);
        if ("password".equals(grantType)) {
            builder.username(username).password(password);
        } else {
            builder.clientSecret(clientSecret);
        }
        return builder.build();
    }
}
