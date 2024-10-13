package io.webcontify.backend.collections.models.errors

enum class ErrorCode(var message: String) {
  INTERNAL_SERVER_ERROR("Unhandled error occurred"),
  INVALID_REQUEST_BODY("Request body cannot be processed"),
  INVALID_PATH_PARAMETERS("Atleast one primary key field has to be specified"),
  INVALID_NAME(
      "Name is not in camelCase, first letter must be lowercase and only single numbers are allowed"),
  NAME_REQUIRED("Name cannot be null or empty"),
  TYPE_NON_NULLABLE("Type cannot be null"),
  FIELD_REQUIRED("At least one field is required for collection creation"),
  GET_ITEM_COLLECTION_WITHOUT_FIELDS("Collection without fields cannot have items"),
  DELETE_ITEM_FROM_COLLECTION_WITHOUT_FIELDS("Cannot delete item from collection without fields"),
  UPDATE_ITEM_FROM_COLLECTION_WITHOUT_FIELDS("Cannot update item from collection without fields"),
  PRIMARY_KEYS_UNEQUAL(
      "Primary key count of collection does not match primary keys of request path parameters"),
  PRIMARY_KEY_NOT_INCLUDED("Primary key %s is not included in request path parameters"),
  CAN_NOT_CAST_VALUE("Can not cast value %s for key %s"),
  UNDEFINED_FIELD("Field is not defined for key %s"),
  NO_HANDLER_FOR_FIELD_TYPE("Field %s has unsupported type of %s"),
  FIELD_WITH_ID_NOT_FOUND("Field with id %s does not exist"),
  FIELD_NOT_FOUND("Field with id %s does not exist for collection with id %s"),
  FIELD_NOT_UPDATED("Field with id %s for collection with id %s does not exist"),
  FIELD_WITH_NAME_ALREADY_EXISTS("Field with name %s for collection with id %s already exist"),
  COLLECTION_NOT_FOUND("Collection with id %s does not exist"),
  COLLECTION_NOT_UPDATED("Collection with name %s could not be updated"),
  COLLECTION_WITH_NAME_ALREADY_EXISTS("Collection with name %s already exist"),
  UNABLE_TO_CREATE_FIELD("Could not create field with name %s for collection with id"),
  NO_FIELDS_TO_UPDATE("No update able field values present for item (%s) in collection with id %s"),
  UNSUPPORTED_FIELD_OPERATION("Changing type or isPrimaryKey is not supported"),
  FIELD_IS_PRIMARY_FIELD("Field with id %s of collection with id %s used as primary field"),
  FIELD_USED_IN_RELATION("Field with id %s of collection with id %s used in relation"),
  UNABLE_TO_RENAME_FIELD("Could not rename field with name %s to %s for collection with id %s"),
  UNABLE_TO_CREATE_COLLECTION("Could not create collection without primary fields"),
  UNABLE_TO_UPDATE_TABLE_NAME("Could not update table name from %s to %s"),
  ITEM_NOT_FOUND("Item with key %s does not exist for collection with id %s"),
  UNABLE_TO_RETRIEVE_ITEM("Could not load item with key %s for collection with id %s"),
  UNABLE_TO_DELETE_ITEM("Could not delete item with key %s for collection with id %s"),
  UNABLE_TO_CREATE_ITEM("Could not create item with values %s for collection with id %s"),
  CONSTRAINT_EXCEPTION("Could not save item with values %s for collection with id %s"),
  MIRROR_FIELD_INCLUDED("Mirror fields are currently not supported in operations"),
  ITEM_ALREADY_EXISTS("Item with key %s for collection with id %s already exists"),
  UNABLE_TO_RETRIEVE_ITEMS("Could not load items for collection with id %s"),
  ITEM_NOT_UPDATED("Could not update item with values %s for collection with id %s"),
  CANNOT_DELETE_COLLECTION(
      "Could not delete collection with id %s, because collection has existing fields"),
  INVALID_VALUE_PASSED(
      "Value %s for field %s is invalid, please check if value complies to configuration %s"),
  INVALID_IN_VALUE_CONFIGURATION(
      "Value %s for inValue configuration of field %s is invalid, please check if inValues are of same type of field type %s")
}
