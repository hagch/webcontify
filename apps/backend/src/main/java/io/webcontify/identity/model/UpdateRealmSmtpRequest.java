package io.webcontify.identity.model;

public record UpdateRealmSmtpRequest(String realmName, SmtpConfig smtpConfig) {}
