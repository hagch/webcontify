package io.webcontify.content.definition.model;

import java.util.UUID;

public record UndoRedoResult(
        boolean success,
        String entityType,
        UUID entityId,
        String changeType,
        boolean canUndo,
        boolean canRedo) {}
