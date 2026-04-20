package io.webcontify.content.management.ddl;

import io.webcontify.content.definition.repository.entity.ContentFieldEntity;

/**
 * Represents a single DDL change to be applied during app publish.
 *
 * @param action the type of DDL operation
 * @param field  the field entity involved (null for table-level actions like CREATE_TABLE/DROP_TABLE)
 */
public record DdlChange(DdlAction action, ContentFieldEntity field) {}
