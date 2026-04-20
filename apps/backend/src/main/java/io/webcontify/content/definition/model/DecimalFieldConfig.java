package io.webcontify.content.definition.model;

import java.math.BigDecimal;

public record DecimalFieldConfig(
        BigDecimal min, BigDecimal max, Integer precision, Integer scale, BigDecimal defaultValue
) implements FieldConfig {}
