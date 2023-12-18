package io.webcontify.backend.collections.models.dtos

import java.time.LocalDateTime

open class WebContifyCollectionColumnConfigurationDto(
    open val nullable: Boolean?,
    open val unique: Boolean?,
    open val inValues: List<Any?>?,
    open val defaultValue: Any?
)

class WebContifyCollectionColumnNumberConfigurationDto(
    val greaterThan: Long?,
    val lowerThan: Long?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Any?>?,
    override val defaultValue: Any?
) : WebContifyCollectionColumnConfigurationDto(nullable, unique, inValues, defaultValue)

class WebContifyCollectionColumnTextConfigurationDto(
    val regex: String?,
    val maxLength: Int?,
    val minLength: Int?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Any?>?,
    override val defaultValue: Any?
) : WebContifyCollectionColumnConfigurationDto(nullable, unique, inValues, defaultValue)

class WebContifyCollectionColumnDecimalConfigurationDto(
    val greaterThan: Double?,
    val lowerThan: Double?,
    val precision: Int?,
    val scale: Int?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Any?>?,
    override val defaultValue: Any?
) : WebContifyCollectionColumnConfigurationDto(nullable, unique, inValues, defaultValue)

class WebContifyCollectionColumnTimestampConfigurationDto(
    val greaterThan: LocalDateTime?,
    val lowerThan: LocalDateTime?,
    val format: String?,
    override val nullable: Boolean?,
    override val unique: Boolean?,
    override val inValues: List<Any?>?,
    override val defaultValue: Any?
) : WebContifyCollectionColumnConfigurationDto(nullable, unique, inValues, defaultValue)
