package dev.universaladmin.update;

/**
 * @param updateApplied {@code false} means the running version already matched the latest release - nothing was downloaded
 * @param version       the latest release's version, whether or not it was actually applied
 */
public record UpdateApplyResult(boolean updateApplied, String version) {
}
