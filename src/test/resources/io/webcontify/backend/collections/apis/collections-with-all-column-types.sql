CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
INSERT INTO WEBCONTIFY_COLLECTION(NAME,DISPLAY_NAME) VALUES('all_column_types_primary_number','All Column Types Primary Number');
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_number'),'number_column','Primary Column','NUMBER',true);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_number'),'decimal_column','Decimal Column','DECIMAL',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_number'),'text_column','Text Column','TEXT',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_number'),'timestamp_column','Timestamp Column','TIMESTAMP',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_number'),'boolean_column','Boolean Column','BOOLEAN',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_number'),'uuid_column','Uuid Column','UUID',false);

CREATE TABLE IF NOT EXISTS ALL_COLUMN_TYPES_PRIMARY_NUMBER (
    number_column bigint GENERATED ALWAYS AS IDENTITY,
    decimal_column decimal,
    text_column text,
    timestamp_column timestamp,
    boolean_column boolean,
    uuid_column uuid,
    constraint PK_ALL_COLUMN_TYPES_PRIMARY_NUMBER primary key (number_column)
);

INSERT INTO WEBCONTIFY_COLLECTION(NAME,DISPLAY_NAME) VALUES('all_column_types_primary_uuid','All Column Types Primary Uuid');
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_uuid'),'number_column','Primary Column','NUMBER',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_uuid'),'decimal_column','Decimal Column','DECIMAL',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_uuid'),'text_column','Text Column','TEXT',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_uuid'),'timestamp_column','Timestamp Column','TIMESTAMP',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_uuid'),'boolean_column','Boolean Column','BOOLEAN',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES((SELECT ID FROM WEBCONTIFY_COLLECTION WHERE name = 'all_column_types_primary_uuid'),'uuid_column','Uuid Column','UUID',true);

CREATE TABLE IF NOT EXISTS ALL_COLUMN_TYPES_PRIMARY_UUID (
                                                               number_column bigint,
                                                               decimal_column decimal,
                                                               text_column text,
                                                               timestamp_column timestamp,
                                                               boolean_column boolean,
                                                               uuid_column uuid NOT NULL DEFAULT uuid_generate_v1(),
                                                               constraint PK_ALL_COLUMN_TYPES_PRIMARY_UUID primary key (uuid_column)
);