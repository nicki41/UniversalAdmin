package dev.universaladmin.modules.server;

import dev.universaladmin.action.Actor;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class DefaultMaintenanceService implements MaintenanceService {

    private final MaintenanceStateRepository repository;
    private final SettingsService settings;
    private final TaskScheduler scheduler;
    private final Logger logger;

    private volatile MaintenanceState cached;

    public DefaultMaintenanceService(
            MaintenanceStateRepository repository, SettingsService settings, TaskScheduler scheduler, Logger logger) {
        this.repository = repository;
        this.settings = settings;
        this.scheduler = scheduler;
        this.logger = logger;
        // Seeds synchronously from config.yml's defaults so current()/isAllowed()
        // are correct even in the brief window before the async DB load below
        // completes - see the class javadoc.
        this.cached = new MaintenanceState(
                settings.get(CoreSettings.MAINTENANCE_ENABLED), null, settings.get(CoreSettings.MAINTENANCE_KICK_MESSAGE),
                Set.of(), Instant.now(), "startup-default");
        repository.load().thenAccept(loaded -> loaded.ifPresent(state -> this.cached = state))
                .exceptionally(error -> {
                    logger.log(Level.WARNING, "Failed to load persisted maintenance state; keeping the config.yml default.", error);
                    return null;
                });
    }

    @Override
    public MaintenanceState current() {
        return cached;
    }

    @Override
    public CompletableFuture<MaintenanceState> enable(String reason, String message, boolean kickNonBypass, Actor actor) {
        MaintenanceState state = new MaintenanceState(
                true, reason, message, cached.allowedPlayers(), Instant.now(), actor.displayName());
        return persist(state).thenApply(saved -> {
            if (kickNonBypass) {
                kickNonBypassPlayers();
            }
            return saved;
        });
    }

    @Override
    public CompletableFuture<MaintenanceState> disable(Actor actor) {
        MaintenanceState state = new MaintenanceState(
                false, cached.reason(), cached.message(), cached.allowedPlayers(), Instant.now(), actor.displayName());
        return persist(state);
    }

    @Override
    public CompletableFuture<MaintenanceState> setAllowedPlayers(Set<String> playerNames, Actor actor) {
        Set<String> normalized = playerNames.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        MaintenanceState state = new MaintenanceState(
                cached.enabled(), cached.reason(), cached.message(), normalized, Instant.now(), actor.displayName());
        return persist(state);
    }

    @Override
    public boolean isAllowed(Player player) {
        MaintenanceState state = cached;
        if (!state.enabled()) {
            return true;
        }
        return player.hasPermission(ServerPermissions.BYPASS_MAINTENANCE.value()) || state.isAllowedByName(player.getName());
    }

    @Override
    public String effectiveKickMessage() {
        String message = cached.message();
        return message != null && !message.isBlank() ? message : settings.get(CoreSettings.MAINTENANCE_KICK_MESSAGE);
    }

    private CompletableFuture<MaintenanceState> persist(MaintenanceState state) {
        return repository.save(state).thenApply(ignored -> {
            cached = state;
            return state;
        });
    }

    private void kickNonBypassPlayers() {
        scheduler.runOnMainThread(() -> {
            Component kickMessage = ComponentMessages.render(effectiveKickMessage());
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isAllowed(player)) {
                    player.kick(kickMessage);
                }
            }
        });
    }
}
