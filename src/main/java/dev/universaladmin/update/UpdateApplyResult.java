package dev.universaladmin.update;

/**
 * What {@code /admin update} did for one plugin.
 *
 * @param name          the plugin - UniversalAdmin or an extension
 * @param updateApplied {@code false} means nothing was downloaded: it already runs the latest release, or {@code failure} says why not
 * @param version       the latest release's version, whether or not it was actually applied; the running version if the check failed
 * @param failure       why this plugin could not be checked or downloaded, {@code null} if it could
 */
public record UpdateApplyResult(String name, boolean updateApplied, String version, String failure) {

    public UpdateApplyResult(String name, boolean updateApplied, String version) {
        this(name, updateApplied, version, null);
    }

    /** This plugin could not be checked or downloaded - the others still were. */
    public static UpdateApplyResult failed(String name, String currentVersion, String failure) {
        return new UpdateApplyResult(name, false, currentVersion, failure);
    }

    public boolean failed() {
        return failure != null;
    }
}
