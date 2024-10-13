package io.webcontify.backend.collections.models.dtos

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

open class WebContifyCollectionFieldConfigurationDto<out T>(
    open val nullable: Boolean?,
    open val unique: Boolean?,
    open val inValues: List<T?>?,
    open val defaultValue: T?
)

class WebContifyCollectionFieldNumberConfigurationDto(
    val greaterThan: Long?,
    val lowerThan: Long?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Long?>?,
    override val defaultValue: Long?
) : WebContifyCollectionFieldConfigurationDto<Long>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldTextConfigurationDto(
    val regex: String?,
    val maxLength: Int?,
    val minLength: Int?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<String?>?,
    override val defaultValue: String?
) : WebContifyCollectionFieldConfigurationDto<String>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldDecimalConfigurationDto(
    val greaterThan: BigDecimal?,
    val lowerThan: BigDecimal?,
    val scale: Int?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<BigDecimal?>?,
    override val defaultValue: BigDecimal?
) : WebContifyCollectionFieldConfigurationDto<BigDecimal>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldTimestampConfigurationDto(
    val greaterThan: LocalDateTime?,
    val lowerThan: LocalDateTime?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<LocalDateTime?>?,
    override val defaultValue: LocalDateTime?
) :
    WebContifyCollectionFieldConfigurationDto<LocalDateTime>(
        nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldUuidConfigurationDto(
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<UUID?>?,
    override val defaultValue: UUID?
) : WebContifyCollectionFieldConfigurationDto<UUID>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldBooleanConfigurationDto(
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Boolean?>?,
    override val defaultValue: Boolean?
) : WebContifyCollectionFieldConfigurationDto<Boolean>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldRelationMirrorConfigurationDto(
    val relationId: Long,
    val referencedField: Long,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Any?>?,
    override val defaultValue: Any?
) : WebContifyCollectionFieldConfigurationDto<Any>(nullable, unique, inValues, defaultValue)
