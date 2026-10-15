package dev.universaladmin.modules.moderation.command;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.core.time.DurationParseException;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.ModerationFormat;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.modules.moderation.ModerationSettings;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentQuery;
import dev.universaladmin.modules.moderation.PunishmentService;
import dev.universaladmin.modules.moderation.PunishmentType;
import dev.universaladmin.modules.moderation.StaffModeService;
import dev.universaladmin.modules.moderation.action.BanInput;
import dev.universaladmin.modules.moderation.action.FreezeInput;
import dev.universaladmin.modules.moderation.action.IpBanInput;
import dev.universaladmin.modules.moderation.action.KickInput;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.modules.moderation.action.MuteInput;
import dev.universaladmin.modules.moderation.action.RemoveWarnInput;
import dev.universaladmin.modules.moderation.action.UnbanInput;
import dev.universaladmin.modules.moderation.action.UnfreezeInput;
import dev.universaladmin.modules.moderation.action.UnmuteInput;
import dev.universaladmin.modules.moderation.action.WarnInput;
import dev.universaladmin.modules.moderation.gui.ModerationGuiContext;
import dev.universaladmin.modules.moderation.gui.ModeratePlayerPage;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.settings.SettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bukkit.entity.Player;

/**
 * Every {@code /admin} subcommand of the Moderation module. Each one only
 * parses arguments and runs the module's existing actions through
 * {@link CommandInvocation#run} - the same actions the GUI wizard uses, so
 * authorization, policy checks, enforcement and audit are identical whichever
 * frontend issued the punishment.
 */
public final class ModerationCommands {

    private static final int HISTORY_PAGE_SIZE = 8;

    private ModerationCommands() {
    }

    /** Dependencies the commands read from directly (lookups only; every mutation goes through an action). */
    public record Dependencies(
            PunishmentService punishments, StaffModeService staffMode, ModerationGuiContext gui,
            SettingsService settings, MessageService messages) {
    }

    /** Every Moderation command, ready to register. */
    public static List<AdminCommand> all(Dependencies deps) {
        return List.of(
                new Punish(Kind.KICK, deps),
                new Punish(Kind.BAN, deps),
                new Punish(Kind.TEMPBAN, deps),
                new Punish(Kind.IPBAN, deps),
                new Punish(Kind.MUTE, deps),
                new Punish(Kind.TEMPMUTE, deps),
                new Punish(Kind.WARN, deps),
                new Punish(Kind.FREEZE, deps),
                new Revoke("unban", ModerationPermissions.UNBAN, ModerationActionIds.UNBAN, UnbanInput::new,
                        "commands.unban.usage", "commands.unban.description"),
                new Revoke("unmute", ModerationPermissions.UNMUTE, ModerationActionIds.UNMUTE, UnmuteInput::new,
                        "commands.unmute.usage", "commands.unmute.description"),
                new Revoke("unfreeze", ModerationPermissions.UNFREEZE, ModerationActionIds.UNFREEZE, UnfreezeInput::new,
                        "commands.unfreeze.usage", "commands.unfreeze.description"),
                new Unwarn(deps),
                new History(deps),
                new Check(deps),
                new Toggle("vanish", ModerationPermissions.VANISH, ModerationActionIds.VANISH,
                        "commands.vanish.description", "v"),
                new Toggle("godmode", ModerationPermissions.GODMODE, ModerationActionIds.GODMODE,
                        "commands.godmode.description", "god"),
                new Toggle("nocollision", ModerationPermissions.COLLISION, ModerationActionIds.NO_COLLISION,
                        "commands.nocollision.description"),
                new Staff(deps),
                new Moderate(deps));
    }

    /** Resolves {@code {1}} reasons: everything from {@code fromIndex} on, or the localized "no reason given". */
    static String reason(CommandInvocation invocation, int fromIndex) {
        String joined = invocation.joinArgs(fromIndex).trim();
        return joined.isEmpty() ? invocation.messages().get(MessageKey.of("moderation.enforcement.no-reason")) : joined;
    }

    /** The kinds of punishment a {@link Punish} command can issue. */
    enum Kind {
        KICK("kick", ModerationPermissions.KICK, false, false),
        BAN("ban", ModerationPermissions.BAN, true, false),
        TEMPBAN("tempban", ModerationPermissions.TEMPBAN, true, true),
        IPBAN("ipban", ModerationPermissions.IPBAN, true, false),
        MUTE("mute", ModerationPermissions.MUTE, true, false),
        TEMPMUTE("tempmute", ModerationPermissions.TEMPMUTE, true, true),
        WARN("warn", ModerationPermissions.WARN, false, false),
        FREEZE("freeze", ModerationPermissions.FREEZE, false, false);

        final String name;
        final PermissionNode permission;
        /** Whether the second argument may be a duration ({@code /ban Steve 7d griefing}). */
        final boolean acceptsDuration;
        /** Whether that duration is mandatory. */
        final boolean requiresDuration;

