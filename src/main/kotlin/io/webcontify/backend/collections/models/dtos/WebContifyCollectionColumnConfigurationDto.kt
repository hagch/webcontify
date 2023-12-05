package io.webcontify.backend.collections.models.dtos

import java.math.BigDecimal
import java.time.LocalDateTime

open class WebContifyCollectionColumnConfigurationDto(
    open val nullable: Boolean?,
    open val unique: Boolean?,
    open val inValues: List<LabelValue>?
)

data class LabelValue(val label: String, val value: Any?)

class WebContifyCollectionColumnNumberConfigurationDto(
    val greaterThan: Long?,
    val lowerThan: Long?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<LabelValue>?
) : WebContifyCollectionColumnConfigurationDto(nullable, unique, inValues)

class WebContifyCollectionColumnTextConfigurationDto(
    val regex: String,
    val maxLength: Long?,
    val minLength: Long?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<LabelValue>?
) : WebContifyCollectionColumnConfigurationDto(nullable, unique, inValues)

class WebContifyCollectionColumnDecimalConfigurationDto(
    val greaterThan: Double?,
    val lowerThan: Double?,
    val precision: Long?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<LabelValue>?
) : WebContifyCollectionColumnConfigurationDto(nullable, unique, inValues)

class WebContifyCollectionColumnTimestampConfigurationDto(
    val greaterThan: LocalDateTime?,
    val lowerThan: LocalDateTime?,
    val format: String?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<LabelValue>?
) : WebContifyCollectionColumnConfigurationDto(nullable, unique, inValues)

class WebContifyCollectionColumnCurrencyConfigurationDto(
    val greaterThan: BigDecimal?,
    val lowerThan: BigDecimal?,
    val precision: Long?,
    val currency: String?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<LabelValue>?
) : WebContifyCollectionColumnConfigurationDto(nullable, unique, inValues)
