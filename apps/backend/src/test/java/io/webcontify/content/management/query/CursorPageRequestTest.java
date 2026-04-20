package io.webcontify.content.management.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CursorPageRequestTest {

    @Test
    void effectiveSize_withinBounds_returnsSameValue() {
        assertThat(new CursorPageRequest(null, 50).effectiveSize()).isEqualTo(50);
    }

    @Test
    void effectiveSize_atMinBound_returnsOne() {
        assertThat(new CursorPageRequest(null, 1).effectiveSize()).isEqualTo(1);
    }

    @Test
    void effectiveSize_atMaxBound_returnsHundred() {
        assertThat(new CursorPageRequest(null, 100).effectiveSize()).isEqualTo(100);
    }

    @Test
    void effectiveSize_belowMin_returnsDefault() {
        assertThat(new CursorPageRequest(null, 0).effectiveSize()).isEqualTo(20);
        assertThat(new CursorPageRequest(null, -1).effectiveSize()).isEqualTo(20);
    }

    @Test
    void effectiveSize_aboveMax_returnsDefault() {
        assertThat(new CursorPageRequest(null, 101).effectiveSize()).isEqualTo(20);
        assertThat(new CursorPageRequest(null, 500).effectiveSize()).isEqualTo(20);
    }

    @Test
    void effectiveSize_defaultSize_returnsDefault() {
        assertThat(new CursorPageRequest(null, 20).effectiveSize()).isEqualTo(20);
    }
}
