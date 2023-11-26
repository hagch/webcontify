package io.webcontify.backend.collections.models.errors

enum class ErrorCode(var message: String) {
  INTERNAL_SERVER_ERROR("Unhandled error occurred"),
  INVALID_REQUEST_BODY(""),
  INVALID_PATH_PARAMETERS("Atleast one primary key column has to be specified"),
  INVALID_NAME("Name cannot have leading or ending '_', following values are allowed 0-9, a-z, _"),
  TYPE_NON_NULLABLE("Type cannot be null"),
  COLUMN_REQUIRED("At least one column is required for collection creation"),
  GET_ITEM_COLLECTION_WITHOUT_COLUMNS("Collection without columns cannot have items"),
  DELETE_ITEM_FROM_COLLECTION_WITHOUT_COLUMNS("Cannot delete item from collection without columns"),
  UPDATE_ITEM_FROM_COLLECTION_WITHOUT_COLUMNS("Cannot update item from collection without columns"),
  PRIMARY_KEYS_UNEQUAL(
      "Primary key count of collection does not match primary keys of request path parameters"),
  PRIMARY_KEY_NOT_INCLUDED("Primary key %s is not included in request path parameters"),
  CAN_NOT_CAST_VALUE("Can not cast value %s for key %s"),
  UNDEFINED_COLUMN("Column is not defined for key %s"),
  NO_HANDLER_FOR_COLUMN_TYPE("Column %s has unsupported type of %s"),
  COLUMN_NOT_FOUND("Column with name %s does not exist for collection with id %s"),
  COLUMN_NOT_UPDATED("Column with name %s for collection with id %s does not exist"),
  COLUMN_WITH_NAME_ALREADY_EXISTS("Column with name %s for collection with id %s already exist"),
  COLLECTION_NOT_FOUND("Collection with id %s does not exist"),
  COLLECTION_NOT_UPDATED("Collection with name %s could not be updated"),
  COLLECTION_WITH_NAME_ALREADY_EXISTS("Collection with name %s already exist"),
  UNABLE_TO_CREATE_COLUMN("Could not create column with name %s for collection with id"),
  UNSUPPORTED_COLUMN_OPERATION("Currently changing type or is primary key is not supported"),
  UNABLE_TO_RENAME_COLUMN("Could not rename column with name %s to %s for collection with id %s"),
  UNABLE_TO_CREATE_TABLE("Could not create table without primary keys"),
  UNABLE_TO_UPDATE_TABLE_NAME("Could not update table name from %s to %s"),
  ITEM_NOT_FOUND("Item with key %s does not exist for collection with id %s"),
  UNABLE_TO_RETRIEVE_ITEM("Could not load item with key %s for collection with id %s"),
  UNABLE_TO_DELETE_ITEM("Could not delete item with key %s for collection with id %s"),
  UNABLE_TO_CREATE_ITEM("Could not create item with values %s for collection with id %s"),
  ITEM_ALREADY_EXISTS("Item with key %s for collection with id %s already exists"),
  UNABLE_TO_RETRIEVE_ITEMS("Could not load items for collection with id %s"),
  ITEM_NOT_UPDATED("Could not update item with values %s for collection with id %s"),
  CANNOT_DELETE_COLLECTION(
      "Could not delete collection with id %s, because collection has existing columns")
}
