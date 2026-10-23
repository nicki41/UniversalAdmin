package dev.universaladmin.voicechat;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import dev.universaladmin.voice.VoiceChat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The plugin Simple Voice Chat calls: hands microphone packets to whatever
 * {@link VoiceChatModule} installed ({@link #onMicrophone}), and keeps the
 * server API once the voice chat server runs. Also the
 * {@link VoiceChat} service UniversalAdmin sees.
 *
 * <p>Microphone packets arrive on the voice chat's network thread, many per
 * second while someone talks - the handler must answer from memory.
 */
final class VoiceChatBridge implements VoicechatPlugin, VoiceChat {

    static final String PLUGIN_ID = "universaladmin";

    private volatile VoicechatServerApi api;
    private volatile Consumer<MicrophonePacketEvent> microphone = event -> { };

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi server) {
            this.api = server;
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> api = event.getVoicechat());
        registration.registerEvent(MicrophonePacketEvent.class, event -> microphone.accept(event));
    }

    /** Replaces the microphone handler - a no-op one while the module is disabled. */
    void onMicrophone(Consumer<MicrophonePacketEvent> handler) {
        microphone = handler;
    }

    /** The voice chat server, once it runs. */
    Optional<VoicechatServerApi> api() {
        return Optional.ofNullable(api);
    }

    /** A player's voice chat connection, if the voice chat runs and knows them. */
    Optional<VoicechatConnection> connection(UUID playerId) {
        return api().map(server -> server.getConnectionOf(playerId));
    }

    @Override
    public String name() {
        return "Simple Voice Chat";
    }

    @Override
    public boolean connected(UUID playerId) {
        return connection(playerId).map(VoicechatConnection::isConnected).orElse(false);
    }
}
