package io.webcontify.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidGeneratorTest {

    @Test
    void shouldGenerateUuidV7() {
        UUID uuid = UuidGenerator.generate();

        // UUIDv7 has version bits = 7 (bits 48-51)
        assertThat(uuid.version()).isEqualTo(7);
    }

    @Test
    void shouldGenerateMonotonicallyIncreasingUuids() {
        UUID first = UuidGenerator.generate();
        UUID second = UuidGenerator.generate();

        // UUIDv7 is time-ordered, so second should be greater
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void shouldGenerateUniqueUuids() {
        UUID first = UuidGenerator.generate();
        UUID second = UuidGenerator.generate();

        assertThat(first).isNotEqualTo(second);
    }
}
