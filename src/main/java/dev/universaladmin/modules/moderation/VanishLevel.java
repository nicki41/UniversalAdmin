package dev.universaladmin.modules.moderation;

/**
 * How "hidden" a vanish is - one value today ({@link #STANDARD}), but a
 * real enum (not a boolean) so a future tier (e.g. hidden even from junior
 * staff) is an additive change to this type and {@link VanishVisibilityPolicy},
 * not a rewrite. See that interface's javadoc - docs/development/architecture-rules.md asks this module
 * to plan for future vanish levels without building a rank system now.
 */
public enum VanishLevel {
    STANDARD
}
