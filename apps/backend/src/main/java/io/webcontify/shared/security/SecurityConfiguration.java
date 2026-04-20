package io.webcontify.shared.security;

import io.webcontify.identity.security.UserActivationFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "webcontify.security.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfiguration {

    @Value("${webcontify.cors.allowed-origin-patterns:http://*.localhost}")
    private String allowedOriginPatterns;

    private final JwtToTenantAuthenticationConverter jwtToTenantAuthenticationConverter;
    private final TenantSlugResolver tenantSlugResolver;
    private final UserActivationFilter userActivationFilter;

    public SecurityConfiguration(
            JwtToTenantAuthenticationConverter jwtToTenantAuthenticationConverter,
            TenantSlugResolver tenantSlugResolver,
            UserActivationFilter userActivationFilter) {
        this.jwtToTenantAuthenticationConverter = jwtToTenantAuthenticationConverter;
        this.tenantSlugResolver = tenantSlugResolver;
        this.userActivationFilter = userActivationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/api/v1/tenant/**").authenticated()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtToTenantAuthenticationConverter))
                )
                .addFilterAfter(new TenantResolutionFilter(tenantSlugResolver), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(userActivationFilter, TenantResolutionFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOriginPatterns.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
