CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(1,'number_collection_constraints','Unique Number collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(1,1,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(2,1,'number_field','Number Field','NUMBER',false,'{ "greaterThan": null, "lowerThan": null, "nullable": false, "unique": true, "inValues": [ 10, 20, 30, 40, 50, 60, 70, 80, 90 ], "defaultValue": 10 }');

CREATE TABLE IF NOT EXISTS NUMBER_COLLECTION_CONSTRAINTS (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    number_field bigint NOT NULL DEFAULT 10,
    constraint PK_NUMBER_COLLECTION_CONSTRAINTS primary key (uuid_field)
);


INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(2,'number_collection_nullable_constraint','Nullable Number collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(3,2,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(4,2,'number_field','Number Field','NUMBER',false,'{ "greaterThan": null, "lowerThan": null, "nullable": true, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS number_collection_nullable_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    number_field bigint,
    constraint PK_NUMBER_COLLECTION_NULLABLE_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(3,'number_collection_not_nullable_constraint','Not Nullable Number collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(5,3,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(6,3,'number_field','Number Field','NUMBER',false,'{ "greaterThan": null, "lowerThan": null, "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS number_collection_not_nullable_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    number_field bigint NOT NULL,
    constraint PK_NUMBER_COLLECTION_NOT_NULLABLE_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(4,'number_collection_greater_lower_constraint','Greater Lower Number collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(7,4,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(8,4,'number_field','Number Field','NUMBER',false,'{ "greaterThan": 10, "lowerThan": 13, "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS number_collection_greater_lower_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    number_field bigint NOT NULL,
    constraint PK_NUMBER_COLLECTION_GREATER_LOWER_CONSTRAINT primary key (uuid_field)
    );