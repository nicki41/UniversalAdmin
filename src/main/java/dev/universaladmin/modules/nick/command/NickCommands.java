package dev.universaladmin.modules.nick.command;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.nick.NickNames;
import dev.universaladmin.modules.nick.NickPermissions;
import dev.universaladmin.modules.nick.NickSettings;
import dev.universaladmin.modules.nick.action.NickActions;
import dev.universaladmin.modules.nick.action.NickInput;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.status.Nicks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;

/**
 * {@code /admin nick <name|random> [skin|own|random]}, {@code /admin nick off [player]},
 * {@code /admin nick list} and {@code /admin unnick [player]} - shortcuts
 * {@code /nick} and {@code /unnick}.
 */
public final class NickCommands {

    private NickCommands() {
    }

    public static List<AdminCommand> all(Nicks nicks, SettingsService settings) {
        return List.of(new Nick(nicks, settings), new Unnick());
    }

    static final class Nick extends BaseAdminCommand {

        private final Nicks nicks;
        private final SettingsService settings;

        Nick(Nicks nicks, SettingsService settings) {
            super("nick", NickPermissions.USE, MessageKey.of("commands.nick.usage"), MessageKey.of("commands.nick.description"));
            this.nicks = nicks;
            this.settings = settings;
            shortcuts("nick");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() == 0) {
                invocation.usage(this);
                return;
            }
            String first = invocation.args().get(0);
            switch (first.toLowerCase(Locale.ROOT)) {
                case "off" -> off(invocation);
                case "list" -> list(invocation);
                default -> set(invocation, first);
            }
        }

        private void set(CommandInvocation invocation, String first) {
            if (invocation.player().isEmpty()) {
                invocation.reply("command.error.player-only");
                return;
            }
            Player self = invocation.player().get();
            boolean random = first.equalsIgnoreCase("random");
            if (!random && !NickNames.valid(first)) {
                invocation.reply("nick.invalid-name", first);
                return;
            }
            NickInput.Skin skin = invocation.arg(1).map(Nick::skin)
                    .orElse(random ? NickInput.Skin.random() : NickInput.Skin.ofName());
            if (skin.kind() == NickInput.Kind.ACCOUNT && !NickNames.PATTERN.matcher(skin.account()).matches()) {
                invocation.reply("nick.invalid-name", skin.account());
                return;
            }
            invocation.run(NickActions.SET, new NickInput(self.getUniqueId(), PlayerNames.real(self), random ? null : first, skin));
        }

        private static NickInput.Skin skin(String raw) {
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "own" -> NickInput.Skin.own();
                case "random" -> NickInput.Skin.random();
                default -> NickInput.Skin.of(raw);
            };
        }

        private void off(CommandInvocation invocation) {
            if (invocation.argCount() > 1) {
                invocation.withPlayer(1, target -> invocation.run(NickActions.CLEAR, target));
                return;
            }
            invocation.player().ifPresentOrElse(
                    self -> invocation.run(NickActions.CLEAR, new PlayerRef(self.getUniqueId(), PlayerNames.real(self))),
                    () -> invocation.usage(this));
        }

        private void list(CommandInvocation invocation) {
            if (!invocation.hasPermission(NickPermissions.SEE)) {
                invocation.reply("error.no-permission");
                return;
            }
            List<Nicks.Nick> active = new ArrayList<>(nicks.active());
            if (active.isEmpty()) {
                invocation.reply("nick.list-empty");
                return;
            }
            active.sort(Comparator.comparing(Nicks.Nick::name, String.CASE_INSENSITIVE_ORDER));
            invocation.reply("nick.list-header", active.size());
            for (Nicks.Nick nick : active) {
                String skin = nick.skinOf() == null ? invocation.messages().get(MessageKey.of("nick.skin-own")) : nick.skinOf();
                invocation.line(nick.server() == null ? "nick.list-entry" : "nick.list-entry-server",
                        nick.name(), nick.realName(), skin, nick.server() == null ? "" : nick.server());
            }
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            if (invocation.argCount() <= 1) {
                return List.of("random", "off", "list");
            }
            String first = invocation.args().get(0).toLowerCase(Locale.ROOT);
            if (invocation.argCount() == 2 && first.equals("off")) {
                return invocation.hasPermission(NickPermissions.OTHERS) ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
            }
            if (invocation.argCount() == 2 && !first.equals("list")) {
                List<String> skins = new ArrayList<>(List.of("own", "random"));
                skins.addAll(settings.get(NickSettings.RANDOM_SKINS));
                return skins;
            }
            return List.of();
        }
    }

    /** {@code /admin unnick [player]} - the same as {@code /admin nick off}. */
    static final class Unnick extends BaseAdminCommand {

        Unnick() {
            super("unnick", NickPermissions.USE, MessageKey.of("commands.unnick.usage"), MessageKey.of("commands.unnick.description"));
            shortcuts("unnick");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() > 0) {
                invocation.withPlayer(0, target -> invocation.run(NickActions.CLEAR, target));
                return;
            }
            invocation.player().ifPresentOrElse(
                    self -> invocation.run(NickActions.CLEAR, new PlayerRef(self.getUniqueId(), PlayerNames.real(self))),
                    () -> invocation.usage(this));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 && invocation.hasPermission(NickPermissions.OTHERS)
                    ? Arguments.visiblePlayerNames(invocation.sender())
                    : List.of();
        }
    }
}
