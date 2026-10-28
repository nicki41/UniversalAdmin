package dev.universaladmin.modules.nick.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionResult.FailureReason;
import dev.universaladmin.core.PlayerDirectory;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.nick.NickNameTakenException;
import dev.universaladmin.modules.nick.NickNames;
import dev.universaladmin.modules.nick.NickRecord;
import dev.universaladmin.modules.nick.NickService;
import dev.universaladmin.modules.nick.NickSettings;
import dev.universaladmin.modules.nick.SkinSource;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.status.Nicks;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * The Nick module's actions. Frontends (commands, the Nick menu, the
 * profile button) only call these.
 */
public final class NickActions {

    public static final ActionId SET = ActionId.core("nick.set");
    public static final ActionId CLEAR = ActionId.core("nick.clear");

    private NickActions() {
    }

    /**
     * Puts a nick on an online player: checks the name is free (not online,
     * not someone else's nick, by default not a player who has played here),
     * fetches the skin, stores the nick and puts it on. Result: the nick.
     */
    public static final class SetNick implements Action<NickInput, NickRecord> {

        private final NickService service;
        private final Nicks nicks;
        private final SkinSource skins;
        private final SettingsService settings;
        private final NetworkService network;
        private final TaskScheduler scheduler;
        private final Supplier<Optional<PlayerDirectory>> directory;
        private final RandomGenerator random;

        public SetNick(NickService service, Nicks nicks, SkinSource skins, SettingsService settings, NetworkService network,
                TaskScheduler scheduler, Supplier<Optional<PlayerDirectory>> directory, RandomGenerator random) {
            this.service = service;
            this.nicks = nicks;
            this.skins = skins;
            this.settings = settings;
            this.network = network;
            this.scheduler = scheduler;
            this.directory = directory;
            this.random = random;
        }

        @Override
        public ActionId id() {
            return SET;
        }

        @Override
        public CompletableFuture<ActionResult<NickRecord>> execute(ActionContext context, NickInput input) {
            return scheduler.supplyOnMainThread(() -> choose(input))
                    .thenCompose(this::notKnown)
                    .thenCompose(this::notStored)
                    .thenCompose(chosen -> skin(chosen, input.skin()).thenApply(skin ->
                            new NickRecord(chosen.targetId(), chosen.realName(), chosen.name(), skin.orElse(null), Instant.now())))
                    .thenCompose(service::save)
                    .thenCompose(nick -> scheduler.supplyOnMainThread(() -> {
                        Player target = Bukkit.getPlayer(nick.playerId());
                        if (target != null) {
                            service.wear(target, nick);
                        }
                        return nick.skin() == null
                                ? ActionResult.success(nick, MessageKey.of("nick.set-own-skin"), nick.name())
                                : ActionResult.success(nick, MessageKey.of("nick.set-skin"), nick.name(), nick.skinOf());
                    }))
                    .exceptionally(NickActions::failure);
        }

        private record Chosen(UUID targetId, String realName, String name) {
        }

        /** Main thread: the target, and a name nobody here (or anywhere in the network) is shown under. */
        private Chosen choose(NickInput input) {
            Player target = Bukkit.getPlayer(input.targetId());
            if (target == null) {
                throw refuse(FailureReason.NOT_FOUND, "nick.not-online", input.targetName());
            }
            UUID targetId = target.getUniqueId();
            String realName = PlayerNames.real(target);
            Set<String> inUse = new HashSet<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(targetId)) {
                    inUse.add(key(online.getName()));
                    inUse.add(key(PlayerNames.real(online)));
                }
            }
            if (network.enabled()) {
                for (NetworkPlayer elsewhere : network.players()) {
                    if (!targetId.equals(elsewhere.id())) {
                        inUse.add(key(elsewhere.name()));
                    }
                }
            }
            nicks.active().stream().filter(nick -> !nick.playerId().equals(targetId)).forEach(nick -> inUse.add(key(nick.name())));
            boolean allowKnown = settings.get(NickSettings.ALLOW_KNOWN_NAMES);
            Predicate<String> known = name -> !allowKnown && playedHere(name, targetId);

