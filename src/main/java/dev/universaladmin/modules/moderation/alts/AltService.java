package dev.universaladmin.modules.moderation.alts;

import dev.universaladmin.modules.moderation.ModerationSettings;
import dev.universaladmin.modules.moderation.PunishmentService;
import dev.universaladmin.settings.SettingsService;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Alt detection - opt-in ({@code moderation.alts.enabled}). Every login
 * stores a keyed hash of the address ({@link AddressHasher}); accounts that
 * share one count as possible alts. Addresses not seen within
 * {@code moderation.alts.retention} are forgotten, also while the feature is
 * switched off. Nothing here refuses anyone on its own - see
 * {@link AltSupport} for the staff hint and the optional refusal.
 */
public final class AltService {

    /** How many possible alts a lookup returns at most. */
    static final int LIMIT = 50;

    private final AltRepository repository;
    private final PunishmentService punishments;
    private final SettingsService settings;

    private volatile AddressHasher hasher;

    public AltService(AltRepository repository, PunishmentService punishments, SettingsService settings) {
        this.repository = repository;
        this.punishments = punishments;
        this.settings = settings;
    }

    public boolean enabled() {
        return settings.get(ModerationSettings.ALTS_ENABLED);
    }

    /**
     * At login, off the main thread: records the address (if alt detection is
     * on) and answers the banned accounts that share an address with this
     * player - empty if there are none or alt detection is off.
     */
    public CompletableFuture<List<AltMatch>> checkLogin(UUID playerId, String playerName, InetAddress address) {
        if (!enabled() || address == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        Instant now = Instant.now();
        return hasher()
                .thenCompose(keyed -> repository.record(playerId, playerName, keyed.hash(address), now))
                .thenCompose(ignored -> alts(playerId))
                .thenApply(alts -> alts.stream().filter(AltMatch::banned).toList());
    }

    /** Possible alts of {@code playerId} within the retention period, with whether each is banned right now. */
    public CompletableFuture<List<AltMatch>> alts(UUID playerId) {
        return repository.findAlts(playerId, Instant.now().minus(retention()), LIMIT).thenCompose(found -> {
            List<CompletableFuture<AltMatch>> withBans = found.stream()
                    .map(alt -> punishments.activeBan(alt.playerId()).thenApply(ban -> alt.withBanned(ban.isPresent())))
                    .toList();
            return CompletableFuture.allOf(withBans.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> withBans.stream().map(CompletableFuture::join).toList());
        });
    }

    /** Deletes every stored address of {@code playerId} - for a player who asks to be forgotten. */
    public CompletableFuture<Integer> forget(UUID playerId) {
        return repository.forget(playerId);
    }

    /** Deletes addresses older than the retention period. */
    public CompletableFuture<Integer> sweep() {
        return repository.forgetBefore(Instant.now().minus(retention()));
    }

    private Duration retention() {
        return settings.get(ModerationSettings.ALTS_RETENTION);
    }

    private CompletableFuture<AddressHasher> hasher() {
        AddressHasher known = hasher;
        if (known != null) {
            return CompletableFuture.completedFuture(known);
        }
        return repository.secret().thenApply(secret -> {
            AddressHasher created = new AddressHasher(secret);
            hasher = created;
            return created;
        });
    }
}
