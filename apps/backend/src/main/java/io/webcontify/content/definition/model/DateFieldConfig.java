package io.webcontify.content.definition.model;

import java.time.LocalDate;

public record DateFieldConfig(
        LocalDate minDate, LocalDate maxDate, String dateFormat, LocalDate defaultValue
) implements FieldConfig {}