            if (input.name() == null) {
                String name = NickNames.random(settings.get(NickSettings.RANDOM_NAMES),
                                candidate -> candidate.equalsIgnoreCase(realName) || inUse.contains(key(candidate)) || known.test(candidate),
                                random)
                        .orElseThrow(() -> refuse(FailureReason.CONFLICT, "nick.no-free-name"));
                return new Chosen(targetId, realName, name);
            }
            String name = input.name();
            if (name.equalsIgnoreCase(realName)) {
                throw refuse(FailureReason.VALIDATION, "nick.own-name");
            }
            if (inUse.contains(key(name))) {
                throw refuse(FailureReason.CONFLICT, "nick.name-in-use", name);
            }
            if (known.test(name)) {
                throw refuse(FailureReason.CONFLICT, "nick.name-known", name);
            }
            return new Chosen(targetId, realName, name);
        }

        private static boolean playedHere(String name, UUID self) {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            return cached != null && !cached.getUniqueId().equals(self) && cached.hasPlayedBefore();
        }

        /** The players of the whole network, not only of this server's cache - unless known names are allowed. */
        private CompletableFuture<Chosen> notKnown(Chosen chosen) {
            Optional<PlayerDirectory> players = directory.get();
            if (settings.get(NickSettings.ALLOW_KNOWN_NAMES) || players.isEmpty()) {
                return CompletableFuture.completedFuture(chosen);
            }
            return players.get().findByName(chosen.name()).thenApply(found -> {
                if (found.isPresent() && !found.get().id().equals(chosen.targetId())) {
                    throw refuse(FailureReason.CONFLICT, "nick.name-known", chosen.name());
                }
                return chosen;
            });
        }

        /**
         * Someone offline keeps their stored nick for when they come back -
         * unless nicks end on leaving, then the leftover row is freed.
         */
        private CompletableFuture<Chosen> notStored(Chosen chosen) {
            return service.storedByName(chosen.name()).thenCompose(stored -> {
                if (stored.isEmpty() || stored.get().playerId().equals(chosen.targetId())) {
                    return CompletableFuture.completedFuture(chosen);
                }
                if (nicks.nick(stored.get().playerId()).isPresent()) {
                    throw refuse(FailureReason.CONFLICT, "nick.name-in-use", chosen.name());
                }
                if (service.restoreOnRejoin()) {
                    throw refuse(FailureReason.CONFLICT, "nick.name-reserved", chosen.name());
                }
                return service.delete(stored.get().playerId()).thenApply(ignored -> chosen);
            });
        }

        private CompletableFuture<Optional<NickRecord.Skin>> skin(Chosen chosen, NickInput.Skin skin) {
            return switch (skin.kind()) {
                case OWN -> CompletableFuture.completedFuture(Optional.empty());
                case OF_NAME -> settings.get(NickSettings.SKIN_OF_NAME)
                        ? skins.skinOf(chosen.name())
                        : CompletableFuture.completedFuture(Optional.empty());
                case RANDOM -> {
                    List<String> pool = settings.get(NickSettings.RANDOM_SKINS);
                    yield pool.isEmpty()
                            ? CompletableFuture.completedFuture(Optional.empty())
                            : skins.skinOf(pool.get(random.nextInt(pool.size())));
                }
                case ACCOUNT -> skins.skinOf(skin.account()).thenApply(found -> {
                    if (found.isEmpty()) {
                        throw refuse(FailureReason.NOT_FOUND, "nick.skin-not-found", skin.account());
                    }
                    return found;
                });
            };
        }
    }

    /**
     * Ends a nick - on this server, or (through the network) on the server
     * the player is on, or the stored one of a player who is offline.
     * Result: the nick that ended.
     */
    public static final class ClearNick implements Action<PlayerRef, NickRecord> {

        private final NickService service;
        private final MessageService messages;
        private final TaskScheduler scheduler;

        public ClearNick(NickService service, MessageService messages, TaskScheduler scheduler) {
            this.service = service;
            this.messages = messages;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return CLEAR;
        }

        @Override
        public CompletableFuture<ActionResult<NickRecord>> execute(ActionContext context, PlayerRef target) {
            boolean self = target.id().equals(context.actor().playerId());
            return scheduler.supplyOnMainThread(() -> {
                        Player player = Bukkit.getPlayer(target.id());
                        Optional<NickRecord> worn = player == null ? Optional.empty() : service.takeOff(player);
                        if (worn.isPresent() && !self) {
                            player.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("nick.removed-by-staff"))));
                        }
                        return worn;
                    })
                    .thenCompose(worn -> service.stored(target.id()).thenCompose(stored -> {
                        Optional<NickRecord> ended = worn.or(() -> stored);
                        if (ended.isEmpty()) {
                            throw refuse(FailureReason.CONFLICT, self ? "nick.not-nicked" : "nick.not-nicked-other", target.name());
                        }
                        return service.delete(target.id()).thenApply(ignored -> self
                                ? ActionResult.success(ended.get(), MessageKey.of("nick.cleared"), ended.get().name())
                                : ActionResult.success(ended.get(), MessageKey.of("nick.cleared-other"), ended.get().name(),
                                        ended.get().realName()));
                    }))
                    .exceptionally(NickActions::failure);
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Ends an action's chain early with a failure the sender sees. */
    private static final class Refusal extends RuntimeException {

        private final transient ActionResult.Failure<NickRecord> failure;

        Refusal(ActionResult.Failure<NickRecord> failure) {
            super(null, null, false, false);
            this.failure = failure;
        }
    }

    private static Refusal refuse(FailureReason reason, String key, Object... args) {
        return new Refusal((ActionResult.Failure<NickRecord>) ActionResult.<NickRecord>failure(reason, MessageKey.of(key), args));
    }

    private static ActionResult<NickRecord> failure(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof Refusal refusal) {
            return refusal.failure;
        }
        if (cause instanceof NickNameTakenException taken) {
            return ActionResult.failure(FailureReason.CONFLICT, MessageKey.of("nick.name-in-use"), taken.name());
        }
        return ActionResult.failure(FailureReason.INTERNAL_ERROR, cause.getMessage());
    }
}
