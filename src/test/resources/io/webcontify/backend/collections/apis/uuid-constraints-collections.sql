CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(1,'UUID_collection_constraints','Unique UUID collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(1,1,'primary_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(2,1,'UUID_field','UUID Field','UUID',false,'{ "nullable": false, "unique": true, "inValues": [ "97dafc8c-77f6-44ac-beca-fa5dbbf3346f", "9b5ae74f-49e4-4a6f-bbf0-c6b622c77f63" ], "defaultValue": "97dafc8c-77f6-44ac-beca-fa5dbbf3346f" }');

CREATE TABLE IF NOT EXISTS UUID_COLLECTION_CONSTRAINTS (
    primary_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    UUID_field UUID NOT NULL DEFAULT uuid('97dafc8c-77f6-44ac-beca-fa5dbbf3346f'),
    constraint PK_UUID_COLLECTION_CONSTRAINTS primary key (primary_field)
);


INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(2,'UUID_collection_nullable_constraint','Nullable UUID collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(3,2,'primary_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(4,2,'UUID_field','UUID Field','UUID',false,'{ "nullable": true, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS UUID_collection_nullable_constraint (
    primary_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    UUID_field UUID,
    constraint PK_UUID_COLLECTION_NULLABLE_CONSTRAINT primary key (primary_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(3,'UUID_collection_not_nullable_constraint','Not Nullable UUID collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(5,3,'primary_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(6,3,'UUID_field','UUID Field','UUID',false,'{ "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS UUID_collection_not_nullable_constraint (
    primary_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    UUID_field UUID NOT NULL,
    constraint PK_UUID_COLLECTION_NOT_NULLABLE_CONSTRAINT primary key (primary_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(4,'UUID_collection_greater_lower_constraint','Greater Lower UUID collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(7,4,'primary_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(8,4,'UUID_field','UUID Field','UUID',false,'{ "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS UUID_collection_greater_lower_constraint (
    primary_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    UUID_field UUID NOT NULL,
    constraint PK_UUID_COLLECTION_GREATER_LOWER_CONSTRAINT primary key (primary_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(5,'UUID_collection_precision_scale_constraint','Precision Scale UUID collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(9,5,'primary_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(10,5,'UUID_field','UUID Field','UUID',false,'{ "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS UUID_collection_precision_scale_constraint (
    primary_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    UUID_field UUID NOT NULL,
    constraint PK_UUID_COLLECTION_PRECISION_SCALE_CONSTRAINT primary key (primary_field)
    );