CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(1,'decimal_collection_constraints','Unique decimal collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(1,1,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(2,1,'decimal_field','decimal Field','DECIMAL',false,'{ "scale": null, "greaterThan": null, "lowerThan": null, "nullable": false, "unique": true, "inValues": [ 10.1, 20.1, 30.0, 40.0, 50.0, 60.0, 70.0, 81.1, 90.0 ], "defaultValue": 81.1 }');

CREATE TABLE IF NOT EXISTS decimal_COLLECTION_CONSTRAINTS (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    decimal_field DECIMAL NOT NULL DEFAULT 81.1,
    constraint PK_decimal_COLLECTION_CONSTRAINTS primary key (uuid_field)
);


INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(2,'decimal_collection_nullable_constraint','Nullable decimal collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(3,2,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(4,2,'decimal_field','decimal Field','DECIMAL',false,'{ "scale": null, "greaterThan": null, "lowerThan": null, "nullable": true, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS decimal_collection_nullable_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    decimal_field DECIMAL,
    constraint PK_decimal_COLLECTION_NULLABLE_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(3,'decimal_collection_not_nullable_constraint','Not Nullable decimal collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(5,3,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(6,3,'decimal_field','decimal Field','DECIMAL',false,'{ "scale": null, "greaterThan": null, "lowerThan": null, "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS decimal_collection_not_nullable_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    decimal_field DECIMAL NOT NULL,
    constraint PK_decimal_COLLECTION_NOT_NULLABLE_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(4,'decimal_collection_greater_lower_constraint','Greater Lower decimal collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(7,4,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(8,4,'decimal_field','decimal Field','DECIMAL',false,'{ "scale": null, "greaterThan": 10.5, "lowerThan": 13.1, "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS decimal_collection_greater_lower_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    decimal_field DECIMAL NOT NULL,
    constraint PK_decimal_COLLECTION_GREATER_LOWER_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(5,'decimal_collection_precision_scale_constraint','Precision Scale decimal collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(9,5,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(10,5,'decimal_field','decimal Field','DECIMAL',false,'{ "scale": 3, "greaterThan": null, "lowerThan": null, "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS decimal_collection_precision_scale_constraint (
                                                                           uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    decimal_field DECIMAL(1000,3) NOT NULL,
    constraint PK_decimal_COLLECTION_PRECISION_SCALE_CONSTRAINT primary key (uuid_field)
    );