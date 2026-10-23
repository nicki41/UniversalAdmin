package dev.universaladmin.voicechat;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.core.UniversalAdminProvider;
import dev.universaladmin.localization.MessageBundles;
import java.util.Optional;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * UniversalAdmin Voice Chat: connects Simple Voice Chat to UniversalAdmin -
 * voice and full mutes silence the microphone, vanished staff stay silent,
 * the profile shows the voice status and staff get their own voice group
 * (ADR 0022). Needs UniversalAdmin 1.5 or newer (API 1.4: mute kinds and the
 * {@link dev.universaladmin.voice.VoiceChat} service).
 */
public final class VoiceChatPlugin extends JavaPlugin {

    private static final String REQUIRED_API = "1.4";

    @Override
    public void onEnable() {
        Optional<UniversalAdmin> universalAdmin = UniversalAdminProvider.find();
        if (universalAdmin.isEmpty()) {
            getLogger().severe("UniversalAdmin is not running - UniversalAdminVoiceChat needs it and disables itself.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (compare(UniversalAdminProvider.API_VERSION, REQUIRED_API) < 0) {
            getLogger().severe("UniversalAdminVoiceChat needs UniversalAdmin 1.5 or newer - please update it.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        BukkitVoicechatService voicechat = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (voicechat == null) {
            getLogger().severe("Simple Voice Chat is not running - UniversalAdminVoiceChat needs it and disables itself.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        MessageBundles.register(this, universalAdmin.get().messages(), "en_US", "de_DE");
        // Simple Voice Chat keeps a registered plugin for good - the bridge
        // stays inert whenever the module is not enabled.
        VoiceChatBridge bridge = new VoiceChatBridge();
        voicechat.registerPlugin(bridge);
        universalAdmin.get().extensions().register(this, new VoiceChatModule(universalAdmin.get(), bridge));
    }

    /** Compares "major.minor" version strings numerically. */
    static int compare(String version, String required) {
        String[] have = version.split("\\.");
        String[] need = required.split("\\.");
        for (int i = 0; i < Math.max(have.length, need.length); i++) {
            int a = i < have.length ? Integer.parseInt(have[i]) : 0;
            int b = i < need.length ? Integer.parseInt(need[i]) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }
}
