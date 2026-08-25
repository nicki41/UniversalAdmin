package dev.universaladmin.permission;

/** A permission node plus the metadata needed to register it with the server. */
public record PermissionDefinition(PermissionNode node, String description, PermissionDefault defaultValue) {
}
