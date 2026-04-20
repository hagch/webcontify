package io.webcontify.content.fieldtype;

import io.webcontify.content.definition.model.FieldType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@Component
public class FieldTypeRegistry {

    private final Map<FieldType, FieldTypeHandler> handlers;

    public FieldTypeRegistry(List<FieldTypeHandler> all) {
        this.handlers = all.stream().collect(toMap(FieldTypeHandler::fieldType, h -> h));
    }

    public FieldTypeHandler get(FieldType type) {
        var h = handlers.get(type);
        if (h == null) {
            throw new IllegalStateException("No handler for " + type);
        }
        return h;
    }
}
