package com.apicia.util;

import com.apicia.model.entity.SpecVersion;
import java.util.Comparator;

public class VersionComparator implements Comparator<SpecVersion> {

    @Override
    public int compare(SpecVersion sv1, SpecVersion sv2) {
        if (sv1 == null && sv2 == null) return 0;
        if (sv1 == null) return -1;
        if (sv2 == null) return 1;

        int comp = compareVersions(sv1.getVersion(), sv2.getVersion());
        if (comp != 0) {
            return comp;
        }

        // Fallback: Chronological order (uploadedAt or id)
        if (sv1.getUploadedAt() != null && sv2.getUploadedAt() != null) {
            int timeComp = sv1.getUploadedAt().compareTo(sv2.getUploadedAt());
            if (timeComp != 0) {
                return timeComp;
            }
        }

        if (sv1.getId() != null && sv2.getId() != null) {
            return sv1.getId().compareTo(sv2.getId());
        }

        return 0;
    }

    public static int compareVersions(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String clean1 = cleanVersion(v1);
        String clean2 = cleanVersion(v2);

        if (clean1.equals(clean2)) {
            return 0;
        }

        boolean isSem1 = isSemVer(clean1);
        boolean isSem2 = isSemVer(clean2);

        if (isSem1 && isSem2) {
            return compareSemVer(clean1, clean2);
        }

        if (isSem1) return 1;
        if (isSem2) return -1;

        return clean1.compareTo(clean2);
    }

    private static String cleanVersion(String v) {
        if (v == null) return "";
        String trimmed = v.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    private static boolean isSemVer(String v) {
        return v.matches("^\\d+\\.\\d+\\.\\d+(?:-.*)?$");
    }

    private static int compareSemVer(String v1, String v2) {
        String[] parts1 = v1.split("-", 2);
        String[] parts2 = v2.split("-", 2);

        String[] numbers1 = parts1[0].split("\\.");
        String[] numbers2 = parts2[0].split("\\.");

        for (int i = 0; i < 3; i++) {
            int n1 = Integer.parseInt(numbers1[i]);
            int n2 = Integer.parseInt(numbers2[i]);
            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }

        boolean hasPre1 = parts1.length > 1;
        boolean hasPre2 = parts2.length > 1;

        if (hasPre1 && !hasPre2) {
            return -1; // prerelease is lower than normal release
        }
        if (!hasPre1 && hasPre2) {
            return 1;
        }
        if (!hasPre1 && !hasPre2) {
            return 0;
        }

        return comparePrerelease(parts1[1], parts2[1]);
    }

    private static int comparePrerelease(String pre1, String pre2) {
        String[] segs1 = pre1.split("\\.");
        String[] segs2 = pre2.split("\\.");

        int len = Math.min(segs1.length, segs2.length);
        for (int i = 0; i < len; i++) {
            String s1 = segs1[i];
            String s2 = segs2[i];

            boolean isNum1 = s1.matches("^\\d+$");
            boolean isNum2 = s2.matches("^\\d+$");

            if (isNum1 && isNum2) {
                int n1 = Integer.parseInt(s1);
                int n2 = Integer.parseInt(s2);
                if (n1 != n2) {
                    return Integer.compare(n1, n2);
                }
            } else if (isNum1) {
                return -1;
            } else if (isNum2) {
                return 1;
            } else {
                int comp = s1.compareTo(s2);
                if (comp != 0) {
                    return comp;
                }
            }
        }
        return Integer.compare(segs1.length, segs2.length);
    }
}
