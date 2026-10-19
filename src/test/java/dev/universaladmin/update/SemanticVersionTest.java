package dev.universaladmin.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SemanticVersionTest {

    @Test
    void aReleaseIsNewerThanItsPreReleases() {
        assertTrue(SemanticVersion.isNewer("1.0.0", "1.0.0-rc.1"));
        assertTrue(SemanticVersion.isNewer("1.0.0", "0.1.0-alpha.5"));
        assertFalse(SemanticVersion.isNewer("1.0.0-rc.1", "1.0.0"));
    }

    @Test
    void numericPreReleaseIdentifiersCompareNumerically() {
        assertTrue(SemanticVersion.isNewer("0.1.0-alpha.10", "0.1.0-alpha.9"));
        assertTrue(SemanticVersion.isNewer("1.0.0-beta", "1.0.0-alpha.9"));
        assertTrue(SemanticVersion.isNewer("1.0.0-alpha.1", "1.0.0-alpha"));
    }

    @Test
    void neverOffersADowngradeOrTheSameVersion() {
        assertFalse(SemanticVersion.isNewer("1.0.0", "1.0.1"));
        assertFalse(SemanticVersion.isNewer("1.2.0", "1.10.0"));
        assertFalse(SemanticVersion.isNewer("1.0.0", "1.0.0"));
        assertFalse(SemanticVersion.isNewer("v1.0.0", "1.0.0"));
    }

    @Test
    void unparsableVersionsFallBackToInequality() {
        assertTrue(SemanticVersion.isNewer("nightly", "1.0.0"));
        assertFalse(SemanticVersion.isNewer("nightly", "nightly"));
    }
}
