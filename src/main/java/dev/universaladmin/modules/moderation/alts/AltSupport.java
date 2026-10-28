package dev.universaladmin.modules.moderation.alts;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.modules.moderation.ModerationSettings;
import dev.universaladmin.network.NetworkListener;
import dev.universaladmin.network.NetworkMessage;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.notification.Notification;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The frontends of alt detection: the "Possible Alts" profile button,
 * {@code /admin alts}, and what happens when a player joins who shares an
 * address with a banned account - a hint for staff (on every server of a
 * network) and, only with {@code moderation.alts.refuse-banned-alts}, a
 * refused login.
 */
public final class AltSupport {

    /** Network channel topic: a player who shares an address with a banned account joined somewhere. */
    public static final String NETWORK_TOPIC = "moderation.alt-joined";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** Everything the alt frontends need. */
    public record Context(
            GuiFramework framework, MessageService messages, TaskScheduler scheduler, ActionExecutor actions, AltService alts,
            SettingsService settings, NotificationService notifications, NetworkService network, Logger logger) {
    }

    private AltSupport() {
    }

    /** "Possible Alts" on a player's profile - only while alt detection is on. */
    public static PlayerMenuEntry menuEntry(Context ctx) {
        return new PlayerMenuEntry() {
            @Override
            public String id() {
                return "core:moderation.alts";
            }

            @Override
            public int order() {
                return 25;
            }

            @Override
            public PermissionNode permission() {
                return ModerationPermissions.ALTS;
            }

            @Override
            public boolean visible(Player viewer, PlayerRef target) {
                return ctx.alts().enabled();
            }

            @Override
            public GuiItem icon(Player viewer, PlayerRef target) {
                return GuiItem.of(Material.SPYGLASS, ComponentMessages.render(ctx.messages().get(MessageKey.of("moderation.alts.gui.button"))));
            }

            @Override
            public void onClick(GuiClickContext context, PlayerRef target) {
                context.open(new AltsPage(ctx.framework(), ctx.messages(), ctx.scheduler(), ctx.actions(), target));
            }
        };
    }

    /** {@code /admin alts <player> [forget]}. */
    public static final class AltsCommand extends BaseAdminCommand {

        private final Context ctx;

        public AltsCommand(Context ctx) {
            super("alts", ModerationPermissions.ALTS, MessageKey.of("commands.alts.usage"), MessageKey.of("commands.alts.description"));
            this.ctx = ctx;
            aliases("alt");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            invocation.withPlayer(0, target -> {
                PlayerRef player = target;
                AltActions.Target input = new AltActions.Target(player.id(), player.name());
                if (invocation.argCount() > 1 && invocation.args().get(1).equalsIgnoreCase("forget")) {
                    invocation.run(AltActions.FORGET, input);
                    return;
                }
                invocation.<AltActions.Target, List<AltMatch>>run(AltActions.VIEW, input, alts -> {
                    if (!ctx.alts().enabled()) {
                        invocation.line("moderation.alts.disabled");
                    }
                    if (alts.isEmpty()) {
                        invocation.reply("moderation.alts.none", player.name());
                        return;
                    }
                    invocation.line("moderation.alts.header", player.name(), alts.size());
                    alts.forEach(alt -> invocation.line(alt.banned() ? "moderation.alts.entry-banned" : "moderation.alts.entry",
                            alt.name(), alt.sharedAddresses(), TIME.format(alt.lastSeen())));
                });
            });
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            if (invocation.argCount() <= 1) {
                return Arguments.visiblePlayerNames(invocation.sender());
            }
            return invocation.argCount() == 2 ? List.of("forget") : List.of();
        }
    }

    /**
     * Checks every login: off the main thread before the player exists
     * (records the address, finds banned accounts that share it), then -
     * with the player's permissions known - refuses them if configured, and
     * tells staff once they joined.
     */
    public static final class JoinListener implements Listener, NetworkListener {

        private final Context ctx;
        /** Banned accounts found at pre-login, until the player joined or left. */
        private final Map<UUID, List<AltMatch>> pending = new ConcurrentHashMap<>();

        public JoinListener(Context ctx) {
            this.ctx = ctx;
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onPreLogin(AsyncPlayerPreLoginEvent event) {
            if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || !ctx.alts().enabled()) {
                return;
            }
            try {
                List<AltMatch> banned = ctx.alts().checkLogin(event.getUniqueId(), event.getName(), event.getAddress()).join();
                if (!banned.isEmpty()) {
                    pending.put(event.getUniqueId(), banned);
                }
            } catch (RuntimeException e) {
                ctx.logger().log(Level.WARNING, "Alt detection failed for " + event.getName() + " - letting them in", e);
            }
        }

        @SuppressWarnings("deprecation")
        @EventHandler(priority = EventPriority.HIGH)
        public void onLogin(PlayerLoginEvent event) {
            Player player = event.getPlayer();
            List<AltMatch> banned = pending.get(player.getUniqueId());
            if (banned == null || event.getResult() != PlayerLoginEvent.Result.ALLOWED
                    || !ctx.settings().get(ModerationSettings.ALTS_REFUSE_BANNED)
                    || player.hasPermission(ModerationPermissions.BYPASS_ALT_CHECK.value())) {
                return;
            }
            pending.remove(player.getUniqueId());
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                    ComponentMessages.render(ctx.messages().get(MessageKey.of("moderation.alts.refused"))));
            announce(PlayerNames.real(player), banned);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onJoin(PlayerJoinEvent event) {
            List<AltMatch> banned = pending.remove(event.getPlayer().getUniqueId());
            if (banned != null) {
                announce(PlayerNames.real(event.getPlayer()), banned);
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            pending.remove(event.getPlayer().getUniqueId());
        }

        /** The hint on another server of the network. */
        @Override
        public void onMessage(NetworkMessage message) {
            if (ctx.settings().get(ModerationSettings.ALTS_NOTIFY)) {
                notifyStaff(message.get("player", "?"), message.get("alts", "?"), message.source());
            }
        }

        private void announce(String player, List<AltMatch> banned) {
            if (!ctx.settings().get(ModerationSettings.ALTS_NOTIFY)) {
                return;
            }
            String names = banned.stream().map(AltMatch::name).collect(Collectors.joining(", "));
            notifyStaff(player, names, null);
            ctx.network().publish(NETWORK_TOPIC, Map.of("player", player, "alts", names));
        }

        private void notifyStaff(String player, String names, String server) {
            String text = server == null
                    ? ctx.messages().get(MessageKey.of("moderation.alts.join-alert"), player, names)
                    : ctx.messages().get(MessageKey.of("moderation.alts.join-alert-network"), player, names, server);
            ctx.scheduler().runOnMainThread(() -> ctx.notifications().notifyStaff(ModerationPermissions.ALTS, Notification.info(text)));
        }
    }
}
