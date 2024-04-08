CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(1,'text_collection_constraints','Unique text collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(1,1,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(2,1,'text_field','text Field','TEXT',false,'{ "regex": null, "maxLength": null, "minLength": null, "nullable": false, "unique": true, "inValues": [ "test","teste" ], "defaultValue": "test" }');

CREATE TABLE IF NOT EXISTS text_COLLECTION_CONSTRAINTS (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    text_field TEXT NOT NULL DEFAULT 'test',
    constraint PK_text_COLLECTION_CONSTRAINTS primary key (uuid_field)
);


INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(2,'text_collection_nullable_constraint','Nullable text collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(3,2,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(4,2,'text_field','text Field','TEXT',false,'{ "regex": null, "maxLength": null, "minLength": null, "nullable": true, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS text_collection_nullable_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    text_field TEXT,
    constraint PK_text_COLLECTION_NULLABLE_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(3,'text_collection_not_nullable_constraint','Not Nullable text collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(5,3,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(6,3,'text_field','text Field','TEXT',false,'{ "regex": null, "maxLength": null, "minLength": null, "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS text_collection_not_nullable_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    text_field TEXT NOT NULL,
    constraint PK_text_COLLECTION_NOT_NULLABLE_CONSTRAINT primary key (uuid_field)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(4,'text_collection_max_min_constraint','Greater Lower text collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(7,4,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(8,4,'text_field','text Field','TEXT',false,'{ "regex": null, "maxLength": 5, "minLength": 3, "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS text_collection_max_min_constraint (
    uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    text_field TEXT NOT NULL,
    constraint PK_text_COLLECTION_MAX_MIN_CONSTRAINT primary key (uuid_field),
    constraint check_min_length check (length(text_field) >= 3),
    constraint check_max_length check (length(text_field) <= 5)
    );

INSERT INTO WEBCONTIFY_COLLECTION(ID,NAME,DISPLAY_NAME) VALUES(5,'text_collection_regex_constraint','Greater Lower text collection');
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY) VALUES(9,5,'uuid_field','Primary Field','UUID',true);
INSERT INTO WEBCONTIFY_COLLECTION_FIELD(ID,COLLECTION_ID,NAME,DISPLAY_NAME,TYPE,IS_PRIMARY_KEY,CONFIGURATION) VALUES(10,5,'text_field','text Field','TEXT',false,'{ "regex": "(\\W|^)[\\w.\\-]{0,25}@(yahoo|hotmail|gmail)\\.com(\\W|$)", "maxLength": null, "minLength": null, "nullable": false, "unique": false, "inValues": null, "defaultValue": null }');

CREATE TABLE IF NOT EXISTS text_collection_regex_constraint (
                                                                        uuid_field uuid NOT NULL DEFAULT uuid_generate_v1(),
    text_field TEXT NOT NULL,
    constraint PK_text_COLLECTION_REGEX_CONSTRAINT primary key (uuid_field),
    constraint regex_field check (text_field ~ '(\W|^)[\w.\-]{0,25}@(yahoo|hotmail|gmail)\.com(\W|$)')
    );