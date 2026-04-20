package io.webcontify.identity.model;

public record SmtpConfig(String host, int port, String username, String password, boolean starttls) {}
