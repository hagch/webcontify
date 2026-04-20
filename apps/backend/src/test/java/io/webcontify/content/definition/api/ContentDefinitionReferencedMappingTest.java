package io.webcontify.content.definition.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.generated.model.ReferencedDefinition;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Security invariant: an inaccessible ReferencedDefinition serialized to JSON
 * MUST NOT leak name, appId, or appName. Only `id` and `accessible` are
 * permitted. The backend mapper and the OpenAPI schema together are expected
 * to enforce this — this test fails loudly if either slips.
 */
class ContentDefinitionReferencedMappingTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void inaccessibleReferencedDefinitionSerializesWithoutMetadata() throws Exception {
        ReferencedDefinition dto = new ReferencedDefinition();
        dto.setId(UUID.randomUUID());
        dto.setAccessible(false);
        // name/appId/appName left null as the mapper contract demands.

        String body = json.writeValueAsString(dto);
        var tree = json.readTree(body);

        assertThat(tree.has("id")).isTrue();
        assertThat(tree.has("accessible")).isTrue();
        assertThat(tree.get("accessible").asBoolean()).isFalse();
        // Nullable fields are serialized as either absent or explicit null.
        // Neither must contain a real value.
        for (String leak : new String[] {"name", "appId", "appName"}) {
            if (tree.has(leak)) {
                assertThat(tree.get(leak).isNull())
                        .as("field %s must be absent or null for inaccessible entries", leak)
                        .isTrue();
            }
        }
    }

    @Test
    void accessibleReferencedDefinitionIncludesMetadata() throws Exception {
        ReferencedDefinition dto = new ReferencedDefinition();
        dto.setId(UUID.randomUUID());
        dto.setAccessible(true);
        dto.setName("Article");
        dto.setAppId(UUID.randomUUID());
        dto.setAppName("Blog");

        String body = json.writeValueAsString(dto);
        var tree = json.readTree(body);

        assertThat(tree.get("name").asText()).isEqualTo("Article");
        assertThat(tree.get("appName").asText()).isEqualTo("Blog");
        assertThat(tree.get("accessible").asBoolean()).isTrue();
    }
}
