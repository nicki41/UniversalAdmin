package dev.universaladmin.extension;

import dev.universaladmin.module.Module;
import java.util.List;
import org.bukkit.plugin.Plugin;

/**
 * How another plugin adds {@link Module}s to UniversalAdmin. An extension is
 * an ordinary Paper plugin that declares {@code depend: [UniversalAdmin]} and
 * registers its module(s) from its own {@code onEnable}:
 *
 * <pre>{@code
 * public final class MyExtension extends JavaPlugin {
 *     @Override
 *     public void onEnable() {
 *         UniversalAdminProvider.get().extensions().register(this, new MyModule());
 *     }
 * }
 * }</pre>
 *
 * <p>The module is loaded, its migrations applied and enabled right away;
 * its commands, GUI pages, main-menu entry, permissions, settings and
 * listeners become part of the platform exactly like a built-in module's.
 * When the owning plugin is disabled, every module it registered is disabled
 * and everything it registered through its {@code ModuleContext} is removed
 * again - nothing to clean up by hand.
 *
 * <p>Extension modules must use their own namespace in their
 * {@link dev.universaladmin.module.ModuleId} (e.g. {@code myextension:tickets});
 * {@code core} is reserved for UniversalAdmin. See docs/extensions/.
 */
public interface ExtensionManager {

    /**
     * Registers and enables {@code module} on behalf of {@code owner}. Must be
     * called on the main thread, after UniversalAdmin has enabled (i.e. from
     * the owner's {@code onEnable} or later).
     *
     * @return {@code true} if the module enabled, {@code false} if it failed (the reason is logged)
     * @throws IllegalArgumentException if the module uses the reserved {@code core} namespace
     * @throws IllegalStateException    if a module with the same id is already active
     */
    boolean register(Plugin owner, Module module);

    /**
     * Lets UniversalAdmin's update check cover {@code owner} too: the newest
     * GitHub release of {@code repository} is compared with the plugin's
     * version; staff and the console hear about a newer one, and - with
     * {@code update.auto-download} - it is downloaded into
     * {@code plugins/update/} like UniversalAdmin itself and applied at the
     * next restart. {@code /admin update} includes it.
     *
     * <p>The release's plugin jar is the asset named
     * {@code assetPrefix + <version> + ".jar"} (a tag {@code v1.2.0} and
     * {@code assetPrefix} {@code "myextension-"} mean {@code myextension-1.2.0.jar});
     * a {@code <jar>.sha256} asset next to it is checked when present. The
     * source is dropped again when {@code owner} is disabled.
     *
     * @param repository {@code "owner/name"} on GitHub, e.g. {@code "universaladmin-mc/universaladmin-discord"}
     * @since 1.5
     */
    default void updatesFrom(Plugin owner, String repository, String assetPrefix) {
    }

    /** Every module registered by an extension so far, with its owner and current state. */
    List<ExtensionInfo> extensions();
}
