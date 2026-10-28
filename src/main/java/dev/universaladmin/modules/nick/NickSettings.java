package dev.universaladmin.modules.nick;

import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingTypes;
import dev.universaladmin.settings.SettingValidator;
import dev.universaladmin.settings.SettingValidators;
import java.util.List;
import java.util.Optional;

/** Settings of the Nick module, under {@code nick.*} in config.yml. */
public final class NickSettings {

    public static final SettingKey<Boolean> RESTORE_ON_REJOIN = SettingKey.of("nick", "nick.restore-on-rejoin");
    public static final SettingKey<Boolean> ALLOW_KNOWN_NAMES = SettingKey.of("nick", "nick.allow-known-names");
    public static final SettingKey<Boolean> SKIN_OF_NAME = SettingKey.of("nick", "nick.skin-of-name");
    public static final SettingKey<List<String>> RANDOM_NAMES = SettingKey.of("nick", "nick.random-names");
    public static final SettingKey<List<String>> RANDOM_SKINS = SettingKey.of("nick", "nick.random-skins");

    private NickSettings() {
    }

    static List<SettingDefinition<?>> all() {
        return List.of(
                SettingDefinition.builder(RESTORE_ON_REJOIN, SettingTypes.BOOLEAN, true)
                        .description("A nick stays when its player leaves and comes back. Off: it ends when they leave "
                                + "(switching servers in a network keeps it).")
                        .build(),
                SettingDefinition.builder(ALLOW_KNOWN_NAMES, SettingTypes.BOOLEAN, false)
                        .description("Allow nicks with the name of a player who has played here before.")
                        .build(),
                SettingDefinition.builder(SKIN_OF_NAME, SettingTypes.BOOLEAN, true)
                        .description("/nick <name> without a skin wears the skin of the account with that name, if there is one.")
                        .build(),
                SettingDefinition.builder(RANDOM_NAMES, SettingTypes.STRING_LIST, List.<String>of())
                        .description("Names /nick random picks from. Empty: made-up names.")
                        .validator(names())
                        .build(),
                SettingDefinition.builder(RANDOM_SKINS, SettingTypes.STRING_LIST, List.of("MHF_Steve", "MHF_Alex"))
                        .description("Accounts whose skins /nick random picks from. Empty: players keep their own skin.")
                        .validator(names())
                        .build());
    }

    private static SettingValidator<List<String>> names() {
        SettingValidator<String> name = SettingValidators.matches(NickNames.PATTERN.pattern(), "Minecraft names (3-16 letters, digits, _)");
        return values -> values.stream().map(name::validate).flatMap(Optional::stream).findFirst();
    }
}
