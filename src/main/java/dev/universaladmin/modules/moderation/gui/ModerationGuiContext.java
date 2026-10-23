package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.CollisionState;
import dev.universaladmin.modules.moderation.GodmodeState;
import dev.universaladmin.modules.moderation.PunishmentService;
import dev.universaladmin.modules.moderation.StaffModeState;
import dev.universaladmin.modules.moderation.VanishService;
import dev.universaladmin.modules.moderation.templates.PunishmentTemplateService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.voice.VoiceChat;
import java.util.Optional;

/**
 * The dependencies every Moderation GUI page needs, bundled into one
 * constructor parameter - same reasoning as {@code PlayerGuiContext} in the
 * Players module. Public (unlike the package-private {@code PlayerGuiContext})
 * because {@link dev.universaladmin.modules.moderation.ModerationModule}
 * builds exactly one instance and both passes it to this package's pages
 * and closes over it in the {@link ModeratePlayerMenuEntry}
 * it registers for the Players module to call into.
 */
public record ModerationGuiContext(
        GuiFramework framework,
        MessageService messages,
        TaskScheduler scheduler,
        PunishmentService punishmentService,
        ActionExecutor actionExecutor,
        SettingsService settings,
        VanishService vanishService,
        GodmodeState godmodeState,
        CollisionState collisionState,
        StaffModeState staffModeState,
        PunishmentTemplateService templates,
        ServiceRegistry services) {

    /** The voice chat an extension connected, if any - only then can a mute be limited to the chat or the voice chat. */
    public Optional<VoiceChat> voiceChat() {
        return services.get(VoiceChat.class);
    }
}
