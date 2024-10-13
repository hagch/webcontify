CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(1,'all_field_types_primary_number','All Field Types Primary Number');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(2,1,'number_field','Primary Field','NUMBER',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(3,1,'decimal_field','Decimal Field','DECIMAL',false);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(4,1,'text_field','Text Field','TEXT',false);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(5,1,'timestamp_field','Timestamp Field','TIMESTAMP',false);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(6,1,'boolean_field','Boolean Field','BOOLEAN',false);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(7,1,'uuid_field','Uuid Field','UUID',false);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(13,1,'mirror_field','Mirror Field','RELATION_MIRROR',false);

CREATE TABLE IF NOT EXISTS ALL_FIELD_TYPES_PRIMARY_NUMBER (
    number_field bigint GENERATED ALWAYS AS IDENTITY,
    decimal_field decimal,
    text_field text,
    timestamp_field timestamp,
    boolean_field boolean,
    uuid_field uuid,
    constraint PK_ALL_FIELD_TYPES_PRIMARY_NUMBER primary key (number_field)
);

INSERT INTO ALL_FIELD_TYPES_PRIMARY_NUMBER (decimal_field, text_field, timestamp_field, boolean_field, uuid_field) VALUES (null, null, null, null, null);

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(2,'all_field_types_primary_uuid','All Field Types Primary Uuid');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(8,2,'number_field','Primary Field','NUMBER',false);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(9,2,'decimal_field','Decimal Field','DECIMAL',false);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(10,2,'text_field','Text Field','TEXT',false);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(11,2,'timestamp_field','Timestamp Field','TIMESTAMP',false);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(12,2,'boolean_field','Boolean Field','BOOLEAN',false);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(14,2,'uuid_field','Uuid Field','UUID',true);

CREATE TABLE IF NOT EXISTS ALL_FIELD_TYPES_PRIMARY_UUID (
                                                               number_field bigint,
                                                               decimal_field decimal,
                                                               text_field text,
                                                               timestamp_field timestamp,
                                                               boolean_field boolean,
                                                               uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
                                                               constraint PK_ALL_FIELD_TYPES_PRIMARY_UUID primary key (uuid_field)
);

INSERT INTO ALL_FIELD_TYPES_PRIMARY_UUID (uuid_field,number_field, decimal_field, text_field, timestamp_field, boolean_field) VALUES (UUID('7e0a036d-0e99-42b3-a4e0-c55694ad04f4'),null, null, null, null, null);