package io.webcontify.identity.model;

public record CreateRealmRequest(String tenantSlug, SmtpConfig platformSmtpConfig) {}
