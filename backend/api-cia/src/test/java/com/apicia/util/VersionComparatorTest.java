package com.apicia.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicia.model.entity.SpecVersion;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

public class VersionComparatorTest {

    @Test
    public void testCompareSemVer() {
        // Basic versions
        assertTrue(VersionComparator.compareVersions("1.0.0", "2.0.0") < 0);
        assertTrue(VersionComparator.compareVersions("2.1.0", "2.0.0") > 0);
        assertEquals(0, VersionComparator.compareVersions("1.2.3", "1.2.3"));

        // Prerelease/SNAPSHOT comparison
        assertTrue(VersionComparator.compareVersions("1.1.0-SNAPSHOT", "1.1.0") < 0);
        assertTrue(VersionComparator.compareVersions("1.1.0", "1.1.0-SNAPSHOT") > 0);
        assertTrue(VersionComparator.compareVersions("1.1.0-SNAPSHOT", "1.1.0-M1") > 0);

        // Leading 'v' cleaning
        assertEquals(0, VersionComparator.compareVersions("v1.2.3", "1.2.3"));
        assertEquals(0, VersionComparator.compareVersions("V1.2.3", "v1.2.3"));
    }

    @Test
    public void testCompareNonSemVer() {
        // Non-SemVer lexicographical
        assertTrue(VersionComparator.compareVersions("abc", "def") < 0);
        assertTrue(VersionComparator.compareVersions("local", "dev") > 0);
        assertEquals(0, VersionComparator.compareVersions("local", "local"));

        // SemVer vs Non-SemVer (SemVer is greater)
        assertTrue(VersionComparator.compareVersions("1.0.0", "local") > 0);
        assertTrue(VersionComparator.compareVersions("local", "1.0.0") < 0);
    }

    @Test
    public void testSpecVersionComparator() {
        VersionComparator comparator = new VersionComparator();

        SpecVersion sv1 = SpecVersion.builder()
                .id(1L)
                .version("1.0.0")
                .uploadedAt(LocalDateTime.of(2026, 8, 3, 10, 0))
                .build();

        SpecVersion sv2 = SpecVersion.builder()
                .id(2L)
                .version("1.1.0")
                .uploadedAt(LocalDateTime.of(2026, 8, 3, 11, 0))
                .build();

        // Higher version should be greater
        assertTrue(comparator.compare(sv1, sv2) < 0);
        assertTrue(comparator.compare(sv2, sv1) > 0);

        // Same version, different time (later uploadedAt is greater)
        SpecVersion sv3 = SpecVersion.builder()
                .id(3L)
                .version("1.0.0")
                .uploadedAt(LocalDateTime.of(2026, 8, 3, 10, 30))
                .build();
        assertTrue(comparator.compare(sv1, sv3) < 0);

        // Same version, same time, different ID (later ID is greater)
        SpecVersion sv4 = SpecVersion.builder()
                .id(4L)
                .version("1.0.0")
                .uploadedAt(LocalDateTime.of(2026, 8, 3, 10, 0))
                .build();
        assertTrue(comparator.compare(sv1, sv4) < 0);
    }
}
