drop table if exists WEBCONTIFY_COLLECTION CASCADE;
drop table if exists WEBCONTIFY_COLLECTION_COLUMN CASCADE;
drop type if exists WEBCONTIFY_COLLECTION_COLUMN_TYPE CASCADE;

create table WEBCONTIFY_COLLECTION(
                                      ID SERIAL PRIMARY KEY,
                                      NAME TEXT NOT NULL,
                                      DISPLAY_NAME TEXT NOT NULL
);
create type WEBCONTIFY_COLLECTION_COLUMN_TYPE as enum (
    'NUMBER',
    'DECIMAL',
    'SHORT_TEXT',
    'LONG_TEXT',
    'TIMESTAMP',
    'CURRENCY',
    'BOOLEAN'
    );
create table WEBCONTIFY_COLLECTION_COLUMN(
                                             COLLECTION_ID SERIAL REFERENCES WEBCONTIFY_COLLECTION(ID),
                                             NAME TEXT,
                                             DISPLAY_NAME TEXT NOT NULL,
                                             TYPE WEBCONTIFY_COLLECTION_COLUMN_TYPE NOT NULL,
                                             IS_PRIMARY_KEY BOOLEAN NOT NULL,
                                             UNIQUE (COLLECTION_ID, IS_PRIMARY_KEY),
                                             PRIMARY KEY (COLLECTION_ID, NAME)
);
