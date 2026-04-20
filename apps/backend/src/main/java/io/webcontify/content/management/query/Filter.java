package io.webcontify.content.management.query;

import io.webcontify.content.fieldtype.Operator;

public record Filter(String column, Operator operator, Object rawValue) {}
