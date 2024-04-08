package helpers.suppliers.respones

import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

open class WebContifyCollectionFieldConfigurationResponse<out T>(
    open val nullable: Boolean?,
    open val unique: Boolean?,
    open val inValues: List<T?>?,
    open val defaultValue: T?
)

class WebContifyCollectionFieldNumberConfigurationResponse(
    val greaterThan: Long?,
    val lowerThan: Long?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Long?>?,
    override val defaultValue: Long?
) : WebContifyCollectionFieldConfigurationResponse<Long>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldTextConfigurationResponse(
    val regex: String?,
    val maxLength: Int?,
    val minLength: Int?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<String?>?,
    override val defaultValue: String?
) :
    WebContifyCollectionFieldConfigurationResponse<String>(
        nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldDecimalConfigurationResponse(
    val greaterThan: BigDecimal?,
    val lowerThan: BigDecimal?,
    val precision: Int?,
    val scale: Int?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<BigDecimal?>?,
    override val defaultValue: BigDecimal?
) :
    WebContifyCollectionFieldConfigurationResponse<BigDecimal>(
        nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldTimestampConfigurationResponse(
    val greaterThan: LocalDateTime?,
    val lowerThan: LocalDateTime?,
    val format: String?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<LocalDateTime?>?,
    override val defaultValue: LocalDateTime?
) :
    WebContifyCollectionFieldConfigurationResponse<LocalDateTime>(
        nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldUuidConfigurationResponse(
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<UUID?>?,
    override val defaultValue: UUID?
) : WebContifyCollectionFieldConfigurationResponse<UUID>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldBooleanConfigurationResponse(
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Boolean?>?,
    override val defaultValue: Boolean?
) :
    WebContifyCollectionFieldConfigurationResponse<Boolean>(
        nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldRelationMirrorConfigurationResponse(
    val relationId: Long,
    val fieldType: WebcontifyCollectionFieldType,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Any?>?,
    override val defaultValue: Any?
) : WebContifyCollectionFieldConfigurationResponse<Any>(nullable, unique, inValues, defaultValue)
