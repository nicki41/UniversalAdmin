package dev.universaladmin.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@code MAJOR.MINOR.PATCH[-PRERELEASE]} version, ordered per SemVer 2.0:
 * a pre-release sorts before its release ({@code 1.0.0-alpha.5 < 1.0.0}),
 * numeric pre-release identifiers compare numerically
 * ({@code alpha.2 < alpha.10}). Used so the update check only ever offers an
 * actually <i>newer</i> release - never a "downgrade" to an older one, e.g.
 * when a server runs a development build.
 */
record SemanticVersion(int major, int minor, int patch, List<String> preRelease) implements Comparable<SemanticVersion> {

    private static final Pattern PATTERN = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+.*)?$");

    SemanticVersion {
        preRelease = List.copyOf(preRelease);
    }

    static Optional<SemanticVersion> parse(String text) {
        Matcher matcher = PATTERN.matcher(text == null ? "" : text.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        List<String> pre = new ArrayList<>();
        if (matcher.group(4) != null) {
            pre.addAll(List.of(matcher.group(4).split("\\.")));
        }
        return Optional.of(new SemanticVersion(
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)), pre));
    }

    /**
     * Whether {@code candidate} is newer than {@code current}. If either
     * cannot be parsed, falls back to "different means newer" - the behaviour
     * before versions were compared at all.
     */
    static boolean isNewer(String candidate, String current) {
        Optional<SemanticVersion> a = parse(candidate);
        Optional<SemanticVersion> b = parse(current);
        if (a.isEmpty() || b.isEmpty()) {
            return !String.valueOf(candidate).equals(current);
        }
        return a.get().compareTo(b.get()) > 0;
    }

    /**
     * Whether both versions share their major version - what an automatic
     * update stays within. {@code false} if either cannot be parsed.
     */
    static boolean sameMajor(String a, String b) {
        Optional<SemanticVersion> first = parse(a);
        Optional<SemanticVersion> second = parse(b);
        return first.isPresent() && second.isPresent() && first.get().major() == second.get().major();
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) {
            result = Integer.compare(minor, other.minor);
        }
        if (result == 0) {
            result = Integer.compare(patch, other.patch);
        }
        if (result != 0) {
            return result;
        }
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            // A release outranks any pre-release of the same version.
            return Boolean.compare(preRelease.isEmpty(), other.preRelease.isEmpty());
        }
        for (int i = 0; i < Math.min(preRelease.size(), other.preRelease.size()); i++) {
            int identifier = compareIdentifier(preRelease.get(i), other.preRelease.get(i));
            if (identifier != 0) {
                return identifier;
            }
        }
        return Integer.compare(preRelease.size(), other.preRelease.size());
    }

    private static int compareIdentifier(String a, String b) {
        boolean numericA = a.chars().allMatch(Character::isDigit);
        boolean numericB = b.chars().allMatch(Character::isDigit);
        if (numericA && numericB) {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        }
        if (numericA != numericB) {
            return numericA ? -1 : 1;
        }
        return a.compareTo(b);
    }
}
