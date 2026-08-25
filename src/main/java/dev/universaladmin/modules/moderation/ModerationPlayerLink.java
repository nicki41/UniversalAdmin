package dev.universaladmin.modules.moderation;

import dev.universaladmin.gui.GuiPage;
import java.util.UUID;

/**
 * Cross-module GUI link so the Players module's profile page can open the
 * Moderation wizard for a target without importing any of this module's
 * internal classes - published via {@code ServiceRegistry} (see {@link
 * dev.universaladmin.core.ServiceRegistry}, already the sanctioned
 * cross-module lookup mechanism) rather than a direct import, per docs/development/architecture-rules.md's
 * "Cross-Module-Zugriff läuft über ServiceRegistry" rule. {@code players}
 * looks this up optionally: if the Moderation module is disabled, {@code
 * ServiceRegistry.get} returns empty and the "Moderate" button simply
 * doesn't render - neither module hard-depends on the other.
 */
public interface ModerationPlayerLink {

    /** A fresh, ephemeral {@link GuiPage} for moderating {@code targetId} - never registered in {@code GuiRegistry}. */
    GuiPage moderationPage(UUID targetId, String targetName);
}
