package dev.universaladmin.permission;

import java.util.regex.Pattern;

/**
 * A permission string such as {@code universaladmin.players.kick}.
 *
 * <p>Unlike {@link dev.universaladmin.core.id.Key}-based typed IDs, this does
 * not use the {@code namespace:name} shape - permission nodes are read and
 * typed by server admins in permission plugins (LuckPerms and friends), so
 * they follow the plain dotted convention every Bukkit plugin already uses.
 * Extensions are expected to prefix their own nodes, e.g.
 * {@code myextension.reports.create}.
 */
public record PermissionNode(String value) {

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9_-]+(\\.[a-z0-9_-]+)*");

    public PermissionNode {
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid permission node: " + value);
        }
    }

    public static PermissionNode of(String value) {
        return new PermissionNode(value);
    }

    /** Shorthand for UniversalAdmin's own nodes: {@code universaladmin.<suffix>}. */
    public static PermissionNode core(String suffix) {
        return new PermissionNode("universaladmin." + suffix);
    }

    @Override
    public String toString() {
        return value;
    }
}
