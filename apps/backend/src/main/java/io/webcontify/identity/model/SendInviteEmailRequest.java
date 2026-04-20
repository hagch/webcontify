package io.webcontify.identity.model;

import java.util.UUID;

public record SendInviteEmailRequest(String realmName, UUID userId) {}
