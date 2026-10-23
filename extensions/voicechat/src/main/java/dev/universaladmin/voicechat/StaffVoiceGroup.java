package dev.universaladmin.voicechat;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The staff voice group: hidden (not in the group list), isolated (members
 * hear only each other and nobody outside hears them), persistent (stays when
 * it empties) and with a random password nobody knows - players only get in
 * through UniversalAdmin ({@code /admin voicegroup}).
 */
final class StaffVoiceGroup {

    private final VoiceChatBridge bridge;
    private final Supplier<String> name;
    private final String password = UUID.randomUUID().toString();
    private volatile Group group;

    StaffVoiceGroup(VoiceChatBridge bridge, Supplier<String> name) {
        this.bridge = bridge;
        this.name = name;
    }

    /** The group, created on first use (and again if the voice chat restarted and lost it). */
    Optional<Group> group() {
        return bridge.api().map(api -> {
            Group current = group;
            if (current == null || api.getGroup(current.getId()) == null) {
                current = api.groupBuilder()
                        .setName(name.get())
                        .setPassword(password)
                        .setHidden(true)
                        .setPersistent(true)
                        .setType(Group.Type.ISOLATED)
                        .build();
                group = current;
            }
            return current;
        });
    }

    /** Whether {@code connection} is in the staff group - without creating it. */
    boolean contains(VoicechatConnection connection) {
        Group current = group;
        Group joined = connection.getGroup();
        return current != null && joined != null && joined.getId().equals(current.getId());
    }
}
