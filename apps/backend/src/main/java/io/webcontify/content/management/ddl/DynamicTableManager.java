package io.webcontify.content.management.ddl;

import io.webcontify.content.definition.mapper.FieldConfigConverter;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Name;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages DDL operations (CREATE/ALTER/DROP TABLE) for per-definition content tables
 * in tenant-specific schemas. Uses jOOQ DSL for safe identifier quoting.
 */
@Component
public class DynamicTableManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicTableManager.class);

    private final DSLContext dsl;
    private final FieldTypeRegistry fieldTypeRegistry;
    private final FieldConfigConverter fieldConfigConverter;

    public DynamicTableManager(
            DSLContext dsl,
            FieldTypeRegistry fieldTypeRegistry,
            FieldConfigConverter fieldConfigConverter) {
        this.dsl = dsl;
        this.fieldTypeRegistry = fieldTypeRegistry;
        this.fieldConfigConverter = fieldConfigConverter;
    }

    /**
     * Creates the content table and companion history table for a definition.
     */
    @Transactional
    public void createTable(String schemaName, ContentDefinitionEntity definition) {
        String normalizedSchema = normalize(schemaName);
        String tableName = "cd_" + normalize(definition.getSlug());
        Name qualifiedTable = DSL.name(normalizedSchema, tableName);

        log.info("Creating table {}.{}", normalizedSchema, tableName);

        setLockTimeout();

        var createStep = dsl.createTable(qualifiedTable)
                .column(DSL.name("id"), SQLDataType.UUID.nullable(false))
                .column(DSL.name("version"), SQLDataType.BIGINT.nullable(false).defaultValue(DSL.inline(0L)))
                .column(DSL.name("created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
                .column(DSL.name("updated_at"), SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
                .column(DSL.name("created_by"), SQLDataType.UUID.nullable(false))
                .column(DSL.name("updated_by"), SQLDataType.UUID.nullable(false))
                .column(DSL.name("deleted_at"), SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(true))
                .column(DSL.name("deleted_by"), SQLDataType.UUID.nullable(true));

        // Add user-defined columns (skip virtual fields like MIRROR)
        List<ContentFieldEntity> fields = definition.getFields();
        if (fields != null) {
            for (ContentFieldEntity field : fields) {
                FieldType fieldType = FieldType.valueOf(field.getFieldType());
                FieldTypeHandler handler = fieldTypeRegistry.get(fieldType);
                if (handler.isVirtual()) continue;

                var config = fieldConfigConverter.fromJson(field.getConfig(), fieldType);
                String columnName = "cf_" + field.getFieldKey();
                DataType<?> dataType = handler.columnType(config);
                if (!field.isRequired()) {
                    dataType = dataType.nullable(true);
                } else {
                    dataType = dataType.nullable(false);
                }
                createStep = createStep.column(DSL.name(columnName), dataType);
            }
        }

        createStep.constraint(DSL.constraint("pk_" + tableName).primaryKey(DSL.name("id")))
                .execute();

        // Create indexes for sortable fields
        if (fields != null) {
            for (ContentFieldEntity field : fields) {
                FieldType fieldType = FieldType.valueOf(field.getFieldType());
                FieldTypeHandler handler = fieldTypeRegistry.get(fieldType);
                if (handler.isVirtual()) continue;

                String columnName = "cf_" + field.getFieldKey();
                if (field.isSortable()) {
                    dsl.createIndex(DSL.name("idx_" + tableName + "_" + field.getFieldKey()))
                            .on(qualifiedTable, DSL.name(columnName), DSL.name("id"))
                            .execute();
                }
                if (field.isUniqueValue()) {
                    dsl.execute(
                            "CREATE UNIQUE INDEX " + DSL.name("ux_" + tableName + "_" + field.getFieldKey()).quotedName().toString()
                                    + " ON " + qualifiedTable.quotedName().toString()
                                    + " (" + DSL.name(columnName).quotedName().toString() + ")"
                                    + " WHERE deleted_at IS NULL");
                }
            }
        }

        // Create companion history table
        createHistoryTable(normalizedSchema, tableName);

        log.info("Table {}.{} created successfully", normalizedSchema, tableName);
    }

    /**
     * Applies a list of DDL changes to an existing content table.
     */
    @Transactional
    public void applyChanges(String schemaName, ContentDefinitionEntity definition, List<DdlChange> changes) {
        String normalizedSchema = normalize(schemaName);
        String tableName = "cd_" + normalize(definition.getSlug());
        Name qualifiedTable = DSL.name(normalizedSchema, tableName);

        for (DdlChange change : changes) {
            setLockTimeout();

            switch (change.action()) {
                case ADD_COLUMN -> {
                    ContentFieldEntity field = change.field();
                    FieldType fieldType = FieldType.valueOf(field.getFieldType());
                    FieldTypeHandler handler = fieldTypeRegistry.get(fieldType);
                    if (handler.isVirtual()) continue;

                    var config = fieldConfigConverter.fromJson(field.getConfig(), fieldType);
                    String columnName = "cf_" + field.getFieldKey();
                    DataType<?> dataType = handler.columnType(config);
                    // New columns on existing tables are always nullable for safety
                    dataType = dataType.nullable(true);

                    dsl.alterTable(qualifiedTable)
                            .addColumn(DSL.name(columnName), dataType)
                            .execute();

                    log.info("Added column {} to {}.{}", columnName, normalizedSchema, tableName);
                }
                case DROP_COLUMN -> {
                    ContentFieldEntity field = change.field();
                    String columnName = "cf_" + field.getFieldKey();

                    dsl.alterTable(qualifiedTable)
                            .dropColumnIfExists(DSL.name(columnName))
                            .execute();

                    log.info("Dropped column {} from {}.{}", columnName, normalizedSchema, tableName);
                }
                case ADD_INDEX -> {
                    ContentFieldEntity field = change.field();
                    String columnName = "cf_" + field.getFieldKey();

                    dsl.createIndex(DSL.name("idx_" + tableName + "_" + field.getFieldKey()))
                            .on(qualifiedTable, DSL.name(columnName), DSL.name("id"))
                            .execute();

                    log.info("Added index on {} for {}.{}", columnName, normalizedSchema, tableName);
                }
                case ADD_UNIQUE_INDEX -> {
                    ContentFieldEntity field = change.field();
                    String columnName = "cf_" + field.getFieldKey();

                    dsl.execute(
                            "CREATE UNIQUE INDEX " + DSL.name("ux_" + tableName + "_" + field.getFieldKey()).quotedName().toString()
                                    + " ON " + qualifiedTable.quotedName().toString()
                                    + " (" + DSL.name(columnName).quotedName().toString() + ")"
                                    + " WHERE deleted_at IS NULL");

                    log.info("Added unique index on {} for {}.{}", columnName, normalizedSchema, tableName);
                }
                case CREATE_TABLE, DROP_TABLE -> {
                    // These are handled by createTable/dropTable directly
                }
            }
        }
    }

    /**
     * Drops both the content table and its companion history table.
     */
    @Transactional
    public void dropTable(String schemaName, ContentDefinitionEntity definition) {
        String normalizedSchema = normalize(schemaName);
        String tableName = "cd_" + normalize(definition.getSlug());

        setLockTimeout();

        dsl.dropTableIfExists(DSL.name(normalizedSchema, tableName + "_history")).execute();
        dsl.dropTableIfExists(DSL.name(normalizedSchema, tableName)).execute();

        log.info("Dropped tables {}.{} and {}.{}_history", normalizedSchema, tableName, normalizedSchema, tableName);
    }

    private void createHistoryTable(String schema, String contentTableName) {
        String historyTableName = contentTableName + "_history";
        Name qualifiedHistoryTable = DSL.name(schema, historyTableName);

        dsl.createTable(qualifiedHistoryTable)
                .column(DSL.name("id"), SQLDataType.UUID.nullable(false))
                .column(DSL.name("entry_id"), SQLDataType.UUID.nullable(false))
                .column(DSL.name("action"), SQLDataType.VARCHAR(20).nullable(false))
                .column(DSL.name("changed_fields"), SQLDataType.JSONB.nullable(true))
                .column(DSL.name("changed_at"), SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
                .column(DSL.name("changed_by"), SQLDataType.UUID.nullable(false))
                .constraint(DSL.constraint("pk_" + historyTableName).primaryKey(DSL.name("id")))
                .execute();

        dsl.createIndex(DSL.name("idx_" + historyTableName + "_entry_id"))
                .on(qualifiedHistoryTable, DSL.name("entry_id"))
                .execute();
    }

    private void setLockTimeout() {
        dsl.execute("SET LOCAL lock_timeout = '5s'");
    }

    private String normalize(String value) {
        return value.replace("-", "_");
    }
}