        Kind(String name, PermissionNode permission, boolean acceptsDuration, boolean requiresDuration) {
            this.name = name;
            this.permission = permission;
            this.acceptsDuration = acceptsDuration;
            this.requiresDuration = requiresDuration;
        }
    }

    /**
     * {@code /admin <kick|ban|tempban|ipban|mute|tempmute|warn|freeze> <player> [duration] [reason]}.
     * For ban/mute/ipban an optional duration right after the name turns the
     * punishment temporary: {@code /ban Steve 7d griefing}.
     */
    static final class Punish extends BaseAdminCommand {

        private final Kind kind;
        private final Dependencies deps;

        Punish(Kind kind, Dependencies deps) {
            super(kind.name, kind.permission, usageKey(kind), descriptionKey(kind));
            this.kind = kind;
            this.deps = deps;
            shortcuts(kind.name);
        }

        @Override
        public void execute(CommandInvocation invocation) {
            int minimumArgs = kind.requiresDuration ? 2 : 1;
            if (invocation.argCount() < minimumArgs) {
                invocation.usage(this);
                return;
            }
            Optional<PlayerRef> target = invocation.knownPlayer(0);
            if (target.isEmpty()) {
                return;
            }

            Instant expiresAt = null;
            int reasonIndex = 1;
            if (kind.acceptsDuration && invocation.argCount() > 1) {
                String candidate = invocation.args().get(1);
                try {
                    Optional<Duration> duration = DurationParser.parse(candidate);
                    expiresAt = duration.map(Instant.now()::plus).orElse(null);
                    reasonIndex = 2;
                } catch (DurationParseException notADuration) {
                    if (kind.requiresDuration) {
                        invocation.reply("command.error.invalid-duration", candidate);
                        return;
                    }
                }
            }
            if (kind.requiresDuration && expiresAt == null) {
                invocation.reply("moderation.action.duration-required");
                return;
            }

            String reason = reason(invocation, reasonIndex);
            PlayerRef player = target.get();
            UUID id = player.id();
            switch (kind) {
                case KICK -> invocation.run(ModerationActionIds.KICK, new KickInput(id, reason));
                case BAN, TEMPBAN -> invocation.run(expiresAt != null ? ModerationActionIds.TEMP_BAN : ModerationActionIds.BAN,
                        new BanInput(id, reason, expiresAt));
                case IPBAN -> invocation.run(ModerationActionIds.IP_BAN, new IpBanInput(id, reason, expiresAt));
                case MUTE, TEMPMUTE -> invocation.run(expiresAt != null ? ModerationActionIds.TEMP_MUTE : ModerationActionIds.MUTE,
                        new MuteInput(id, reason, expiresAt));
                case WARN -> invocation.run(ModerationActionIds.WARN, new WarnInput(id, reason));
                case FREEZE -> invocation.run(ModerationActionIds.FREEZE, new FreezeInput(id, reason));
            }
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            if (invocation.argCount() <= 1) {
                return Arguments.visiblePlayerNames(invocation.sender());
            }
            if (invocation.argCount() == 2 && kind.acceptsDuration) {
                List<String> suggestions = new ArrayList<>(deps.settings()
                        .get(ModerationSettings.DURATION_PRESETS));
                if (!kind.requiresDuration) {
                    suggestions.addAll(deps.settings().get(ModerationSettings.REASON_PRESETS));
                }
                return suggestions;
            }
            return deps.settings().get(ModerationSettings.REASON_PRESETS);
        }

        private static MessageKey usageKey(Kind kind) {
            return switch (kind) {
                case KICK -> MessageKey.of("commands.kick.usage");
                case BAN -> MessageKey.of("commands.ban.usage");
                case TEMPBAN -> MessageKey.of("commands.tempban.usage");
                case IPBAN -> MessageKey.of("commands.ipban.usage");
                case MUTE -> MessageKey.of("commands.mute.usage");
                case TEMPMUTE -> MessageKey.of("commands.tempmute.usage");
                case WARN -> MessageKey.of("commands.warn.usage");
                case FREEZE -> MessageKey.of("commands.freeze.usage");
            };
        }

        private static MessageKey descriptionKey(Kind kind) {
            return switch (kind) {
                case KICK -> MessageKey.of("commands.kick.description");
                case BAN -> MessageKey.of("commands.ban.description");
                case TEMPBAN -> MessageKey.of("commands.tempban.description");
                case IPBAN -> MessageKey.of("commands.ipban.description");
                case MUTE -> MessageKey.of("commands.mute.description");
                case TEMPMUTE -> MessageKey.of("commands.tempmute.description");
                case WARN -> MessageKey.of("commands.warn.description");
                case FREEZE -> MessageKey.of("commands.freeze.description");
            };
        }
    }

