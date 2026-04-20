package io.webcontify.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.smtp")
public record PlatformSmtpProperties(
        String host,
        int port,
        String username,
        String password,
        boolean starttls
) {}
