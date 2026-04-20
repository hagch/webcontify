package io.webcontify.identity.model;

import java.util.List;

public record OpenFgaDeleteRequest(String storeId, List<RelationshipTuple> tuples) {}
