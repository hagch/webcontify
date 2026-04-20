package io.webcontify.identity.model;

import java.util.Map;

public record OpenFgaBatchCheckResponse(Map<String, Boolean> results) {}
