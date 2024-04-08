CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(1,'BOOLEAN_collection_constraints','Unique BOOLEAN collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(1,1,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(2,1,'BOOLEAN_field','BOOLEAN Field','BOOLEAN',false,'{ "nullable": false, "unique": true, "inValues": [ true ], "defaultValue": true }');

CREATE TABLE IF NOT EXISTS BOOLEAN_COLLECTION_CONSTRAINTS (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    BOOLEAN_field BOOLEAN NOT NULL DEFAULT true,
    constraint PK_BOOLEAN_COLLECTION_CONSTRAINTS primary key (uuid_field)
);


INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(2,'BOOLEAN_collection_nullable_constraint','Nullable BOOLEAN collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(3,2,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(4,2,'BOOLEAN_field','BOOLEAN Field','BOOLEAN',false,'{ "nullable": true, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS BOOLEAN_collection_nullable_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    BOOLEAN_field BOOLEAN,
    constraint PK_BOOLEAN_COLLECTION_NULLABLE_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(3,'BOOLEAN_collection_not_nullable_constraint','Not Nullable BOOLEAN collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(5,3,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(6,3,'BOOLEAN_field','BOOLEAN Field','BOOLEAN',false,'{ "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS BOOLEAN_collection_not_nullable_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    BOOLEAN_field BOOLEAN NOT NULL,
    constraint PK_BOOLEAN_COLLECTION_NOT_NULLABLE_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(4,'BOOLEAN_collection_greater_lower_constraint','Greater Lower BOOLEAN collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(7,4,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(8,4,'BOOLEAN_field','BOOLEAN Field','BOOLEAN',false,'{ "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS BOOLEAN_collection_greater_lower_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    BOOLEAN_field BOOLEAN NOT NULL,
    constraint PK_BOOLEAN_COLLECTION_GREATER_LOWER_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(5,'BOOLEAN_collection_precision_scale_constraint','Precision Scale BOOLEAN collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(9,5,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(10,5,'BOOLEAN_field','BOOLEAN Field','BOOLEAN',false,'{ "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS BOOLEAN_collection_precision_scale_constraint (
                                                                           uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    BOOLEAN_field BOOLEAN NOT NULL,
    constraint PK_BOOLEAN_COLLECTION_PRECISION_SCALE_CONSTRAINT primary key (uuid_field)
    );