package io.webcontify.identity.model;

public record OpenFgaReadTuplesRequest(String storeId, String objectType, String objectId, String relation) {}
