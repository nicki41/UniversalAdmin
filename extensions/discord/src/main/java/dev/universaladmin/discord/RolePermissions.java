package dev.universaladmin.discord;

import dev.universaladmin.permission.PermissionEvaluator;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a Discord member may do in UniversalAdmin: the union of the
 * permission patterns of their roles ({@code bot.roles} in the config).
 * {@code *} grants everything, {@code universaladmin.moderation.*} a node and
 * everything below it; anything else must match exactly. Members without a
 * listed role may do nothing.
 */
record RolePermissions(Map<String, List<String>> roles) {

    RolePermissions {
        roles = Map.copyOf(roles);
    }

    PermissionEvaluator forRoles(Collection<String> roleIds) {
        Set<String> patterns = new LinkedHashSet<>();
        for (String roleId : roleIds) {
            patterns.addAll(roles.getOrDefault(roleId, List.of()));
        }
        if (patterns.isEmpty()) {
            return PermissionEvaluator.denyAll();
        }
        return node -> patterns.stream().anyMatch(pattern -> matches(pattern, node.value()));
    }

    static boolean matches(String pattern, String node) {
        String trimmed = pattern.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.equals("*")) {
            return true;
        }
        if (trimmed.endsWith(".*")) {
            String prefix = trimmed.substring(0, trimmed.length() - 2);
            return node.equals(prefix) || node.startsWith(prefix + ".");
        }
        return trimmed.equals(node);
    }
}
