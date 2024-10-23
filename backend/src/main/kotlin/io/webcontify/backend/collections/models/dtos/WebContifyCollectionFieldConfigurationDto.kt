package io.webcontify.backend.collections.models.dtos

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

open class WebContifyCollectionFieldConfigurationDto<out T>(
    open val nullable: Boolean?,
    open var unique: Boolean?,
    open var inValues: List<@UnsafeVariance T?>?,
    open var defaultValue: @UnsafeVariance T?
)

class WebContifyCollectionFieldNumberConfigurationDto(
    val greaterThan: Long?,
    val lowerThan: Long?,
    override val nullable: Boolean?,
    override var unique: Boolean?,
    override var inValues: List<Long?>?,
    override var defaultValue: Long?
) : WebContifyCollectionFieldConfigurationDto<Long>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldTextConfigurationDto(
    val regex: String?,
    val maxLength: Int?,
    val minLength: Int?,
    override val nullable: Boolean?,
    override var unique: Boolean?,
    override var inValues: List<String?>?,
    override var defaultValue: String?
) : WebContifyCollectionFieldConfigurationDto<String>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldDecimalConfigurationDto(
    val greaterThan: BigDecimal?,
    val lowerThan: BigDecimal?,
    val scale: Int?,
    override val nullable: Boolean?,
    override var unique: Boolean?,
    override var inValues: List<BigDecimal?>?,
    override var defaultValue: BigDecimal?
) : WebContifyCollectionFieldConfigurationDto<BigDecimal>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldTimestampConfigurationDto(
    val greaterThan: LocalDateTime?,
    val lowerThan: LocalDateTime?,
    override val nullable: Boolean?,
    override var unique: Boolean?,
    override var inValues: List<LocalDateTime?>?,
    override var defaultValue: LocalDateTime?
) :
    WebContifyCollectionFieldConfigurationDto<LocalDateTime>(
        nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldUuidConfigurationDto(
    override val nullable: Boolean?,
    override var unique: Boolean?,
    override var inValues: List<UUID?>?,
    override var defaultValue: UUID?
) : WebContifyCollectionFieldConfigurationDto<UUID>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionFieldBooleanConfigurationDto(
    override val nullable: Boolean?,
    override var unique: Boolean?,
    override var inValues: List<Boolean?>?,
    override var defaultValue: Boolean?
) : WebContifyCollectionFieldConfigurationDto<Boolean>(nullable, unique, inValues, defaultValue)
