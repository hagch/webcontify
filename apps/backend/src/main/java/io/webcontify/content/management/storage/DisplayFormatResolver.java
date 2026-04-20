package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Resolves display format templates for content entries by replacing
 * {@code {cf_xxx}} placeholders with formatted field values.
 */
@Component
public class DisplayFormatResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(cf_[a-z0-9_]+)}");

    private final FieldTypeRegistry fieldTypeRegistry;

    public DisplayFormatResolver(FieldTypeRegistry fieldTypeRegistry) {
        this.fieldTypeRegistry = fieldTypeRegistry;
    }

    /**
     * Resolves the display format template for a content definition by substituting
     * placeholders with formatted values. Returns the entry ID as fallback when no
     * template is defined.
     */
    public String resolve(ContentDefinition def, Map<String, Object> values) {
        String template = def.getDisplayFormat();
        if (template == null || template.isBlank()) {
            Object id = values.get("id");
            return id != null ? id.toString() : "";
        }

        Map<String, FieldDefinition> fieldsByKey = buildFieldIndex(def);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String fieldKey = placeholder.substring(3); // strip "cf_" prefix
            FieldDefinition field = fieldsByKey.get(fieldKey);
            String replacement;

            if (field == null) {
                replacement = "";
            } else {
                Object value = values.get(placeholder);
                if (value == null) {
                    replacement = "";
                } else {
                    var handler = fieldTypeRegistry.get(field.getFieldType());
                    replacement = handler.formatForDisplay(value, field.getConfig());
                }
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Extracts the field keys (without cf_ prefix) referenced in a display format template.
     */
    public List<String> extractSearchFields(String template) {
        if (template == null || template.isBlank()) {
            return List.of();
        }

        List<String> fields = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            fields.add(matcher.group(1));
        }
        return fields;
    }

    private Map<String, FieldDefinition> buildFieldIndex(ContentDefinition def) {
        var fields = def.getFields();
        if (fields == null) return Map.of();
        var map = new java.util.HashMap<String, FieldDefinition>();
        for (var field : fields) {
            map.put(field.getFieldKey(), field);
        }
        return map;
    }
}
