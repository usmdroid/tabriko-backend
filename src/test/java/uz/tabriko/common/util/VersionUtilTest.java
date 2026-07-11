package uz.tabriko.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VersionUtilTest {

    // --- compare ---

    @Test
    void compare_specExample_1_0_9_lessThan_1_0_18() {
        assertThat(VersionUtil.compare("1.0.9", "1.0.18")).isLessThan(0);
    }

    @Test
    void compare_equal_versions() {
        assertThat(VersionUtil.compare("1.2.3", "1.2.3")).isEqualTo(0);
    }

    @Test
    void compare_shortVersion_equalToLongWithTrailingZeros() {
        assertThat(VersionUtil.compare("1.0", "1.0.0")).isEqualTo(0);
    }

    @Test
    void compare_majorVersionDifference() {
        assertThat(VersionUtil.compare("2.0.0", "1.9.9")).isGreaterThan(0);
    }

    @Test
    void compare_minorVersionDifference() {
        assertThat(VersionUtil.compare("1.3.0", "1.2.9")).isGreaterThan(0);
    }

    @Test
    void compare_nonNumericComponentDoesNotThrow() {
        assertThatCode(() -> VersionUtil.compare("1.0.alpha", "1.0.0")).doesNotThrowAnyException();
    }

    @Test
    void compare_nullInputsDoNotThrow() {
        assertThatCode(() -> VersionUtil.compare(null, "1.0.0")).doesNotThrowAnyException();
        assertThatCode(() -> VersionUtil.compare("1.0.0", null)).doesNotThrowAnyException();
    }

    // --- isInRange ---

    @Test
    void isInRange_bothBounds_insideRange() {
        assertThat(VersionUtil.isInRange("1.0.10", "1.0.5", "1.0.18")).isTrue();
    }

    @Test
    void isInRange_bothBounds_belowMin() {
        assertThat(VersionUtil.isInRange("1.0.4", "1.0.5", "1.0.18")).isFalse();
    }

    @Test
    void isInRange_bothBounds_equalToMax_excluded() {
        assertThat(VersionUtil.isInRange("1.0.18", "1.0.5", "1.0.18")).isFalse();
    }

    @Test
    void isInRange_equalToMin_included() {
        assertThat(VersionUtil.isInRange("1.0.5", "1.0.5", "1.0.18")).isTrue();
    }

    @Test
    void isInRange_onlyMinBound() {
        assertThat(VersionUtil.isInRange("2.0.0", "1.0.0", null)).isTrue();
        assertThat(VersionUtil.isInRange("0.9.0", "1.0.0", null)).isFalse();
    }

    @Test
    void isInRange_onlyMaxBound() {
        assertThat(VersionUtil.isInRange("1.0.17", null, "1.0.18")).isTrue();
        assertThat(VersionUtil.isInRange("1.0.18", null, "1.0.18")).isFalse();
    }

    @Test
    void isInRange_noBounds_matchesAnyNonBlankVersion() {
        assertThat(VersionUtil.isInRange("0.0.1", null, null)).isTrue();
        assertThat(VersionUtil.isInRange("99.99.99", null, null)).isTrue();
    }

    @Test
    void isInRange_nullVersion_returnsFalse() {
        assertThat(VersionUtil.isInRange(null, null, null)).isFalse();
    }

    @Test
    void isInRange_blankVersion_returnsFalse() {
        assertThat(VersionUtil.isInRange("  ", null, null)).isFalse();
    }
}
