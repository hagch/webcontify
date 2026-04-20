package io.webcontify.identity.model;

import java.util.Map;

public record UpdateRealmBrandingRequest(String realmName, Map<String, String> brandingAttributes) {}