    /** {@code /admin <unban|unmute|unfreeze> <player>}. */
    static final class Revoke extends BaseAdminCommand {

        private final ActionId action;
        private final Function<UUID, Object> input;

        Revoke(String name, PermissionNode permission, ActionId action, Function<UUID, Object> input,
                String usageKey, String descriptionKey) {
            super(name, permission, MessageKey.of(usageKey), MessageKey.of(descriptionKey));
            this.action = action;
            this.input = input;
            shortcuts(name);
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            invocation.knownPlayer(0).ifPresent(player -> invocation.run(action, input.apply(player.id())));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    /** {@code /admin unwarn <warning-id>} - the id is shown by {@code /admin history}. */
    static final class Unwarn extends BaseAdminCommand {

        private final Dependencies deps;

        Unwarn(Dependencies deps) {
            super("unwarn", ModerationPermissions.REMOVE_WARN,
                    MessageKey.of("commands.unwarn.usage"), MessageKey.of("commands.unwarn.description"));
            this.deps = deps;
            aliases("removewarn", "delwarn");
            shortcuts("unwarn");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            Optional<Integer> id = invocation.integer(0);
            if (id.isEmpty()) {
                return;
            }
            deps.punishments().findById(id.get()).whenComplete((found, error) -> {
                Optional<Punishment> warning = error == null
                        ? found.filter(punishment -> punishment.type() == PunishmentType.WARN)
                        : Optional.empty();
                if (warning.isEmpty()) {
                    invocation.reply("moderation.action.warn-not-found");
                    return;
                }
                Punishment warn = warning.get();
                invocation.run(ModerationActionIds.REMOVE_WARN, new RemoveWarnInput(warn.id(), warn.targetId()));
            });
        }
    }

    /** {@code /admin history <player> [page]} - every punishment of a player, newest first. */
    static final class History extends BaseAdminCommand {

        private final Dependencies deps;

        History(Dependencies deps) {
            super("history", ModerationPermissions.VIEW,
                    MessageKey.of("commands.history.usage"), MessageKey.of("commands.history.description"));
            this.deps = deps;
            aliases("punishments", "warnings");
            shortcuts("history");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            Optional<PlayerRef> target = invocation.knownPlayer(0);
            if (target.isEmpty()) {
                return;
            }
            int requestedPage = invocation.argCount() > 1 ? Arguments.integer(invocation.args().get(1)).orElse(1) : 1;
            PlayerRef player = target.get();
            deps.punishments().history(PunishmentQuery.forTarget(player.id(), 500)).whenComplete((history, error) -> {
                if (error != null) {
                    invocation.reply("command.error.internal");
                    return;
                }
                if (history.isEmpty()) {
                    invocation.reply("commands.history.empty", player.name());
                    return;
                }
                int pages = (history.size() + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE;
                int page = Math.max(1, Math.min(requestedPage, pages));
                invocation.line("commands.history.header", player.name(), history.size(), page, pages);
                history.stream()
                        .skip((long) (page - 1) * HISTORY_PAGE_SIZE)
                        .limit(HISTORY_PAGE_SIZE)
                        .forEach(punishment -> invocation.line("commands.history.entry",
                                punishment.id(),
                                typeName(deps.messages(), punishment.type()),
                                punishment.reason() == null ? "-" : punishment.reason(),
                                punishment.actorName() == null ? "CONSOLE" : punishment.actorName(),
                                ModerationFormat.instant(punishment.createdAt(), deps.settings()),
                                MessageService.markup(status(deps.messages(), punishment))));
                if (page < pages) {
                    invocation.line("commands.history.more", invocation.label(), player.name(), page + 1);
                }
            });
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    /** {@code /admin check <player>} - active ban/mute/freeze and warning count at a glance. */
    static final class Check extends BaseAdminCommand {

        private final Dependencies deps;

        Check(Dependencies deps) {
            super("check", ModerationPermissions.VIEW,
                    MessageKey.of("commands.check.usage"), MessageKey.of("commands.check.description"));
            this.deps = deps;
            aliases("checkban", "status-of");
            shortcuts("checkban");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            invocation.knownPlayer(0).ifPresent(player -> {
                PunishmentService punishments = deps.punishments();
                var ban = punishments.activeBan(player.id());
                var mute = punishments.activeMute(player.id());
                var freeze = punishments.activeFreeze(player.id());
                var warnings = punishments.history(new PunishmentQuery(player.id(), Set.of(PunishmentType.WARN), true, 500));
                CompletableFuture.allOf(ban, mute, freeze, warnings).whenComplete((ignored, error) -> {
                    if (error != null) {
                        invocation.reply("command.error.internal");
                        return;
                    }
                    invocation.line("commands.check.header", player.name());
                    report(invocation, ban.join(), "commands.check.banned", "commands.check.not-banned");
                    report(invocation, mute.join(), "commands.check.muted", "commands.check.not-muted");
                    report(invocation, freeze.join(), "commands.check.frozen", "commands.check.not-frozen");
                    invocation.line("commands.check.warnings", warnings.join().size());
                });
            });
        }

        private void report(CommandInvocation invocation, Optional<Punishment> active, String activeKey, String inactiveKey) {
            active.ifPresentOrElse(
                    punishment -> invocation.line(MessageKey.of(activeKey),
                            punishment.reason() == null ? "-" : punishment.reason(),
                            ModerationFormat.expiry(punishment.expiresAt(), deps.settings(), deps.messages()),
                            punishment.actorName() == null ? "CONSOLE" : punishment.actorName()),
                    () -> invocation.line(MessageKey.of(inactiveKey)));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    /** {@code /admin <vanish|godmode|nocollision>} - toggles one of the sender's own staff states. */
    static final class Toggle extends BaseAdminCommand {

        private final ActionId action;

        Toggle(String name, PermissionNode permission, ActionId action, String descriptionKey, String... shortcutAliases) {
            super(name, permission, MessageKey.of("commands.toggle.usage"), MessageKey.of(descriptionKey));
            this.action = action;
            List<String> labels = new ArrayList<>();
            labels.add(name);
            labels.addAll(List.of(shortcutAliases));
            aliases(shortcutAliases);
            shortcuts(labels.toArray(String[]::new));
            playersOnly();
        }

        @Override
        public void execute(CommandInvocation invocation) {
            invocation.run(action, null);
        }
    }

    /** {@code /admin staff} toggles staff mode; {@code /admin staff recover [player]} restores a crashed session's inventory. */
    static final class Staff extends BaseAdminCommand {

        private final Dependencies deps;

        Staff(Dependencies deps) {
            super("staff", ModerationPermissions.STAFFMODE,
                    MessageKey.of("commands.staff.usage"), MessageKey.of("commands.staff.description"));
            this.deps = deps;
            aliases("staffmode", "sm");
            shortcuts("staff", "staffmode");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() > 0 && invocation.args().get(0).equalsIgnoreCase("recover")) {
                recover(invocation);
                return;
            }
            Optional<Player> player = invocation.player();
            if (player.isEmpty()) {
                invocation.reply("command.error.player-only");
                return;
            }
            boolean active = deps.staffMode().isActive(player.get().getUniqueId());
            invocation.run(active ? ModerationActionIds.STAFF_MODE_EXIT : ModerationActionIds.STAFF_MODE_ENTER, null);
        }

        private void recover(CommandInvocation invocation) {
            if (!invocation.hasPermission(ModerationPermissions.STAFFMODE_RECOVER)) {
                invocation.reply("error.no-permission");
                return;
            }
            Optional<Player> target = invocation.argCount() > 1
                    ? invocation.onlinePlayer(1)
                    : invocation.player();
            if (target.isEmpty()) {
                if (invocation.argCount() <= 1) {
                    invocation.reply("command.staff.recover.usage");
                }
                return;
            }
            invocation.run(ModerationActionIds.STAFF_MODE_RECOVER, target.get().getUniqueId());
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            if (invocation.argCount() <= 1) {
                return List.of("recover");
            }
            return invocation.argCount() == 2 && invocation.args().get(0).equalsIgnoreCase("recover")
                    ? Arguments.visiblePlayerNames(invocation.sender())
                    : List.of();
        }
    }

    /** {@code /admin moderate <player>} - opens the punishment wizard GUI for a player. */
    static final class Moderate extends BaseAdminCommand {

        private final Dependencies deps;

        Moderate(Dependencies deps) {
            super("moderate", ModerationPermissions.USE,
                    MessageKey.of("commands.moderate.usage"), MessageKey.of("commands.moderate.description"));
            this.deps = deps;
            aliases("punish");
            shortcuts("punish");
            playersOnly();
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            Player viewer = invocation.player().orElseThrow();
            invocation.knownPlayer(0).ifPresent(target ->
                    new ModeratePlayerPage(deps.gui(), target.id(), target.name()).open(viewer));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    static String typeName(MessageService messages, PunishmentType type) {
        return messages.get(MessageKey.of("moderation.type." + type.name().toLowerCase(Locale.ROOT).replace('_', '-')));
    }

    private static String status(MessageService messages, Punishment punishment) {
        if (punishment.revokedAt() != null) {
            return messages.get(MessageKey.of("commands.history.status-revoked"),
                    punishment.revokedBy() == null ? "-" : punishment.revokedBy());
        }
        if (punishment.type() == PunishmentType.KICK) {
            return "";
        }
        return messages.get(MessageKey.of(punishment.isActiveAt(Instant.now())
                ? "commands.history.status-active" : "commands.history.status-expired"));
    }
}
