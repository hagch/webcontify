package io.webcontify.shared.web;

/**
 * CORS is configured via {@code CorsConfigurationSource} Bean in
 * {@code io.webcontify.shared.security.SecurityConfiguration}. Spring Security's CorsFilter takes
 * precedence over WebMvcConfigurer for secured endpoints.
 */
final class CorsConfiguration {
    private CorsConfiguration() {}
}
