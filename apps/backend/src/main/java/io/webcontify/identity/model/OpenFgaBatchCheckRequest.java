package io.webcontify.identity.model;

import java.util.List;

public record OpenFgaBatchCheckRequest(String storeId, List<OpenFgaCheckRequest> checks) {}
