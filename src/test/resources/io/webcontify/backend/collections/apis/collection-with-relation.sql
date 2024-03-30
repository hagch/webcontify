CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
INSERT INTO WEBCONTIFY_COLLECTION VALUES(1,'related_collection','RelatedCollection');
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN VALUES(1,'number_column','Primary Column','NUMBER',true);

CREATE TABLE IF NOT EXISTS RELATED_COLLECTION (
    number_column bigint GENERATED ALWAYS AS IDENTITY,
    constraint PK_RELATED_COLLECTION_NUMBER primary key (number_column)
);

INSERT INTO WEBCONTIFY_COLLECTION VALUES(2,'collection_with_relation','CollectionWithRelation');
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN VALUES(2,'number_column','Primary Column','NUMBER',true);
INSERT INTO WEBCONTIFY_COLLECTION_COLUMN VALUES(2,'related_column','Related Column','NUMBER',false);

INSERT INTO WEBCONTIFY_COLLECTION_RELATION (source_collection_id, referenced_collection_id, type, name, display_name) VALUES (2, 1, 'MANY_TO_ONE', 'relation', 'Relation');
INSERT INTO WEBCONTIFY_COLLECTION_RELATION_FIELD(source_collection_id, source_collection_column_name, referenced_collection_id, referenced_collection_column_name, type, name) VALUES (2, 'related_column', 1, 'number_column', 'MANY_TO_ONE', 'relation');
INSERT INTO WEBCONTIFY_COLLECTION_RELATION (source_collection_id, referenced_collection_id, type, name, display_name) VALUES (1, 2, 'ONE_TO_MANY', 'relation', 'Relation');
INSERT INTO WEBCONTIFY_COLLECTION_RELATION_FIELD(source_collection_id, source_collection_column_name, referenced_collection_id, referenced_collection_column_name, type, name) VALUES (1, 'number_column',2, 'related_column', 'ONE_TO_MANY', 'relation');

CREATE TABLE IF NOT EXISTS COLLECTION_WITH_RELATION (
    number_column bigint GENERATED ALWAYS AS IDENTITY,
    related_column bigint,
    constraint PK_COLLECTION_WITH_RELATION_NUMBER primary key (number_column),
    FOREIGN KEY(related_column) references RELATED_COLLECTION(number_column)
);