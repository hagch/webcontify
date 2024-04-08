CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(1,'TIMESTAMP_collection_constraints','Unique TIMESTAMP collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(1,1,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(2,1,'TIMESTAMP_field','TIMESTAMP Field','TIMESTAMP',false,'{ "greaterThan": null, "lowerThan": null, "nullable": false, "unique": true, "inValues": [ "2000-10-31T01:30:00", "2000-10-31T01:30:02" ], "defaultValue": "2000-10-31T01:30:00" }');

CREATE TABLE IF NOT EXISTS TIMESTAMP_COLLECTION_CONSTRAINTS (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    TIMESTAMP_field TIMESTAMP NOT NULL DEFAULT TO_TIMESTAMP('2000-10-31T01:30:00', 'YYYY-MM-DD"T"HH24:MI:SS'),
    constraint PK_TIMESTAMP_COLLECTION_CONSTRAINTS primary key (uuid_field)
);


INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(2,'TIMESTAMP_collection_nullable_constraint','Nullable TIMESTAMP collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(3,2,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(4,2,'TIMESTAMP_field','TIMESTAMP Field','TIMESTAMP',false,'{ "greaterThan": null, "lowerThan": null, "nullable": true, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS TIMESTAMP_collection_nullable_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    TIMESTAMP_field TIMESTAMP,
    constraint PK_TIMESTAMP_COLLECTION_NULLABLE_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(3,'TIMESTAMP_collection_not_nullable_constraint','Not Nullable TIMESTAMP collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(5,3,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(6,3,'TIMESTAMP_field','TIMESTAMP Field','TIMESTAMP',false,'{ "greaterThan": null, "lowerThan": null, "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS TIMESTAMP_collection_not_nullable_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    TIMESTAMP_field TIMESTAMP NOT NULL,
    constraint PK_TIMESTAMP_COLLECTION_NOT_NULLABLE_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(4,'TIMESTAMP_collection_greater_lower_constraint','Greater Lower TIMESTAMP collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(7,4,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(8,4,'TIMESTAMP_field','TIMESTAMP Field','TIMESTAMP',false,'{ "greaterThan": "2000-10-31T01:30:00", "lowerThan": "2000-10-31T02:30:00", "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS TIMESTAMP_collection_greater_lower_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    TIMESTAMP_field TIMESTAMP NOT NULL,
    constraint PK_TIMESTAMP_COLLECTION_GREATER_LOWER_CONSTRAINT primary key (uuid_field),
    constraint check_greather_then check (TIMESTAMP_field > TO_TIMESTAMP('2000-10-31T01:30:00', 'YYYY-MM-DD"T"HH24:MI:SS')),
    constraint check_lower_than_length check (TIMESTAMP_field < TO_TIMESTAMP('2000-10-31T02:30:00', 'YYYY-MM-DD"T"HH24:MI:SS'))
    );