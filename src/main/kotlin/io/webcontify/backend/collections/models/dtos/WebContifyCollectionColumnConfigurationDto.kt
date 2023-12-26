package io.webcontify.backend.collections.models.dtos

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

open class WebContifyCollectionColumnConfigurationDto<out T>(
    open val nullable: Boolean?,
    open val unique: Boolean?,
    open val inValues: List<T?>?,
    open val defaultValue: T?
)

class WebContifyCollectionColumnNumberConfigurationDto(
    val greaterThan: Long?,
    val lowerThan: Long?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Long?>?,
    override val defaultValue: Long?
) : WebContifyCollectionColumnConfigurationDto<Long>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionColumnTextConfigurationDto(
    val regex: String?,
    val maxLength: Int?,
    val minLength: Int?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<String?>?,
    override val defaultValue: String?
) : WebContifyCollectionColumnConfigurationDto<String>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionColumnDecimalConfigurationDto(
    val greaterThan: BigDecimal?,
    val lowerThan: BigDecimal?,
    val precision: Int?,
    val scale: Int?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<BigDecimal?>?,
    override val defaultValue: BigDecimal?
) :
    WebContifyCollectionColumnConfigurationDto<BigDecimal>(
        nullable, unique, inValues, defaultValue)

class WebContifyCollectionColumnTimestampConfigurationDto(
    val greaterThan: LocalDateTime?,
    val lowerThan: LocalDateTime?,
    val format: String?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<LocalDateTime?>?,
    override val defaultValue: LocalDateTime?
) :
    WebContifyCollectionColumnConfigurationDto<LocalDateTime>(
        nullable, unique, inValues, defaultValue)

class WebContifyCollectionColumnUuidConfigurationDto(
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<UUID?>?,
    override val defaultValue: UUID?
) : WebContifyCollectionColumnConfigurationDto<UUID>(nullable, unique, inValues, defaultValue)

class WebContifyCollectionColumnBooleanConfigurationDto(
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Boolean?>?,
    override val defaultValue: Boolean?
) : WebContifyCollectionColumnConfigurationDto<Boolean>(nullable, unique, inValues, defaultValue)
