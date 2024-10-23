package helpers.suppliers.respones

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.webcontify.backend.collections.services.field.handler.*
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType

data class WebContifyCollectionFieldResponse(
    val id: Long?,
    val collectionId: Long?,
    val name: String,
    val displayName: String = name,
    val type: WebcontifyCollectionFieldType,
    val isPrimaryKey: Boolean,
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldTextConfigurationResponse::class,
            name = TEXT_FIELD_TYPE),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldNumberConfigurationResponse::class,
            name = NUMBER_FIELD_TYPE),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldDecimalConfigurationResponse::class,
            name = DECIMAL_FIELD_TYPE),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldTimestampConfigurationResponse::class,
            name = TIMESTAMP_FIELD_TYPE),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldUuidConfigurationResponse::class,
            name = UUID_FIELD_TYPE),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldBooleanConfigurationResponse::class,
            name = BOOLEAN_FIELD_TYPE),
    )
    val configuration: WebContifyCollectionFieldConfigurationResponse<Any>?
) {}
