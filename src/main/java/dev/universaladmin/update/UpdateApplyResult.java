package dev.universaladmin.update;

/**
 * What {@code /admin update} did for one plugin.
 *
 * @param name          the plugin - UniversalAdmin or an extension
 * @param updateApplied {@code false} means it already runs the latest release - nothing was downloaded
 * @param version       the latest release's version, whether or not it was actually applied
 */
public record UpdateApplyResult(String name, boolean updateApplied, String version) {
}
