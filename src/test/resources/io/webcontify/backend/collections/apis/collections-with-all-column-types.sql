CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(1,'all_column_types_primary_number','All Column Types Primary Number');
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(1,1,'number_column','Primary Column','NUMBER',true);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(2,1,'decimal_column','Decimal Column','DECIMAL',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(3,1,'text_column','Text Column','TEXT',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(4,1,'timestamp_column','Timestamp Column','TIMESTAMP',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(5,1,'boolean_column','Boolean Column','BOOLEAN',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(6,1,'uuid_column','Uuid Column','UUID',false);

CREATE TABLE IF NOT EXISTS ALL_COLUMN_TYPES_PRIMARY_NUMBER (
    number_column bigint GENERATED ALWAYS AS IDENTITY,
    decimal_column decimal,
    text_column text,
    timestamp_column timestamp,
    boolean_column boolean,
    uuid_column uuid,
    constraint PK_ALL_COLUMN_TYPES_PRIMARY_NUMBER primary key (number_column)
);

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(2,'all_column_types_primary_uuid','All Column Types Primary Uuid');
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(7,2,'number_column','Primary Column','NUMBER',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(8,2,'decimal_column','Decimal Column','DECIMAL',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(9,2,'text_column','Text Column','TEXT',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(10,2,'timestamp_column','Timestamp Column','TIMESTAMP',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(11,2,'boolean_column','Boolean Column','BOOLEAN',false);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(12,2,'uuid_column','Uuid Column','UUID',true);

CREATE TABLE IF NOT EXISTS ALL_COLUMN_TYPES_PRIMARY_UUID (
                                                               number_column bigint,
                                                               decimal_column decimal,
                                                               text_column text,
                                                               timestamp_column timestamp,
                                                               boolean_column boolean,
                                                               uuid_column uuid NOT NULL DEFAULT uuid_generate_v1(),
                                                               constraint PK_ALL_COLUMN_TYPES_PRIMARY_UUID primary key (uuid_column)
);