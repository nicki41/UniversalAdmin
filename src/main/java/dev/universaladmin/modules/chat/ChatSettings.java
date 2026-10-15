package dev.universaladmin.modules.chat;

import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingTypes;
import dev.universaladmin.settings.SettingValidators;
import java.util.List;

/** Settings of the Chat module, under {@code chat.*} in config.yml. */
public final class ChatSettings {

    public static final SettingKey<String> STAFF_CHAT_PREFIX = SettingKey.of("chat", "chat.staff-chat.prefix");
    public static final SettingKey<Integer> CLEAR_LINES = SettingKey.of("chat", "chat.clear-lines");
    public static final SettingKey<Boolean> STAFF_CHAT_CONSOLE = SettingKey.of("chat", "chat.staff-chat.log-to-console");

    private ChatSettings() {
    }

    static List<SettingDefinition<?>> all() {
        return List.of(
                SettingDefinition.builder(STAFF_CHAT_PREFIX, SettingTypes.STRING, "#")
                        .description("A chat message starting with this goes to staff chat (for players with chat.staff); empty disables it.")
                        .validator(SettingValidators.matches(".{0,3}", "at most 3 characters"))
                        .build(),
                SettingDefinition.builder(CLEAR_LINES, SettingTypes.INTEGER, 100)
                        .description("How many empty lines /admin chat clear sends to push old messages out of view.")
                        .validator(SettingValidators.intRange(10, 300))
                        .build(),
                SettingDefinition.builder(STAFF_CHAT_CONSOLE, SettingTypes.BOOLEAN, true)
                        .description("Also write staff chat messages to the server console/log.")
                        .build());
    }
}
