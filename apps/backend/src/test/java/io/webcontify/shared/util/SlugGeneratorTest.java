package io.webcontify.shared.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    private final SlugGenerator slugGenerator = new SlugGenerator();

    @ParameterizedTest
    @CsvSource({
        "Acme Corporation, acme-corporation",
        "My Company, my-company",
        "UPPER CASE, upper-case",
        "already-slug, already-slug",
        "  spaces  around  , spaces-around",
        "Über Grüße, ueber-gruesse",
        "Café & Bar, cafe-bar",
        "test--double, test-double",
        "123 Numbers, 123-numbers"
    })
    void shouldGenerateSlugFromName(String name, String expectedSlug) {
        assertThat(slugGenerator.generate(name)).isEqualTo(expectedSlug);
    }

    @Test
    void shouldGenerateSlugWithSuffixWhenDuplicate() {
        assertThat(slugGenerator.generateUnique("Acme", slug -> slug.equals("acme")))
            .isEqualTo("acme-2");
    }

    @Test
    void shouldIncrementSuffixUntilUnique() {
        assertThat(slugGenerator.generateUnique("Acme", slug -> slug.equals("acme") || slug.equals("acme-2")))
            .isEqualTo("acme-3");
    }

    @Test
    void shouldReturnBaseSlugWhenNoConflict() {
        assertThat(slugGenerator.generateUnique("Acme", slug -> false))
            .isEqualTo("acme");
    }
}
