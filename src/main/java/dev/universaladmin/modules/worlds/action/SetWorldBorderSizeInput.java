package dev.universaladmin.modules.worlds.action;

/** {@code transitionSeconds} {@code null} or {@code 0} means an instant jump; otherwise a gradual grow/shrink over that many seconds. */
public record SetWorldBorderSizeInput(String worldName, double size, Long transitionSeconds) {
}
