package dev.universaladmin.modules.moderation;

import dev.universaladmin.action.ActionEvent;
import dev.universaladmin.action.ActionEventListener;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.network.NetworkListener;
import dev.universaladmin.network.NetworkMessage;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Moderation across the network. Every punishment or pardon issued on this
 * server is announced on the {@link #TOPIC} channel; when another server
 * announces one, this server makes it take effect for the player if they are
 * here ({@link PunishmentEnforcer}: removed from the network for a ban or
 * kick, told about a mute or warning, frozen or unfrozen) and tells its own
 * staff ({@link StaffPunishmentNotifier}, tagged with the other server's
 * name).
 *
 * <p>Bans and mutes need no message to be <em>enforced</em> - every server
 * reads the same database on join and on chat. The message is about what
 * happens right now: the banned player is online somewhere else.
 */
final class NetworkModerationBridge implements ActionEventListener, NetworkListener {

    static final String TOPIC = "moderation.applied";

    private static final Set<ActionId> ISSUING = Set.of(
            ModerationActionIds.KICK, ModerationActionIds.BAN, ModerationActionIds.TEMP_BAN, ModerationActionIds.IP_BAN,
            ModerationActionIds.MUTE, ModerationActionIds.TEMP_MUTE, ModerationActionIds.WARN, ModerationActionIds.FREEZE);
    private static final Set<ActionId> LIFTING = Set.of(
            ModerationActionIds.UNBAN, ModerationActionIds.UNMUTE, ModerationActionIds.UNFREEZE, ModerationActionIds.REMOVE_WARN);

    private final NetworkService network;
    private final PunishmentService punishments;
    private final PunishmentEnforcer enforcer;
    private final StaffPunishmentNotifier notifier;
    private final TaskScheduler scheduler;

    NetworkModerationBridge(
            NetworkService network, PunishmentService punishments, PunishmentEnforcer enforcer,
            StaffPunishmentNotifier notifier, TaskScheduler scheduler) {
        this.network = network;
        this.punishments = punishments;
        this.enforcer = enforcer;
        this.notifier = notifier;
        this.scheduler = scheduler;
    }

    @Override
    public void onActionEvent(ActionEvent event) {
        if (!network.enabled() || !(event instanceof ActionEvent.Executed<?, ?> executed)) {
            return;
        }
        ActionId id = executed.id();
        if (!ISSUING.contains(id) && !LIFTING.contains(id)) {
            return;
        }
        List<Punishment> affected = new ArrayList<>();
        Object value = executed.result().value();
        if (value instanceof Punishment punishment) {
            affected.add(punishment);
        } else if (value instanceof List<?> list) {
            list.stream().filter(Punishment.class::isInstance).map(Punishment.class::cast).forEach(affected::add);
        }
        if (affected.isEmpty()) {
            return;
        }
        network.publish(TOPIC, Map.of(
                "action", id.toString(),
                "punishments", affected.stream().map(punishment -> Long.toString(punishment.id())).collect(Collectors.joining(",")),
                "actor", executed.context().actor().displayName(),
                "actor-id", executed.context().actor().playerId() == null ? "" : executed.context().actor().playerId().toString()));
    }

    @Override
    public void onMessage(NetworkMessage message) {
        String action = message.get("action", "");
        int colon = action.indexOf(':');
        if (colon <= 0) {
            return;
        }
        ActionId id = ActionId.of(action.substring(0, colon), action.substring(colon + 1));
        boolean issuing = ISSUING.contains(id);
        if (!issuing && !LIFTING.contains(id)) {
            return;
        }
        String actor = message.get("actor", "?");
        String actorIdText = message.get("actor-id", "");
        UUID actorId = actorIdText.isEmpty() ? null : UUID.fromString(actorIdText);
        List<CompletableFuture<Optional<Punishment>>> lookups = Arrays.stream(message.get("punishments", "").split(","))
                .filter(part -> !part.isBlank())
                .map(part -> punishments.findById(Long.parseLong(part.trim())))
                .toList();
        CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new)).thenRun(() -> scheduler.runOnMainThread(() -> {
            boolean announced = false;
            for (CompletableFuture<Optional<Punishment>> lookup : lookups) {
                Optional<Punishment> found = lookup.join();
                if (found.isEmpty()) {
                    continue;
                }
                Punishment punishment = found.get();
                if (issuing) {
                    enforcer.enforce(punishment);
                } else {
                    enforcer.lift(punishment);
                }
                if (!announced) {
                    notifier.announce(id, punishment, actor, actorId, message.source());
                    announced = true;
                }
            }
        }));
    }
}
