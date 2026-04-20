package io.webcontify.identity.model;

import java.util.List;

public record OpenFgaWriteRequest(String storeId, List<RelationshipTuple> tuples) {}
