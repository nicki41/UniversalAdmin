package dev.universaladmin.discord;

import java.util.List;
import java.util.regex.Pattern;

/** Include/exclude patterns over audit event types; {@code *} matches any run of characters. */
record EventFilter(List<String> include, List<String> exclude) {

    EventFilter {
        include = List.copyOf(include);
        exclude = List.copyOf(exclude);
    }

    boolean matches(String eventType) {
        return include.stream().anyMatch(pattern -> glob(pattern, eventType))
                && exclude.stream().noneMatch(pattern -> glob(pattern, eventType));
    }

    static boolean glob(String pattern, String value) {
        StringBuilder regex = new StringBuilder();
        for (String part : pattern.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return value.matches(regex.toString());
    }
}
